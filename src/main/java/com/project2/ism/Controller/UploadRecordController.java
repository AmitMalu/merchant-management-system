package com.project2.ism.Controller;

import com.project2.ism.Exception.ResourceNotFoundException;
import com.project2.ism.Model.UploadRecord;
import com.project2.ism.Service.UploadRecordService;
import com.project2.ism.Service.VidualPayCreditCardService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/upload")
public class UploadRecordController {

    private final Logger log = LoggerFactory.getLogger(UploadRecordController.class);

    private final UploadRecordService service;

    public UploadRecordController(UploadRecordService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<?> upload(
            @RequestParam Long vendorId,
            @RequestParam Long productId,
            @RequestParam MultipartFile file
    ) {

        String fileName = file != null ? file.getOriginalFilename() : null;

        log.info(
                "Vendor transaction file upload request received | vendorId={} | productId={} | fileName={} | fileSize={} bytes",
                vendorId,
                productId,
                fileName,
                file != null ? file.getSize() : 0
        );

        try {

            String response = service.handleFileUpload(
                    vendorId,
                    productId,
                    file
            );

            log.info(
                    "Vendor transaction file upload completed successfully | vendorId={} | productId={} | fileName={}",
                    vendorId,
                    productId,
                    fileName
            );

            return ResponseEntity.ok(response);

        } catch (ResourceNotFoundException e) {

            log.warn(
                    "Vendor transaction file upload failed because resource was not found | vendorId={} | productId={} | fileName={} | reason={}",
                    vendorId,
                    productId,
                    fileName,
                    e.getMessage()
            );

            return ResponseEntity.badRequest()
                    .body("Failed: " + e.getMessage());

        } catch (IllegalArgumentException e) {

            log.warn(
                    "Vendor transaction file validation failed | vendorId={} | productId={} | fileName={} | reason={}",
                    vendorId,
                    productId,
                    fileName,
                    e.getMessage()
            );

            return ResponseEntity.badRequest()
                    .body("Failed: " + e.getMessage());

        } catch (Exception e) {

            log.error(
                    "Unexpected error during vendor transaction file upload | vendorId={} | productId={} | fileName={}",
                    vendorId,
                    productId,
                    fileName,
                    e
            );

            return ResponseEntity.badRequest()
                    .body("Failed: " + e.getMessage());
        }
    }
}
