package com.project2.ism.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.project2.ism.Enum.BillAvenueApiType;
import com.project2.ism.Helper.BillAvenueIVGenerator;
import com.project2.ism.Model.Bbps.BbpsComplaint;
import com.project2.ism.Model.Bbps.BbpsTransaction;
import com.project2.ism.Model.Payment.PaymentVendor;
import com.project2.ism.Repository.BbpsComplaintRepository;
import com.project2.ism.Repository.BbpsTransactionRepository;
import com.project2.ism.Repository.PaymentVendorRepository;
import com.project2.ism.util.BillAvenueApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Complaints about a merchant's own bill payments, raised with and tracked at Bill Avenue.
 * Every call is for one merchant: a complaint can be raised only against a payment that merchant
 * made, and only that merchant's complaints can be tracked.
 */
@Service
public class BbpsComplaintService {

    private static final Logger log = LoggerFactory.getLogger(BbpsComplaintService.class);

    /** The only complaint type supported (the one Bill Avenue's Transaction complaint API covers). */
    public static final String COMPLAINT_TYPE_TRANSACTION = "Transaction";

    /** The dispositions Bill Avenue accepts for a Transaction complaint. */
    public static final List<String> DISPOSITIONS = List.of(
            "Transaction Successful, Amount Debited but services not received",
            "Transaction Successful, Amount Debited but Service Disconnected or Service Stopped",
            "Transaction Successful, Amount Debited but Late Payment Surcharge Charges add in next bill",
            "Erroneously paid in wrong account",
            "Duplicate Payment",
            "Erroneously paid the wrong amount",
            "Payment information not received from Biller or Delay in receiving payment information from the Biller",
            "Bill Paid but Amount not adjusted or still showing due amount");

    static final int MAX_DESCRIPTION = 255;

    private final PaymentVendorRepository paymentVendorRepository;
    private final BillAvenueApiClient billAvenueApiClient;
    private final BbpsTransactionRepository bbpsTransactionRepository;
    private final BbpsComplaintRepository bbpsComplaintRepository;
    private final ObjectMapper objectMapper;

    public BbpsComplaintService(PaymentVendorRepository paymentVendorRepository,
                                BillAvenueApiClient billAvenueApiClient,
                                BbpsTransactionRepository bbpsTransactionRepository,
                                BbpsComplaintRepository bbpsComplaintRepository,
                                ObjectMapper objectMapper) {
        this.paymentVendorRepository = paymentVendorRepository;
        this.billAvenueApiClient = billAvenueApiClient;
        this.bbpsTransactionRepository = bbpsTransactionRepository;
        this.bbpsComplaintRepository = bbpsComplaintRepository;
        this.objectMapper = objectMapper;
    }

    /** The merchant's latest successful payments, to pick from when raising a complaint. */
    public List<Map<String, Object>> myTransactions(Long merchantId) {
        return bbpsTransactionRepository
                .findTop50ByMerchantIdAndStatusAndTxnRefIdIsNotNullOrderByCreatedAtDesc(
                        merchantId, BbpsTransaction.BbpsStatus.SUCCESS)
                .stream()
                .map(t -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("txnRefId", t.getTxnRefId());
                    row.put("billerName", t.getBillerName());
                    row.put("category", t.getCategory());
                    row.put("amount", t.getAmount());
                    row.put("customerMobile", t.getCustomerMobile());
                    row.put("createdAt", t.getCreatedAt());
                    return row;
                })
                .toList();
    }

    public List<BbpsComplaint> myComplaints(Long merchantId) {
        return bbpsComplaintRepository.findTop50ByMerchantIdOrderByCreatedAtDesc(merchantId);
    }

    /**
     * Raises a Transaction complaint with Bill Avenue for one of the merchant's own payments.
     * Throws IllegalArgumentException (shown to the user) for anything wrong with the input and
     * IllegalStateException when Bill Avenue refuses the complaint.
     */
    public BbpsComplaint register(Long merchantId, String txnRefId, String disposition, String description)
            throws Exception {

        if (txnRefId == null || txnRefId.isBlank()) {
            throw new IllegalArgumentException("Enter the transaction ID");
        }
        if (disposition == null || !DISPOSITIONS.contains(disposition)) {
            throw new IllegalArgumentException("Select a valid complaint disposition");
        }
        String cleanDescription = description == null ? "" : description.replaceAll("\\p{Cntrl}", " ").trim();
        if (cleanDescription.isEmpty()) {
            throw new IllegalArgumentException("Enter a complaint description");
        }
        if (cleanDescription.length() > MAX_DESCRIPTION) {
            throw new IllegalArgumentException("Description cannot exceed " + MAX_DESCRIPTION + " characters");
        }

        // Only against a payment this merchant made. Same answer whether the id does not exist or
        // belongs to someone else.
        BbpsTransaction txn = bbpsTransactionRepository.findByTxnRefId(txnRefId.trim())
                .filter(t -> merchantId.equals(t.getMerchantId()))
                .orElseThrow(() -> new IllegalArgumentException("No transaction found for that ID"));

        PaymentVendor vendor = paymentVendorRepository
                .findByVendorNameAndStatus("Bill Avenue", true)
                .orElseThrow(() -> new IllegalStateException("Bill Avenue is not available right now"));

        ObjectNode body = objectMapper.createObjectNode();
        body.put("complaintType", COMPLAINT_TYPE_TRANSACTION);
        body.put("complainDesc", cleanDescription);
        body.put("txnRefId", txn.getTxnRefId());
        body.put("complaintDisposition", disposition);

        log.info("Registering BBPS complaint | merchantId={} txnRefId={}", merchantId, txn.getTxnRefId());

        JsonNode response = callVendor(vendor.getId(), BillAvenueApiType.COMPLAINT_REGISTER, body);

        String code = response.path("complaintResponseCode").asText("");
        String complaintId = response.path("complaintId").asText("");
        if (!"000".equals(code) || complaintId.isBlank()) {
            String reason = vendorReason(response);
            log.warn("BBPS complaint refused | merchantId={} txnRefId={} code={} reason={}",
                    merchantId, txn.getTxnRefId(), code, reason);
            throw new IllegalStateException("Complaint could not be registered: " + reason);
        }

        BbpsComplaint complaint = new BbpsComplaint();
        complaint.setMerchantId(merchantId);
        complaint.setBbpsTransactionId(txn.getId());
        complaint.setTxnRefId(txn.getTxnRefId());
        complaint.setComplaintType(COMPLAINT_TYPE_TRANSACTION);
        complaint.setDisposition(disposition);
        complaint.setDescription(cleanDescription);
        complaint.setComplaintId(complaintId);
        complaint.setComplaintAssigned(blankToNull(response.path("complaintAssigned").asText("")));
        complaint.setStatus("REGISTERED");
        return bbpsComplaintRepository.save(complaint);
    }

    /** Latest status of one of the merchant's own complaints, asked from Bill Avenue. */
    public BbpsComplaint track(Long merchantId, String complaintId) throws Exception {

        if (complaintId == null || complaintId.isBlank()) {
            throw new IllegalArgumentException("Enter the complaint ID");
        }

        BbpsComplaint complaint = bbpsComplaintRepository
                .findByComplaintIdAndMerchantId(complaintId.trim(), merchantId)
                .orElseThrow(() -> new IllegalArgumentException("No complaint found for that ID"));

        PaymentVendor vendor = paymentVendorRepository
                .findByVendorNameAndStatus("Bill Avenue", true)
                .orElseThrow(() -> new IllegalStateException("Bill Avenue is not available right now"));

        ObjectNode body = objectMapper.createObjectNode();
        body.put("complaintId", complaint.getComplaintId());

        JsonNode response = callVendor(vendor.getId(), BillAvenueApiType.COMPLAINT_TRACK, body);

        String code = response.path("complaintResponseCode").asText("");
        if (!"000".equals(code)) {
            throw new IllegalStateException("Complaint status is not available: " + vendorReason(response));
        }

        String status = blankToNull(response.path("complaintStatus").asText(""));
        if (status != null) {
            complaint.setStatus(status);
        }
        complaint.setComplaintAssigned(blankToNull(response.path("complaintAssigned").asText("")));
        complaint.setRemarks(blankToNull(response.path("complaintRemarks").asText("")));
        complaint.setLastCheckedAt(LocalDateTime.now());
        return bbpsComplaintRepository.save(complaint);
    }

    private JsonNode callVendor(Long vendorId, BillAvenueApiType type, ObjectNode body) throws Exception {
        String decrypted = billAvenueApiClient.call(
                vendorId, type, BillAvenueIVGenerator.generate(), objectMapper.writeValueAsString(body));
        return objectMapper.readTree(decrypted);
    }

    private String vendorReason(JsonNode response) {
        String reason = response.path("complaintResponseReason").asText("");
        if (!reason.isBlank()) {
            return reason;
        }
        JsonNode errors = response.path("errorInfo").path("error");
        if (errors.isArray() && errors.size() > 0) {
            return errors.get(0).path("errorMessage").asText("Rejected by Bill Avenue");
        }
        return "Rejected by Bill Avenue";
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
