package com.project2.ism.DTO;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Request shape for the BBPS Transaction Status API, per the Bharat Bill
 * Payment System (Bill Avenue) API Integration Document — JSON request
 * fields match the spec's "Transaction Status Sample JSON Request" section:
 * {"trackingType": ..., "trackingValue": ...}.
 *
 * trackingType is one of TRANS_REF_ID, MOBILE_NO, or REQUEST_ID.
 * fromDate/toDate (YYYY-MM-DD) are only required when trackingType is
 * MOBILE_NO, and are omitted from the vendor payload otherwise.
 *
 * requestId is our own tracking id (sent as a URL query param by
 * BillAvenueApiClient, not part of the vendor's JSON body) so it is excluded
 * from serialization.
 *
 * @author SHUBHAM KHOPADE
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TransactionStatusRequest {

    @JsonIgnore
    private String requestId;
    private String trackingType;
    private String trackingValue;
    private String fromDate;
    private String toDate;

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    public String getTrackingType() { return trackingType; }
    public void setTrackingType(String trackingType) { this.trackingType = trackingType; }

    public String getTrackingValue() { return trackingValue; }
    public void setTrackingValue(String trackingValue) { this.trackingValue = trackingValue; }

    public String getFromDate() { return fromDate; }
    public void setFromDate(String fromDate) { this.fromDate = fromDate; }

    public String getToDate() { return toDate; }
    public void setToDate(String toDate) { this.toDate = toDate; }
}
