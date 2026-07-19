package com.project2.ism.Service;

// Service/UploadRecordService.java

import com.project2.ism.Controller.UploadRecordController;
import com.project2.ism.Exception.ResourceNotFoundException;
import com.project2.ism.Model.Product;
import com.project2.ism.Model.VendorTransactions;
import com.project2.ism.Model.UploadRecord;
import com.project2.ism.Model.Vendor.Vendor;
import com.project2.ism.Repository.ProductRepository;
import com.project2.ism.Repository.TransactionRepository;
import com.project2.ism.Repository.UploadRecordRepository;
import com.project2.ism.Repository.VendorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Locale;

@Service
public class UploadRecordService {

    private final Logger log = LoggerFactory.getLogger(UploadRecordService.class);

    private final TransactionRepository transactionRepository;
    private final UploadRecordRepository uploadRecordRepository;
    private final VendorRepository vendorRepository;
    private final ProductRepository productRepository;
    private final ExcelParser excelParser;
    private final CsvParser csvParser;


    public UploadRecordService(TransactionRepository tr, UploadRecordRepository ur, VendorRepository vendorRepository, ProductRepository productRepository, ExcelParser excelParser, CsvParser csvParser) {
        this.transactionRepository = tr;
        this.uploadRecordRepository = ur;
        this.vendorRepository = vendorRepository;
        this.productRepository = productRepository;
        this.excelParser = excelParser;
        this.csvParser = csvParser;
    }

    @Transactional
    public String handleFileUpload(
            Long vendorId,
            Long productId,
            MultipartFile file
    ) throws Exception {

        log.info(
                "Starting vendor transaction file processing | vendorId={} | productId={}",
                vendorId,
                productId
        );

        validateUploadFile(file);

        String originalFileName = file.getOriginalFilename();
        String normalizedFileName = originalFileName
                .trim()
                .toLowerCase(Locale.ROOT);

        log.info(
                "Upload file validated | vendorId={} | productId={} | fileName={} | contentType={} | fileSize={} bytes",
                vendorId,
                productId,
                originalFileName,
                file.getContentType(),
                file.getSize()
        );

        Vendor vendor = vendorRepository.findById(vendorId)
                .orElseThrow(() -> {
                    log.warn(
                            "Vendor not found during file upload | vendorId={}",
                            vendorId
                    );

                    return new ResourceNotFoundException(
                            "Vendor not found with ID: " + vendorId
                    );
                });

        log.debug(
                "Vendor found successfully | vendorId={}",
                vendorId
        );

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> {
                    log.warn(
                            "Product not found during file upload | productId={}",
                            productId
                    );

                    return new ResourceNotFoundException(
                            "Product not found with ID: " + productId
                    );
                });

        log.debug(
                "Product found successfully | productId={}",
                productId
        );

        List<VendorTransactions> transactions;

        if (normalizedFileName.endsWith(".csv")) {

            log.info(
                    "Parsing vendor transaction CSV file | fileName={}",
                    originalFileName
            );

            transactions = csvParser.parse(file.getInputStream());

        } else if (normalizedFileName.endsWith(".xlsx")
                || normalizedFileName.endsWith(".xls")) {

            log.info(
                    "Parsing vendor transaction Excel file | fileName={}",
                    originalFileName
            );

            transactions = excelParser.parse(file.getInputStream());

        } else {

            log.warn(
                    "Unsupported vendor transaction file type | fileName={} | contentType={}",
                    originalFileName,
                    file.getContentType()
            );

            throw new IllegalArgumentException(
                    "Unsupported file type. Upload CSV, XLS or XLSX only."
            );
        }

        if (transactions == null || transactions.isEmpty()) {

            log.warn(
                    "No valid transactions found in uploaded file | vendorId={} | productId={} | fileName={}",
                    vendorId,
                    productId,
                    originalFileName
            );

            throw new IllegalArgumentException(
                    "The uploaded file does not contain any valid transactions."
            );
        }

        log.info(
                "Vendor transaction file parsed successfully | vendorId={} | productId={} | fileName={} | transactionCount={}",
                vendorId,
                productId,
                originalFileName,
                transactions.size()
        );

        try {

            transactionRepository.saveAll(transactions);

            log.info(
                    "Vendor transactions saved successfully | vendorId={} | productId={} | fileName={} | savedCount={}",
                    vendorId,
                    productId,
                    originalFileName,
                    transactions.size()
            );

            UploadRecord uploadRecord = new UploadRecord();
            uploadRecord.setVendor(vendor);
            uploadRecord.setProduct(product);
            uploadRecord.setFileName(originalFileName);

            uploadRecordRepository.save(uploadRecord);

            log.info(
                    "Upload record saved successfully | vendorId={} | productId={} | fileName={}",
                    vendorId,
                    productId,
                    originalFileName
            );

        } catch (Exception e) {

            log.error(
                    "Database error while saving vendor transaction upload | vendorId={} | productId={} | fileName={} | transactionCount={}",
                    vendorId,
                    productId,
                    originalFileName,
                    transactions.size(),
                    e
            );

            throw e;
        }

        log.info(
                "Vendor transaction file processing completed | vendorId={} | productId={} | fileName={} | transactionCount={}",
                vendorId,
                productId,
                originalFileName,
                transactions.size()
        );

        return "File uploaded successfully. Total transactions saved: "
                + transactions.size();
    }

    private void validateUploadFile(MultipartFile file) {

        if (file == null) {
            log.warn("File upload validation failed because file is null");
            throw new IllegalArgumentException("File is required.");
        }

        if (file.isEmpty()) {
            log.warn(
                    "File upload validation failed because file is empty | fileName={}",
                    file.getOriginalFilename()
            );

            throw new IllegalArgumentException(
                    "Uploaded file is empty."
            );
        }

        if (file.getOriginalFilename() == null
                || file.getOriginalFilename().isBlank()) {

            log.warn(
                    "File upload validation failed because filename is missing"
            );

            throw new IllegalArgumentException(
                    "Uploaded file name is missing."
            );
        }
    }
}
