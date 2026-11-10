package com.project2.ism.util;

import com.project2.ism.Model.Payment.PaymentVendorResponseLog;
import com.project2.ism.Repository.PaymentVendorResponseLogRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Saves one vendor request/response log row in its OWN transaction.
 *
 * Before, the log was saved inside the caller's transaction. When that transaction was read-only (the
 * status lookup) MySQL refused the insert, and the failed insert marked the whole request as
 * rollback-only, so a good answer from Bill Avenue came back as "409 Transaction silently rolled
 * back". The same could happen inside a bill payment (after the wallet debit) if a log insert ever
 * failed. A separate transaction means a log problem can no longer affect the request that wrote it.
 */
@Component
public class VendorResponseLogWriter {

    private final PaymentVendorResponseLogRepository repository;

    public VendorResponseLogWriter(PaymentVendorResponseLogRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void write(PaymentVendorResponseLog entry) {
        repository.save(entry);
    }
}
