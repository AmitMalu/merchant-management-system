package com.project2.ism.Security;

import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Component;

@Component
public class MosambeeChecksumUtil {

    public String generateChecksum(
            String transactionId,
            String merchantId,
            String rrn,
            String salt) {

        String data =
                transactionId +
                        merchantId +
                        rrn +
                        salt;

        return DigestUtils.sha512Hex(data);
    }

}
