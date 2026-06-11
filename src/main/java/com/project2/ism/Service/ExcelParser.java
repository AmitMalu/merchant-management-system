package com.project2.ism.Service;

import com.project2.ism.Model.VendorTransactions;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class ExcelParser {

    public static final DateTimeFormatter[] DATE_PATTERNS = new DateTimeFormatter[]{
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"),
            DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm:ss"),
            DateTimeFormatter.ofPattern("dd/MM/yy HH:mm:ss"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"),
            DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm"),
            DateTimeFormatter.ofPattern("dd/MM/yy HH:mm"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss"),
            DateTimeFormatter.ofPattern("dd-MM-uuuu HH:mm:ss"),
            DateTimeFormatter.ofPattern("dd-MM-yy HH:mm:ss"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm"),
            DateTimeFormatter.ofPattern("dd-MM-uuuu HH:mm"),
            DateTimeFormatter.ofPattern("dd-MM-yy HH:mm"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("MM/dd/yy HH:mm"),
            DateTimeFormatter.ofPattern("M/dd/yy HH:mm"),
            DateTimeFormatter.ofPattern("M/d/yy HH:mm"),
            DateTimeFormatter.ofPattern("M/d/yy H:mm"),
            DateTimeFormatter.ofPattern("M/dd/yy H:mm"),


            // --------------------
            // New Date-only patterns
            // --------------------
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/uuuu"),
            DateTimeFormatter.ofPattern("dd/MM/yy"),

            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-uuuu"),
            DateTimeFormatter.ofPattern("dd-MM-yy"),

            DateTimeFormatter.ofPattern("yyyy-MM-dd"),

            DateTimeFormatter.ofPattern("MM/dd/yy"),
            DateTimeFormatter.ofPattern("M/d/yy"),
            DateTimeFormatter.ofPattern("M/dd/yy")
    };

    public List<VendorTransactions> parse(InputStream in) {
        List<VendorTransactions> list = new ArrayList<>();

        try (Workbook wb = WorkbookFactory.create(in)) {
            Sheet sheet = wb.getSheetAt(0);
            if (sheet == null) {
                throw new IllegalArgumentException("Excel file does not contain any sheets.");
            }

            DataFormatter fmt = new DataFormatter();
            Iterator<Row> it = sheet.rowIterator();
            if (!it.hasNext()) {
                throw new IllegalArgumentException("Excel file is empty or missing headers.");
            }

            // Header row
            Row header = it.next();
            Map<String, Integer> colIndex = new HashMap<>();
            for (int c = 0; c < header.getLastCellNum(); c++) {
                String h = fmt.formatCellValue(header.getCell(c)).trim();
                if (!h.isEmpty()) colIndex.put(h, c);
            }

            if (colIndex.isEmpty()) {
                throw new IllegalArgumentException("Header row is missing or invalid.");
            }

            while (it.hasNext()) {
                Row r = it.next();
                if (isRowEmpty(r, fmt)) continue;

                try {
                    VendorTransactions t = mapRowToTransaction(r, colIndex, fmt);
                    if (!isTransactionEmpty(t)) list.add(t);
                } catch (Exception e) {
                    // Throw a descriptive message for your global handler
                    throw new RuntimeException("Error parsing row " + r.getRowNum() + ": " + e.getMessage(), e);
                }
            }

        } catch (IOException e) {
            throw new RuntimeException("Failed to read Excel file: " + e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            // Re-throw user-friendly validation errors directly
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Unexpected error while parsing Excel: " + e.getMessage(), e);
        }

        return list;
    }

    private VendorTransactions mapRowToTransaction(Row r, Map<String, Integer> colIndex, DataFormatter fmt) {
        VendorTransactions t = new VendorTransactions();

        t.setTransactionReferenceId(get(r, colIndex, "ID", fmt));
        t.setDate(parseDate(get(r, colIndex, "Date", fmt)));
        t.setMobile(get(r, colIndex, "Mobile", fmt));
        t.setEmail(get(r, colIndex, "Email", fmt));
        t.setConsumer(get(r, colIndex, "Consumer", fmt));
        t.setUsername(get(r, colIndex, "Username", fmt));
        t.setType(get(r, colIndex, "Type", fmt));
        t.setMode(get(r, colIndex, "Mode", fmt));
        t.setAmount(parseBig(get(r, colIndex, "Amount", fmt)));
        t.setTip(parseBig(get(r, colIndex, "Tip", fmt)));
        t.setCashAtPos(parseBig(get(r, colIndex, "Cash at POS", fmt)));
        t.setTxnType(get(r, colIndex, "Txn Type", fmt));
        t.setAuthCode(get(r, colIndex, "Auth Code", fmt));
        t.setCard(get(r, colIndex, "Card", fmt));
        t.setIssuingBank(get(r, colIndex, "Issuing Bank", fmt));
        t.setCardType(get(r, colIndex, "Card Type", fmt));
        t.setBrandType(get(r, colIndex, "Brand Type", fmt));
        t.setCardClassification(get(r, colIndex, "Card Classification", fmt));
        t.setCardTxnType(get(r, colIndex, "Card Txn Type", fmt));
        t.setRrn(get(r, colIndex, "RRN", fmt));
        t.setInvoiceNumber(get(r, colIndex, "Invoice#", fmt));
        t.setDeviceSerial(get(r, colIndex, "Device Serial", fmt));
        t.setMerchant(get(r, colIndex, "Merchant", fmt));
        t.setCategory(get(r, colIndex, "Category", fmt));
        t.setStatus(get(r, colIndex, "Status", fmt));
        t.setSettledOn(parseDate(get(r, colIndex, "Settled On", fmt)));
        t.setLabels(get(r, colIndex, "Labels", fmt));
        t.setMid(getMidOrTid(r, colIndex, "MID"));
        t.setTid(getMidOrTid(r, colIndex, "TID"));
        t.setBatchNumber(get(r, colIndex, "Batch#", fmt));
        t.setRef(get(r, colIndex, "Ref#", fmt));
        t.setRef1(get(r, colIndex, "Ref# 1", fmt));
        t.setRef2(get(r, colIndex, "Ref# 2", fmt));
        t.setRef3(get(r, colIndex, "Ref# 3", fmt));
        t.setRef4(get(r, colIndex, "Ref# 4", fmt));
        t.setRef5(get(r, colIndex, "Ref# 5", fmt));
        t.setRef6(get(r, colIndex, "Ref# 6", fmt));
        t.setRef7(get(r, colIndex, "Ref# 7", fmt));
        t.setOriginalTransactionId(get(r, colIndex, "Original Transaction Id", fmt));
        t.setReceiptNo(get(r, colIndex, "Receipt No", fmt));
        t.setErrorCode(get(r, colIndex, "Error Code", fmt));
        t.setAdditionalInformation(get(r, colIndex, "Additional Information", fmt));
        t.setPgErrorCode(get(r, colIndex, "PG Error Code", fmt));
        t.setPgErrorMessage(get(r, colIndex, "PG Error Message", fmt));
        t.setLatitude(get(r, colIndex, "Latitude", fmt));
        t.setLongitude(get(r, colIndex, "Longitude", fmt));
        t.setPayer(get(r, colIndex, "Payer", fmt));
        t.setTidLocation(get(r, colIndex, "TID Location", fmt));
        t.setDxMode(get(r, colIndex, "DX Mode", fmt));
        t.setAcquiringBank(get(r, colIndex, "Acquiring Bank", fmt));
        t.setIssuingBankAlt(get(r, colIndex, "Issuing Bank.1", fmt));

        return t;
    }

    private static String get(Row r, Map<String, Integer> idx, String key, DataFormatter fmt) {
        Integer c = idx.get(key);
        if (c == null) return "";
        return fmt.formatCellValue(r.getCell(c)).trim();
    }

    private static BigDecimal parseBig(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            return new BigDecimal(s.replaceAll(",", ""));
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

    private static boolean isRowEmpty(Row row, DataFormatter fmt) {
        if (row == null) return true;
        for (Cell cell : row) {
            String value = fmt.formatCellValue(cell).trim();
            if (!value.isEmpty()) return false;
        }
        return true;
    }

    private static boolean isTransactionEmpty(VendorTransactions t) {
        return (t.getTransactionReferenceId() == null || t.getTransactionReferenceId().isEmpty())
                && t.getDate() == null
                && (t.getMobile() == null || t.getMobile().isEmpty())
                && (t.getEmail() == null || t.getEmail().isEmpty())
                && t.getAmount() == null;
    }

    private static String getMidOrTid(Row r, Map<String, Integer> idx, String key) {
        Integer c = idx.get(key);
        if (c == null) return "";
        Cell cell = r.getCell(c);
        if (cell == null) return "";

        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC ->
                // Convert large numbers to plain string to avoid scientific notation
                    new BigDecimal(cell.getNumericCellValue()).toPlainString();
            case FORMULA ->
                // Evaluate formula if needed
                    new BigDecimal(cell.getNumericCellValue()).toPlainString();
            default -> "";
        };
    }

}
