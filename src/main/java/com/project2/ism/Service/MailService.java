package com.project2.ism.Service;

import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    @Autowired
    private JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    /**
     * Send a plain-text email.
     */
    @Async
    public void sendEmail(List<String> to, String subject, String body) {
        sendHtmlEmail(to, subject, toHtml(body));
    }

    /**
     * Send an HTML email directly.
     * This is the core method — all emails go through here.
     */
    @Async
    public void sendHtmlEmail(List<String> to, String subject, String htmlBody) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    message,
                    MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
                    StandardCharsets.UTF_8.name()
            );

            helper.setFrom(new InternetAddress(fromEmail, "UtsabPay"));
            helper.setTo(to.toArray(new String[0]));
            helper.setSubject(subject);
            helper.setText(htmlBody, true); // true = HTML

            mailSender.send(message);
            log.info("Email sent to {} | Subject: {}", to, subject);

        } catch (Exception e) {
            log.error("Email sending failed to {} | Subject: {} | Error: {}", to, subject, e.getMessage(), e);
            throw new RuntimeException("Failed to send email: " + e.getMessage(), e);
        }
    }

    /**
     * Wraps plain text in a minimal HTML wrapper for consistent rendering.
     */
    private String toHtml(String plainText) {
        String lines = plainText
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\n", "<br/>");
        return "<div style=\"font-family:Arial,sans-serif;font-size:14px;color:#333;\">" + lines + "</div>";
    }
}
