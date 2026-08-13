package com.project2.ism.DTO;

/**
 * @author SHUBHAM KHOPADE
 */
public record BillAvenueCredentials(
        String baseUrl,
        String secretKey,
        String saltKey,
        String encryptDecryptKey,
        String userId
) {
}
