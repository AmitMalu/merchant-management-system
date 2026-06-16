package com.project2.ism.Controller;

import com.project2.ism.Service.MosambeeTransactionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/mosambee")
public class MosambeeNotificationController {

    private static final Logger log =
            LoggerFactory.getLogger(MosambeeNotificationController.class);

    private final MosambeeTransactionService mosambeeTransactionService;

    public MosambeeNotificationController(
            MosambeeTransactionService mosambeeTransactionService) {
        this.mosambeeTransactionService = mosambeeTransactionService;
    }

    @PostMapping("/notification")
    public ResponseEntity<String> receiveNotification(
            @RequestBody String rawJson) {

        asyncProcess(rawJson);

        return ResponseEntity.ok("success");
    }

    @Async("mosambeeNotificationExecutor")
    public void asyncProcess(String rawJson) {

        try {
            mosambeeTransactionService.process(rawJson);
        } catch (Exception e) {
            log.error("Failed to process Mosambee transaction", e);
        }
    }
}
