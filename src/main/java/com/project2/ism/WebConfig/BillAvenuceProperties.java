package com.project2.ism.WebConfig;

/**
 * @author SHUBHAM KHOPADE
 */
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BillAvenuceProperties {

    @Value("${bill.avenue.path.biller.info.api}")
    private String billerInfoPath;

    @Value("${bill.avenue.path.biller.fetch.api}")
    private String billerFetchPath;

    @Value("${bill.avenue.path.biller.payment.api}")
    private String billPaymentPath;

    @Value("${bill.avenue.path.txn.status.api}")
    private String transactionStatusPath;

    public String getBillerInfoPath() {
        return billerInfoPath;
    }

    public String getBillerFetchPath() {
        return billerFetchPath;
    }

    public String getBillPaymentPath() {
        return billPaymentPath;
    }

    public String getTransactionStatusPath() {
        return transactionStatusPath;
    }
}

