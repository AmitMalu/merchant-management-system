package com.project2.ism.Service;

import com.project2.ism.DTO.FranchiseInwardDTO;
import com.project2.ism.DTO.MerchantInwardDTO;
import com.project2.ism.DTO.OutwardTransactionDTO;
import com.project2.ism.DTO.ProductSerialDTO;
import com.project2.ism.Exception.BusinessException;
import com.project2.ism.Exception.DuplicateResourceException;
import com.project2.ism.Exception.ResourceNotFoundException;
import com.project2.ism.Model.InventoryTransactions.OutwardTransactions;
import com.project2.ism.Model.InventoryTransactions.ProductSerialNumbers;
import com.project2.ism.Model.Product;
import com.project2.ism.Model.Users.Franchise;
import com.project2.ism.Model.Users.Merchant;
import com.project2.ism.Repository.*;
import org.springframework.transaction.annotation.Transactional;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class OutwardTransactionService {

    private static final Logger log = LoggerFactory.getLogger(OutwardTransactionService.class);
    private final OutwardTransactionRepository outwardTransactionRepository;
        private final FranchiseRepository franchiseRepo;
        private final MerchantRepository merchantRepo;
        private final ProductRepository productRepo;
        private final ProductSerialsRepository serialRepo;

        public OutwardTransactionService(
                OutwardTransactionRepository outwardTransactionRepository,
                FranchiseRepository franchiseRepo,
                MerchantRepository merchantRepo,
                ProductRepository productRepo,
                ProductSerialsRepository serialRepo) {
            this.outwardTransactionRepository = outwardTransactionRepository;
            this.franchiseRepo = franchiseRepo;
            this.merchantRepo = merchantRepo;
            this.productRepo = productRepo;
            this.serialRepo = serialRepo;
        }

        public List<OutwardTransactions> getAll() {
            return outwardTransactionRepository.findAll();
        }

    public Page<OutwardTransactions> getFilteredPaginated(
            Pageable pageable,
            LocalDateTime startDate,
            LocalDateTime endDate
    ) {
        // If both are null: fetch everything paginated
        if (startDate == null && endDate == null) {
            return outwardTransactionRepository.findAll(pageable);
        }

        // Only startDate provided
        if (startDate != null && endDate == null) {
            return outwardTransactionRepository.findByDispatchDateAfter(startDate, pageable);
        }

        // Only endDate provided
        if (startDate == null && endDate != null) {
            return outwardTransactionRepository.findByDispatchDateBefore(endDate, pageable);
        }

        // Both provided
        return outwardTransactionRepository.findByDispatchDateBetween(startDate, endDate, pageable);
    }



    public OutwardTransactions getById(Long id) {
            return outwardTransactionRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Outward Transaction not found with id " + id));
        }

        @Transactional
        public OutwardTransactions
        createFromDTO(OutwardTransactionDTO dto) {
//            if (outwardTransactionRepository.existsByDeliveryNumber(dto.deliveryNumber)) {
//                throw new DuplicateResourceException("Delivery number already exists: " + dto.deliveryNumber);
//            }



            Franchise franchise = dto.franchiseId != null
                    ? franchiseRepo.findById(dto.franchiseId)
                    .orElseThrow(() -> new ResourceNotFoundException("Franchise not found"))
                    : null;

            Merchant merchant = dto.merchantId != null
                    ? merchantRepo.findById(dto.merchantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Merchant not found"))
                    : null;

            Product product = dto.productId != null
                    ? productRepo.findById(dto.productId)
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found"))
                    : null;

            dto.deliveryNumber = generateDeliveryNumber();
            OutwardTransactions outward = dto.toEntity(franchise, merchant, product,serialRepo);
//            outward.setDeliveryNumber(generateDeliveryNumber());
            return outwardTransactionRepository.save(outward);
        }


        // 👇 Helper method should be private and inside this service
        public String generateDeliveryNumber() {
            String prefix = "D";
            String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            long countToday = outwardTransactionRepository.countByDispatchDate(LocalDate.now()) + 1;
            String sequence = String.format("%03d", countToday);
            return prefix + "-" + datePart + "-" + sequence;
        }


        @Transactional
        public OutwardTransactions updateFromDTO(Long id, OutwardTransactionDTO dto) {
            OutwardTransactions existing = getById(id);

            Franchise franchise = dto.franchiseId != null
                    ? franchiseRepo.findById(dto.franchiseId)
                    .orElseThrow(() -> new ResourceNotFoundException("Franchise not found"))
                    : null;

            Merchant merchant = dto.merchantId != null
                    ? merchantRepo.findById(dto.merchantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Merchant not found"))
                    : null;

            Product product = dto.productId != null
                    ? productRepo.findById(dto.productId)
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found"))
                    : null;

            // map DTO → existing entity
            existing.setDeliveryNumber(dto.deliveryNumber);
            existing.setFranchise(franchise);
            existing.setMerchant(merchant);
            existing.setProduct(product);
            existing.setDispatchDate(dto.dispatchDate);
            existing.setDispatchedBy(dto.dispatchedBy);
            existing.setQuantity(dto.quantity);
            existing.setDeliveryAddress(dto.deliveryAddress);
            existing.setContactPerson(dto.contactPerson);
            existing.setContactPersonNumber(dto.contactPersonNumber);
            existing.setDeliveryMethod(dto.deliveryMethod);
            existing.setTrackingNumber(dto.trackingNumber);
            existing.setExpectedDeliveryDate(dto.expectedDelivery);
            existing.setRemarks(dto.remarks);

            if (dto.serialNumbers != null) {
                existing.setProductSerialNumbers(
                        dto.serialNumbers.stream()
                                .map(sn -> sn.toOutwardEntity(existing, serialRepo))
                                .collect(Collectors.toList())
                );
            }

            return outwardTransactionRepository.save(existing);
        }

        @Transactional
        public void delete(Long id) {
            OutwardTransactions existing = getById(id);
            // Break relationship manually
            for (ProductSerialNumbers psn : existing.getProductSerialNumbers()) {
                psn.setOutwardTransaction(null);
            }
            outwardTransactionRepository.delete(existing);
        }


    @Transactional
    public void receivedDateService(Long outwardId) {
        OutwardTransactions outward = outwardTransactionRepository.findById(outwardId)
                .orElseThrow(() -> new IllegalArgumentException("Outward transaction not found: " + outwardId));

        LocalDateTime now = LocalDateTime.now();

        // Set received date for outward
        outward.setReceivedDate(now);
        outwardTransactionRepository.save(outward);

        // Update all associated ProductSerialNumbers
        if (outward.getProductSerialNumbers() != null && !outward.getProductSerialNumbers().isEmpty()) {
            for (ProductSerialNumbers psn : outward.getProductSerialNumbers()) {
                psn.setReceivedDateByFranchise(now); // make sure this field exists in PSN entity
            }
            serialRepo.saveAll(outward.getProductSerialNumbers());
        }
    }



    public List<FranchiseInwardDTO> getFranchiseInward(Long franchiseId) {
        List<OutwardTransactions> list = outwardTransactionRepository.findByFranchiseIdWithSerials(franchiseId);

        return list.stream().map(outward -> {
            FranchiseInwardDTO dto = new FranchiseInwardDTO();
            dto.setOutwardId(outward.getId());
            dto.setProductName(outward.getProduct().getProductName()); // assuming relation exists
            dto.setQuantity(Long.valueOf(outward.getQuantity()));
            dto.setDeliveryMethod(outward.getDeliveryMethod());
            dto.setTrackingNumber(outward.getTrackingNumber());
            dto.setDispatchDate(outward.getDispatchDate());
            dto.setExpectedDeliveryDate(outward.getExpectedDeliveryDate());
            dto.setReceivedDate(outward.getReceivedDate());
            List<ProductSerialDTO> psnList = outward.getProductSerialNumbers()
                    .stream()
                    .map(psn -> {
                        ProductSerialDTO p = new ProductSerialDTO();
                        p.setId(psn.getId());
                        p.setSid(psn.getSid());
                        p.setMid(psn.getMid());
                        p.setTid(psn.getTid());
                        p.setVpaid(psn.getVpaid());
                        p.setMobNumber(psn.getMobNumber());
                        return p;
                    })
                    .toList();

            dto.setProductSerialNumbers(psnList);

            return dto;
        }).toList();
    }

    public List<MerchantInwardDTO> getMerchantInward(Long merchantId) {
        List<OutwardTransactions> list = outwardTransactionRepository.findByMerchantId(merchantId);

        return list.stream().map(outward -> {
            MerchantInwardDTO dto = new MerchantInwardDTO();
            dto.setOutwardId(outward.getId());
            dto.setProductName(outward.getProduct().getProductName()); // assuming relation exists
            dto.setQuantity(Long.valueOf(outward.getQuantity()));
            dto.setDeliveryMethod(outward.getDeliveryMethod());
            dto.setTrackingNumber(outward.getTrackingNumber());
            dto.setDispatchDate(outward.getDispatchDate());
            dto.setExpectedDeliveryDate(outward.getExpectedDeliveryDate());
            dto.setReceivedDate(outward.getReceivedDate());
            List<ProductSerialDTO> psnList = outward.getProductSerialNumbers()
                    .stream()
                    .map(psn -> {
                        ProductSerialDTO p = new ProductSerialDTO();
                        p.setId(psn.getId());
                        p.setSid(psn.getSid());
                        p.setMid(psn.getMid());
                        p.setTid(psn.getTid());
                        p.setVpaid(psn.getVpaid());
                        p.setMobNumber(psn.getMobNumber());
                        return p;
                    })
                    .toList();

            dto.setProductSerialNumbers(psnList);
            return dto;
        }).toList();
    }



    //only for updating the database one time
    @Transactional
    public int backfillReceivedDateByFranchise(Long franchiseId) {
        // Get all outward transactions for this franchise that have a receivedDate
        List<OutwardTransactions> outwards = outwardTransactionRepository.findByFranchiseIdAndReceivedDateIsNotNull(franchiseId);

        int updatedCount = 0;

        for (OutwardTransactions outward : outwards) {
            LocalDateTime receivedDate = outward.getReceivedDate();
            if (receivedDate == null || outward.getProductSerialNumbers() == null) continue;

            for (ProductSerialNumbers psn : outward.getProductSerialNumbers()) {
                // Only update if null to avoid overwriting
                if (psn.getReceivedDateByFranchise() == null) {
                    psn.setReceivedDateByFranchise(receivedDate);
                }
                if (psn.getFranchise() == null) {
                    psn.setFranchise(outward.getFranchise());
                }
            }

            serialRepo.saveAll(outward.getProductSerialNumbers());
            updatedCount += outward.getProductSerialNumbers().size();
        }

        return updatedCount;
    }

    /**
     * Export all outward transactions to Excel with serial number details
     */
    @Transactional(readOnly = true)
    public ByteArrayInputStream exportAllOutwardTransactionsToExcel(
            LocalDate startDate,
            LocalDate endDate) {

        log.info("Exporting outward transactions from {} to {}", startDate, endDate);

        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Outward Transactions");

            // Create header
            Row headerRow = sheet.createRow(0);
            createExcelHeader(headerRow);

            // Stream and process data
            AtomicInteger rowNum = new AtomicInteger(1);

            Stream<OutwardTransactions> transactionStream = startDate != null && endDate != null
                    ? outwardTransactionRepository.streamByDispatchDateBetween(
                    startDate.atStartOfDay(),
                    endDate.atTime(23, 59, 59))
                    : outwardTransactionRepository.streamAllBy();

            transactionStream.forEach(transaction -> {
                try {
                    // If transaction has serial numbers, create a row for each
                    if (transaction.getProductSerialNumbers() != null && !transaction.getProductSerialNumbers().isEmpty()) {
                        transaction.getProductSerialNumbers().forEach(serial -> {
                            Row row = sheet.createRow(rowNum.getAndIncrement());
                            populateExcelRow(row, transaction, serial);
                        });
                    } else {
                        // Create single row without serial details
                        Row row = sheet.createRow(rowNum.getAndIncrement());
                        populateExcelRow(row, transaction, null);
                    }

                    // Log progress every 500 rows
                    if (rowNum.get() % 500 == 0) {
                        log.info("Processed {} rows", rowNum.get());
                    }
                } catch (Exception e) {
                    log.error("Error processing transaction {}: {}",
                            transaction.getId(), e.getMessage());
                }
            });

            // Auto-size columns
            autoSizeColumns(sheet);

            workbook.write(out);
            log.info("Successfully exported {} rows", rowNum.get() - 1);

            return new ByteArrayInputStream(out.toByteArray());

        } catch (Exception e) {
            log.error("Error exporting outward transactions", e);
            throw new BusinessException("Failed to export to Excel: " + e.getMessage());
        }
    }

    private void createExcelHeader(Row headerRow) {
        String[] headers = {
                "Transaction ID", "Delivery Number", "Customer Type", "Customer Name",
                "Product Code", "Product Name", "Quantity", "Dispatch Date", "Dispatched By",
                "Contact Person", "Contact Number", "Delivery Method", "Tracking Number",
                "Expected Delivery", "SID", "MID", "TID", "VPAID",
                "Delivery Address", "Remarks"
        };

        CellStyle headerStyle = headerRow.getSheet().getWorkbook().createCellStyle();
        Font headerFont = headerRow.getSheet().getWorkbook().createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);

        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
    }

    private void populateExcelRow(Row row, OutwardTransactions transaction, ProductSerialNumbers serial) {
        int colNum = 0;

        // Transaction ID
        setCellValue(row, colNum++, transaction.getId());

        // Delivery Number
        setCellValue(row, colNum++, transaction.getDeliveryNumber());

        // Customer Type
        String customerType = transaction.getFranchise() != null ? "Franchise" :
                transaction.getMerchant() != null ? "Merchant" : "Unknown";
        setCellValue(row, colNum++, customerType);

        // Customer Name
        String customerName = transaction.getFranchise() != null
                ? transaction.getFranchise().getFranchiseName()
                : transaction.getMerchant() != null
                ? transaction.getMerchant().getBusinessName()
                : "-";
        setCellValue(row, colNum++, customerName);

        // Product Code
        setCellValue(row, colNum++, transaction.getProduct() != null
                ? transaction.getProduct().getProductCode() : "-");

        // Product Name
        setCellValue(row, colNum++, transaction.getProduct() != null
                ? transaction.getProduct().getProductName() : "-");

        // Quantity
        setCellValue(row, colNum++, transaction.getQuantity());

        // Dispatch Date
        setCellValue(row, colNum++, transaction.getDispatchDate() != null
                ? transaction.getDispatchDate().toString() : "-");

        // Dispatched By
        setCellValue(row, colNum++, transaction.getDispatchedBy());

        // Contact Person
        setCellValue(row, colNum++, transaction.getContactPerson());

        // Contact Number
        setCellValue(row, colNum++, transaction.getContactPersonNumber());

        // Delivery Method
        setCellValue(row, colNum++, transaction.getDeliveryMethod());

        // Tracking Number
        setCellValue(row, colNum++, transaction.getTrackingNumber());

        // Expected Delivery
        setCellValue(row, colNum++, transaction.getExpectedDeliveryDate() != null
                ? transaction.getExpectedDeliveryDate().toString() : "-");

        // Serial Number Details (if available)
        if (serial != null) {
            setCellValue(row, colNum++, serial.getSid());
            setCellValue(row, colNum++, serial.getMid());
            setCellValue(row, colNum++, serial.getTid());
            setCellValue(row, colNum++, serial.getVpaid());
        } else {
            setCellValue(row, colNum++, "-");
            setCellValue(row, colNum++, "-");
            setCellValue(row, colNum++, "-");
            setCellValue(row, colNum++, "-");
        }

        // Delivery Address
        setCellValue(row, colNum++, transaction.getDeliveryAddress());

        // Remarks
        setCellValue(row, colNum++, transaction.getRemarks());
    }

    private void setCellValue(Row row, int colNum, Object value) {
        Cell cell = row.createCell(colNum);

        if (value == null) {
            cell.setCellValue("");
            return;
        }

        if (value instanceof Number) {
            cell.setCellValue(((Number) value).doubleValue());
        } else if (value instanceof LocalDateTime) {
            cell.setCellValue(value.toString());
        } else {
            cell.setCellValue(value.toString());
        }
    }

    private void autoSizeColumns(Sheet sheet) {
        for (int i = 0; i < 20; i++) {
            try {
                sheet.autoSizeColumn(i);
            } catch (Exception e) {
                log.warn("Could not auto-size column {}", i);
            }
        }
    }

}

