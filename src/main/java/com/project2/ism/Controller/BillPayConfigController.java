package com.project2.ism.Controller;

import com.project2.ism.DTO.BillPayConfigDTO;
import com.project2.ism.DTO.BillPayProviderRequestDTO;
import com.project2.ism.DTO.BillPaymentRequest;
import com.project2.ism.DTO.BillerFetchRequestDTO;
import com.project2.ism.DTO.FetchBillerInfoRequest;
import com.project2.ism.DTO.TransactionStatusRequest;
import com.project2.ism.Service.BbpsNotificationService;
import com.project2.ism.Service.BbpsPaymentService;
import com.project2.ism.Service.BillPayConfigService;
import com.project2.ism.Service.CallerIdentityService;
import com.project2.ism.response.CommonResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

/**
 * @author SHUBHAM KHOPADE
 */
@RestController
@RequestMapping("/billpay/config")
public class BillPayConfigController {

    private static final Logger log =
            LoggerFactory.getLogger(BillPayConfigController.class);

    private final BillPayConfigService billPayConfigService;
    private final BbpsPaymentService bbpsPaymentService;
    private final CallerIdentityService callerIdentity;
    private final BbpsNotificationService notifications;
    private final com.project2.ism.Service.BillerSyncService billerSyncService;

    public BillPayConfigController(
            BillPayConfigService billPayConfigService,
            BbpsPaymentService bbpsPaymentService,
            CallerIdentityService callerIdentity,
            BbpsNotificationService notifications,
            com.project2.ism.Service.BillerSyncService billerSyncService) {
        this.billerSyncService = billerSyncService;
        this.billPayConfigService = billPayConfigService;
        this.bbpsPaymentService = bbpsPaymentService;
        this.callerIdentity = callerIdentity;
        this.notifications = notifications;
    }

    @PostMapping("/services")
    public Map<String, Object> getServices(
            @RequestBody BillPayConfigDTO req) {

        return billPayConfigService.getBillAvenueServices(req);
    }

    @PostMapping("/providers")
    public Map<String, Object> getProviders(
            @RequestBody BillPayProviderRequestDTO req) {

        return billPayConfigService.getBillAvenueProviders(req);
    }

    @PostMapping("/biller-info")
    public CommonResponse<Object> fetchBiller(
            @RequestBody FetchBillerInfoRequest request)
            throws Exception {

        return billPayConfigService.fetchBillerInfo(request);
    }

    @PostMapping("/bill-fetch")
    public CommonResponse<Object> billFetch(
            @RequestBody BillerFetchRequestDTO request) throws Exception {

        log.info(
                "Bill fetch request received. requestId={}",
                request != null ? request.getRequestId() : null
        );

        Object response = billPayConfigService.fetchBill(request);

        log.info(
                "Bill fetch request completed successfully. requestId={}",
                request != null ? request.getRequestId() : null
        );

        return CommonResponse.success(
                response,
                "Bill fetched successfully"
        );
    }

    @PostMapping("/bill-payment")
    public CommonResponse<Object> billPayment(
            @RequestBody BillPaymentRequest request) throws Exception {

        log.info(
                "Bill payment request received. merchantId={}, billerId={}, requestId={}",
                request != null ? request.getMerchantId() : null,
                request != null ? request.getBillerId() : null,
                request != null ? request.getRequestId() : null
        );

        // The wallet to debit is the merchantId in the body — it must be the
        // logged-in merchant's own, not whoever the caller names.
        callerIdentity.requireOwner("MERCHANT", request != null ? request.getMerchantId() : null);

        CommonResponse<Object> result = bbpsPaymentService.doBillPayment(request);

        // The payment is saved and committed by now. Tell the customer (in the background).
        if (result != null && result.getStatusCode() == 200
                && result.getData() instanceof com.fasterxml.jackson.databind.JsonNode vendorResponse) {
            notifications.notifyPaymentSuccess(vendorResponse.path("txnRefId").asText(null));
        }
        return result;
    }

    public static class SyncBillersRequest {
        private java.util.List<String> billerIds;
        private Boolean dryRun;
        private Boolean removeStale;

        public java.util.List<String> getBillerIds() { return billerIds; }
        public void setBillerIds(java.util.List<String> billerIds) { this.billerIds = billerIds; }
        public Boolean getDryRun() { return dryRun; }
        public void setDryRun(Boolean dryRun) { this.dryRun = dryRun; }
        public Boolean getRemoveStale() { return removeStale; }
        public void setRemoveStale(Boolean removeStale) { this.removeStale = removeStale; }
    }

    /**
     * Admin only (SecurityConfig). Reloads the biller list from Bill Avenue for the active
     * environment. The default is a preview that changes nothing (dryRun=true).
     */
    @PostMapping("/sync-billers")
    public CommonResponse<Map<String, Object>> syncBillers(
            @RequestBody(required = false) SyncBillersRequest request) throws Exception {

        boolean dryRun = request == null || request.getDryRun() == null || request.getDryRun();
        boolean removeStale = request != null && Boolean.TRUE.equals(request.getRemoveStale());

        Map<String, Object> result = billerSyncService.sync(
                request != null ? request.getBillerIds() : null, dryRun, removeStale);
        return CommonResponse.success(result, dryRun ? "Preview only - nothing was changed" : "Billers saved");
    }

    public static class QuoteRequest {
        private java.math.BigDecimal amount;
        private String custConvFee;
        private String billerId;

        public String getBillerId() { return billerId; }
        public void setBillerId(String billerId) { this.billerId = billerId; }
        public java.math.BigDecimal getAmount() { return amount; }
        public void setAmount(java.math.BigDecimal amount) { this.amount = amount; }
        public String getCustConvFee() { return custConvFee; }
        public void setCustConvFee(String custConvFee) { this.custConvFee = custConvFee; }
    }

    /** How much a payment of this bill amount would take from the logged-in merchant's wallet. */
    @PostMapping("/payment-quote")
    public CommonResponse<Map<String, Object>> paymentQuote(@RequestBody QuoteRequest request) {
        Map<String, Object> quote = bbpsPaymentService.quote(
                callerIdentity.currentMerchantId(),
                request != null ? request.getAmount() : null,
                request != null ? request.getCustConvFee() : null,
                request != null ? request.getBillerId() : null);
        return CommonResponse.success(quote, "Payment quote");
    }

    @PostMapping("/transaction-status")
    public CommonResponse<Object> transactionStatus(
            @RequestBody TransactionStatusRequest request) throws Exception {

        // Only the logged-in merchant's own transactions, searched by transaction ID.
        Object response = bbpsPaymentService.fetchTransactionStatus(request, callerIdentity.currentMerchantId());

        return CommonResponse.success(
                response,
                "Transaction status fetched successfully"
        );
    }
}

