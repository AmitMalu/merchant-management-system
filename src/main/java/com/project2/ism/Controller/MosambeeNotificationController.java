package com.project2.ism.Controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project2.ism.Service.MosambeeTransactionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
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

//    @PostMapping("/notification")
//    public ResponseEntity<String> receiveNotification(
//            @RequestBody String rawJson) {
//
//        asyncProcess(rawJson);
//
//        return ResponseEntity.ok("success");
//    }
//
//    @Async("mosambeeNotificationExecutor")
//    public void asyncProcess(String rawJson) {
//
//        try {
//            mosambeeTransactionService.process(rawJson);
//        } catch (Exception e) {
//            log.error("Failed to process Mosambee transaction", e);
//        }
//    }

    @PostMapping("/notification")
    public ResponseEntity<String> receiveNotification(
            @RequestBody String rawJson) {

        // Log incoming request
        log.info("Received notification request");
        log.debug("Raw JSON payload: {}", rawJson);

        // Validate input
        if (rawJson == null || rawJson.trim().isEmpty()) {
            log.warn("Received empty notification payload");
            return ResponseEntity.badRequest().body("Empty payload");
        }

        // Log payload size for monitoring
        log.info("Processing notification payload of size: {} bytes", rawJson.getBytes().length);

        // Track start time for performance monitoring
        long startTime = System.currentTimeMillis();

        try {
            // Submit for async processing
            asyncProcess(rawJson);

            long processingTime = System.currentTimeMillis() - startTime;
            log.info("Notification submitted for async processing in {}ms", processingTime);

            // Return immediate acknowledgment
            return ResponseEntity.ok("success");

        } catch (Exception e) {
            log.error("Failed to submit notification for async processing", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Processing failed");
        }
    }

    @Async("mosambeeNotificationExecutor")
    public void asyncProcess(String rawJson) {

        String transactionId = extractTransactionId(rawJson); // Helper method to extract ID

        log.info("Starting async processing for notification, transactionId: {}", transactionId);
        long startTime = System.currentTimeMillis();

        try {
            // Process the notification
            log.debug("Processing notification with payload: {}", rawJson);
            mosambeeTransactionService.process(rawJson);

            long processingTime = System.currentTimeMillis() - startTime;
            log.info("Successfully processed notification async for transactionId: {} in {}ms",
                    transactionId, processingTime);

        } catch (Exception e) {
            long processingTime = System.currentTimeMillis() - startTime;
            log.error("Failed to process Mosambee transaction asynchronously, transactionId: {}, duration: {}ms",
                    transactionId, processingTime, e);

            // Optional: Send to dead letter queue or error handler
            handleProcessingError(rawJson, e);
        }
    }

    // Helper method to extract transaction ID from JSON (implementation depends on JSON structure)
    private String extractTransactionId(String rawJson) {
        try {
            // Example using Jackson
            ObjectMapper mapper = new ObjectMapper();
            JsonNode node = mapper.readTree(rawJson);
            JsonNode txIdNode = node.get("transactionId");
            return txIdNode != null ? txIdNode.asText() : "unknown";
        } catch (Exception e) {
            log.warn("Could not extract transactionId from payload");
            return "unknown";
        }
    }

    private void handleProcessingError(String rawJson, Exception e) {
        log.error("Moving failed notification to error queue: {}", rawJson);
        // Implement dead letter queue logic or retry mechanism
    }
}
