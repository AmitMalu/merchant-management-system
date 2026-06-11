package com.project2.ism.Service;

import com.project2.ism.Model.VendorTransactions;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class CsvParser {

    // Re-use SAME date patterns as ExcelParser
    private static final DateTimeFormatter[] DATE_PATTERNS = ExcelParser.DATE_PATTERNS;

    public List<VendorTransactions> parse(InputStream in) {
        List<VendorTransactions> list = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new InputStreamReader(in))) {

            String headerLine = br.readLine();
            if (headerLine == null) {
                throw new IllegalArgumentException("CSV file is empty");
            }

            // split headers
            String[] headers = headerLine.split(",", -1);

            // map header -> column index
            Map<String, Integer> colIndex = new HashMap<>();
            for (int i = 0; i < headers.length; i++) {
                colIndex.put(headers[i].trim(), i);
            }

            String line;
            int rowNum = 1;

            while ((line = br.readLine()) != null) {
                rowNum++;

                if (line.trim().isEmpty()) continue;

                String[] cols = line.split(",", -1);

                try {
                    VendorTransactions t = mapRow(cols, colIndex);
                    if (!isTransactionEmpty(t)) list.add(t);

                } catch (Exception e) {
                    throw new RuntimeException("Error parsing CSV row " + rowNum + ": " + e.getMessage(), e);
                }
            }

        } catch (IOException e) {
            throw new RuntimeException("Failed to read CSV file: " + e.getMessage(), e);
        }

        return list;
    }

    private VendorTransactions mapRow(String[] cols, Map<String, Integer> colIndex) {
        VendorTransactions t = new VendorTransactions();

        t.setTransactionReferenceId(get(cols, colIndex, "ID"));
        t.setDate(parseDate(get(cols, colIndex, "Date")));
        t.setMobile(get(cols, colIndex, "Mobile"));
        t.setEmail(get(cols, colIndex, "Email"));
        t.setConsumer(get(cols, colIndex, "Consumer"));
        t.setUsername(get(cols, colIndex, "Username"));
        t.setType(get(cols, colIndex, "Type"));
        t.setMode(get(cols, colIndex, "Mode"));
        t.setAmount(parseBig(get(cols, colIndex, "Amount")));
        t.setTip(parseBig(get(cols, colIndex, "Tip")));
        t.setCashAtPos(parseBig(get(cols, colIndex, "Cash at POS")));
        t.setTxnType(get(cols, colIndex, "Txn Type"));
        t.setAuthCode(get(cols, colIndex, "Auth Code"));
        t.setCard(get(cols, colIndex, "Card"));
        t.setIssuingBank(get(cols, colIndex, "Issuing Bank"));
        t.setCardType(get(cols, colIndex, "Card Type"));
        t.setBrandType(get(cols, colIndex, "Brand Type"));
        t.setCardClassification(get(cols, colIndex, "Card Classification"));
        t.setCardTxnType(get(cols, colIndex, "Card Txn Type"));
        t.setRrn(get(cols, colIndex, "RRN"));
        t.setInvoiceNumber(get(cols, colIndex, "Invoice#"));
        t.setDeviceSerial(get(cols, colIndex, "Device Serial"));
        t.setMerchant(get(cols, colIndex, "Merchant"));
        t.setCategory(get(cols, colIndex, "Category"));
        t.setStatus(get(cols, colIndex, "Status"));
        t.setSettledOn(parseDate(get(cols, colIndex, "Settled On")));
        t.setLabels(get(cols, colIndex, "Labels"));
        t.setMid(get(cols, colIndex, "MID"));
        t.setTid(get(cols, colIndex, "TID"));
        t.setBatchNumber(get(cols, colIndex, "Batch#"));
        t.setRef(get(cols, colIndex, "Ref#"));
        t.setRef1(get(cols, colIndex, "Ref# 1"));
        t.setRef2(get(cols, colIndex, "Ref# 2"));
        t.setRef3(get(cols, colIndex, "Ref# 3"));
        t.setRef4(get(cols, colIndex, "Ref# 4"));
        t.setRef5(get(cols, colIndex, "Ref# 5"));
        t.setRef6(get(cols, colIndex, "Ref# 6"));
        t.setRef7(get(cols, colIndex, "Ref# 7"));
        t.setOriginalTransactionId(get(cols, colIndex, "Original Transaction Id"));
        t.setReceiptNo(get(cols, colIndex, "Receipt No"));
        t.setErrorCode(get(cols, colIndex, "Error Code"));
        t.setAdditionalInformation(get(cols, colIndex, "Additional Information"));
        t.setPgErrorCode(get(cols, colIndex, "PG Error Code"));
        t.setPgErrorMessage(get(cols, colIndex, "PG Error Message"));
        t.setLatitude(get(cols, colIndex, "Latitude"));
        t.setLongitude(get(cols, colIndex, "Longitude"));
        t.setPayer(get(cols, colIndex, "Payer"));
        t.setTidLocation(get(cols, colIndex, "TID Location"));
        t.setDxMode(get(cols, colIndex, "DX Mode"));
        t.setAcquiringBank(get(cols, colIndex, "Acquiring Bank"));
        t.setIssuingBankAlt(get(cols, colIndex, "Issuing Bank.1"));

        return t;
    }

    private static String get(String[] cols, Map<String, Integer> idx, String key) {
        Integer i = idx.get(key);
        if (i == null || i >= cols.length) return "";
        return sanitize(cols[i]);
    }

    private static String sanitize(String v) {
        if (v == null) return null;

        // remove wrapping quotes 'value' or "value"
        v = v.trim()
                .replaceAll("^'+", "")   // remove leading '
                .replaceAll("'+$", "")   // remove trailing '
                .replaceAll("^\"+", "")  // remove leading "
                .replaceAll("\"+$", ""); // remove trailing "

        // remove stray invisible unicode whitespace
        v = v.replaceAll("\\p{C}", "").trim();

        return v;
    }


    private static BigDecimal parseBig(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            return new BigDecimal(s.replace(",", ""));
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid numeric value: '" + s + "'");
        }
    }

    private static LocalDateTime parseDate(String s) {
        if (s == null || s.isEmpty()) return null;

        s = s.trim();
        for (DateTimeFormatter f : DATE_PATTERNS) {
            try {
                return LocalDateTime.parse(s, f);
            } catch (Exception ignored) {}
        }
        throw new IllegalArgumentException("Invalid date format: '" + s + "'");
    }

    private static boolean isTransactionEmpty(VendorTransactions t) {
        return (t.getTransactionReferenceId() == null || t.getTransactionReferenceId().isEmpty())
                && t.getDate() == null
                && (t.getMobile() == null || t.getMobile().isEmpty())
                && t.getAmount() == null;
    }
}
