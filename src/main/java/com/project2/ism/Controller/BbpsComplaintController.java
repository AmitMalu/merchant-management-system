package com.project2.ism.Controller;

import com.project2.ism.Model.Bbps.BbpsComplaint;
import com.project2.ism.Service.BbpsComplaintService;
import com.project2.ism.Service.BbpsNotificationService;
import com.project2.ism.Service.CallerIdentityService;
import com.project2.ism.response.CommonResponse;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bill payment complaints for the logged-in merchant. The merchant is always taken from the login
 * (never from the request), so a merchant can only raise and track complaints about their own payments.
 */
@RestController
@RequestMapping("/billpay/complaints")
public class BbpsComplaintController {

    private final BbpsComplaintService complaintService;
    private final CallerIdentityService callerIdentity;
    private final BbpsNotificationService notifications;

    public BbpsComplaintController(BbpsComplaintService complaintService, CallerIdentityService callerIdentity,
                                   BbpsNotificationService notifications) {
        this.complaintService = complaintService;
        this.callerIdentity = callerIdentity;
        this.notifications = notifications;
    }

    public static class RegisterRequest {
        private String txnRefId;
        private String disposition;
        private String description;

        public String getTxnRefId() { return txnRefId; }
        public void setTxnRefId(String txnRefId) { this.txnRefId = txnRefId; }
        public String getDisposition() { return disposition; }
        public void setDisposition(String disposition) { this.disposition = disposition; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }

    public static class TrackRequest {
        private String complaintId;

        public String getComplaintId() { return complaintId; }
        public void setComplaintId(String complaintId) { this.complaintId = complaintId; }
    }

    /** The merchant's latest successful payments, to pick from. */
    @GetMapping("/my-transactions")
    public CommonResponse<List<Map<String, Object>>> myTransactions() {
        return CommonResponse.success(
                complaintService.myTransactions(callerIdentity.currentMerchantId()),
                "Transactions fetched successfully");
    }

    /** The merchant's own complaints, newest first. */
    @GetMapping
    public CommonResponse<List<Map<String, Object>>> myComplaints() {
        List<Map<String, Object>> rows = complaintService.myComplaints(callerIdentity.currentMerchantId())
                .stream().map(BbpsComplaintController::toView).toList();
        return CommonResponse.success(rows, "Complaints fetched successfully");
    }

    @PostMapping("/register")
    public CommonResponse<Map<String, Object>> register(@RequestBody RegisterRequest request) throws Exception {
        Long merchantId = callerIdentity.currentMerchantId();
        BbpsComplaint complaint = complaintService.register(
                merchantId, request.getTxnRefId(), request.getDisposition(), request.getDescription());
        notifications.notifyComplaintRegistered(complaint.getTxnRefId(), complaint.getComplaintId());
        return CommonResponse.success(toView(complaint), "Complaint registered successfully");
    }

    @PostMapping("/track")
    public CommonResponse<Map<String, Object>> track(@RequestBody TrackRequest request) throws Exception {
        Long merchantId = callerIdentity.currentMerchantId();
        BbpsComplaint complaint = complaintService.track(merchantId, request.getComplaintId());
        return CommonResponse.success(toView(complaint), "Complaint status fetched successfully");
    }

    /** Only what the merchant needs to see - no internal ids. */
    private static Map<String, Object> toView(BbpsComplaint c) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("complaintId", c.getComplaintId());
        view.put("txnRefId", c.getTxnRefId());
        view.put("complaintType", c.getComplaintType());
        view.put("disposition", c.getDisposition());
        view.put("description", c.getDescription());
        view.put("status", c.getStatus());
        view.put("complaintAssigned", c.getComplaintAssigned());
        view.put("remarks", c.getRemarks());
        view.put("createdAt", c.getCreatedAt());
        view.put("lastCheckedAt", c.getLastCheckedAt());
        return view;
    }
}
