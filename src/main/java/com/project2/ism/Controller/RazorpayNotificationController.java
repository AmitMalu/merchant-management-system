package com.project2.ism.Controller;



import com.project2.ism.DTO.ReportDTO.ApiResponse;
import com.project2.ism.Service.RazorpayTransactionService;
import com.razorpay.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;


@RestController
@RequestMapping("/razorpay")
public class RazorpayNotificationController {

    private static final Logger log = LoggerFactory.getLogger(RazorpayNotificationController.class);
    private final RazorpayTransactionService razorpayTransactionService;

    //@Value("${razorpay.notification.secret}")
    //private String razorpaySecret;

    @Value("${razorpay.webhook.secret}")
    private String webhookSecret;

    public RazorpayNotificationController(RazorpayTransactionService razorpayTransactionService) {
        this.razorpayTransactionService = razorpayTransactionService;
    }


    @PostMapping("/notification")
    public ResponseEntity<String> receiveNotification(@RequestBody String rawJson) {

//        // 1. Auth check – static secret based
//        if (incomingSecret == null || !incomingSecret.equals(razorpaySecret)) {
//            log.warn("Unauthorized Razorpay notification attempt");
//            return ResponseEntity.status(401).body("Unauthorized");
//        }

        // 2. Immediately acknowledge Razorpay
        asyncProcess(rawJson);

        return ResponseEntity.ok("OK");
    }

    @Async("razorpayNotificationExecutor")
    public void asyncProcess(String rawJson) {
        try {
            razorpayTransactionService.process(rawJson);
        } catch (Exception e) {
            log.error("Failed to process Razorpay transaction async", e);
        }
    }
}
