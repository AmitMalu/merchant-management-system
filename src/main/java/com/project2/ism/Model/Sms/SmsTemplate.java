package com.project2.ism.Model.Sms;

import jakarta.persistence.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * One SMS the system can send (for example BILL_AVENUE_PAYMENT) and the DLT details it needs.
 * Kept in the database so a new message, or a new DLT template id, needs no code change.
 * Through Surepass a message is only sent when it is enabled AND has a template id.
 */
@Entity
@Table(name = "sms_templates",
        uniqueConstraints = @UniqueConstraint(name = "uk_sms_template_key", columnNames = "template_key"))
public class SmsTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Our own name for the message, e.g. BILL_AVENUE_PAYMENT. */
    @Column(name = "template_key", nullable = false, length = 60)
    private String templateKey;

    /** The DLT header (sender id) the template was registered with, e.g. SMCRSO. */
    @Column(name = "sender_id", length = 20)
    private String senderId;

    /** The DLT template id (about 19 digits). Empty until the template is approved. */
    @Column(name = "template_id", length = 40)
    private String templateId;

    /** The registered text with {#var#} placeholders. For reference only; the vendor sends the registered text. */
    @Column(name = "message_text", length = 1000)
    private String messageText;

    /** The name shown to admins on the SMS Settings screen. */
    @Column(name = "label", length = 100)
    private String label;

    /**
     * The admin's switch for this message. Off means NO sender sends it (Surepass or the old one).
     * On means it is sent, through Surepass once that is ready (template id set), otherwise as before.
     */
    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    /** Who last switched it, for the record. */
    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTemplateKey() { return templateKey; }
    public void setTemplateKey(String templateKey) { this.templateKey = templateKey; }

    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }

    public String getTemplateId() { return templateId; }
    public void setTemplateId(String templateId) { this.templateId = templateId; }

    public String getMessageText() { return messageText; }
    public void setMessageText(String messageText) { this.messageText = messageText; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
