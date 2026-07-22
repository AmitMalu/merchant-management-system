package com.project2.ism.Security;

import com.project2.ism.Service.RazorpayTransactionService;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class MosambeeChecksumUtil {

    private static final Logger log =
            LoggerFactory.getLogger(MosambeeChecksumUtil.class);

    public String generateChecksum(
            String transactionId,
            String merchantId,
            String rrn,
            String salt) {

        log.info("Generating checksum for transaction: {}", transactionId);

        String data = transactionId + merchantId + rrn + salt;
        String checksum = DigestUtils.sha512Hex(data);

        log.info("Checksum generated: {} for transaction: {} and data: {}", checksum, transactionId, data);
        return checksum;
    }

}
