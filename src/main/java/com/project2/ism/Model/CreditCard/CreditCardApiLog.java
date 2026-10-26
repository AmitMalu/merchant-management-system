package com.project2.ism.Model.CreditCard;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One row for every call we make to SuivrePay for the credit card feature:
 * who made it (merchant), what it was (fetch billers / fetch bill / pay bill /
 * complaint), what we sent, and what came back. Shown to admins in
 * Admin > Logs > "Credit Card Logs".
 *
 * Card digits, mobile numbers and PAN are masked in the stored request and
 * response; auth header values are never stored.
 */
@Entity
@Table(name = "credit_card_api_logs",
        indexes = {
                @Index(name = "idx_ccal_created", columnList = "created_at"),
                @Index(name = "idx_ccal_merchant", columnList = "merchant_id, created_at"),
                @Index(name = "idx_ccal_request", columnList = "request_id")
        })
public class CreditCardApiLog {

    public static final String FETCH_BILLERS = "FETCH_BILLERS";
    public static final String FETCH_BILL = "FETCH_BILL";
    public static final String PAY_BILL = "PAY_BILL";
    public static final String RAISE_COMPLAINT = "RAISE_COMPLAINT";
    public static final String COMPLAINT_STATUS = "COMPLAINT_STATUS";
    public static final String UNKNOWN_ACTION = "UNKNOWN";

    /** SuivrePay answered with success. */
    public static final String OUTCOME_SUCCESS = "SUCCESS";
    /** SuivrePay answered, but refused / reported an error. */
    public static final String OUTCOME_REFUSED = "REFUSED";
    /** No usable answer (timeout, connection problem). For a payment the result is unknown. */
    public static final String OUTCOME_NO_RESPONSE = "NO_RESPONSE";
    /** We could not even send the call (vendor / credentials not configured). */
    public static final String OUTCOME_CONFIG_ERROR = "CONFIG_ERROR";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "call_id", length = 20)
    private String callId;

    @Column(name = "merchant_id")
    private Long merchantId;

    @Column(name = "merchant_name")
    private String merchantName;

    @Column(name = "action", nullable = false, length = 30)
    private String action;

    @Column(name = "biller_id", length = 50)
    private String billerId;

    @Column(name = "card_last4", length = 4)
    private String cardLast4;

    @Column(name = "request_id", length = 100)
    private String requestId;

    @Column(name = "amount", precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "endpoint_url", length = 500)
    private String endpointUrl;

    @Column(name = "request_payload", columnDefinition = "TEXT")
    private String requestPayload;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "vendor_status", length = 30)
    private String vendorStatus;

    @Column(name = "vendor_response_code", length = 20)
    private String vendorResponseCode;

    @Column(name = "vendor_error_code", length = 30)
    private String vendorErrorCode;

    @Column(name = "vendor_message", length = 500)
    private String vendorMessage;

    @Column(name = "response_payload", columnDefinition = "LONGTEXT")
    private String responsePayload;

    @Column(name = "outcome", nullable = false, length = 20)
    private String outcome;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "time_taken_ms")
    private Long timeTakenMs;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public String getCallId() { return callId; }
    public void setCallId(String callId) { this.callId = callId; }

    public Long getMerchantId() { return merchantId; }
    public void setMerchantId(Long merchantId) { this.merchantId = merchantId; }

    public String getMerchantName() { return merchantName; }
    public void setMerchantName(String merchantName) { this.merchantName = merchantName; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getBillerId() { return billerId; }
    public void setBillerId(String billerId) { this.billerId = billerId; }

    public String getCardLast4() { return cardLast4; }
    public void setCardLast4(String cardLast4) { this.cardLast4 = cardLast4; }

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getEndpointUrl() { return endpointUrl; }
    public void setEndpointUrl(String endpointUrl) { this.endpointUrl = endpointUrl; }

    public String getRequestPayload() { return requestPayload; }
    public void setRequestPayload(String requestPayload) { this.requestPayload = requestPayload; }

    public Integer getHttpStatus() { return httpStatus; }
    public void setHttpStatus(Integer httpStatus) { this.httpStatus = httpStatus; }

    public String getVendorStatus() { return vendorStatus; }
    public void setVendorStatus(String vendorStatus) { this.vendorStatus = vendorStatus; }

    public String getVendorResponseCode() { return vendorResponseCode; }
    public void setVendorResponseCode(String vendorResponseCode) { this.vendorResponseCode = vendorResponseCode; }

    public String getVendorErrorCode() { return vendorErrorCode; }
    public void setVendorErrorCode(String vendorErrorCode) { this.vendorErrorCode = vendorErrorCode; }

    public String getVendorMessage() { return vendorMessage; }
    public void setVendorMessage(String vendorMessage) { this.vendorMessage = vendorMessage; }

    public String getResponsePayload() { return responsePayload; }
    public void setResponsePayload(String responsePayload) { this.responsePayload = responsePayload; }

    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public Long getTimeTakenMs() { return timeTakenMs; }
    public void setTimeTakenMs(Long timeTakenMs) { this.timeTakenMs = timeTakenMs; }
}
