package com.project2.ism.Service;

import com.project2.ism.DTO.SmsDTO.SmsSettingsDTO;
import com.project2.ism.Model.Sms.SmsTemplate;
import com.project2.ism.Repository.SmsTemplateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * The admin's on/off switch for each SMS (payment success, complaint, ...). Only the switch can be
 * changed here; the DLT template details are not editable from the screen.
 */
@Service
public class SmsSettingsService {

    private static final Logger log = LoggerFactory.getLogger(SmsSettingsService.class);

    private final SmsTemplateRepository templates;
    private final SurepassSmsService surepass;

    public SmsSettingsService(SmsTemplateRepository templates, SurepassSmsService surepass) {
        this.templates = templates;
        this.surepass = surepass;
    }

    public SmsSettingsDTO list() {
        List<SmsSettingsDTO.Item> items = templates.findAll().stream()
                .sorted(Comparator.comparing(SmsTemplate::getId))
                .map(this::toItem)
                .toList();
        return new SmsSettingsDTO(surepass.isSurepassSwitchedOn(), items);
    }

    public SmsSettingsDTO.Item setEnabled(String key, boolean enabled, String changedBy) {
        SmsTemplate row = templates.findByTemplateKey(key)
                .orElseThrow(() -> new IllegalArgumentException("Unknown SMS: " + key));
        if (row.isEnabled() != enabled) {
            row.setEnabled(enabled);
            row.setUpdatedBy(changedBy);
            row = templates.save(row);
            log.info("SMS '{}' switched {} by {}", key, enabled ? "ON" : "OFF", changedBy);
        }
        return toItem(row);
    }

    private SmsSettingsDTO.Item toItem(SmsTemplate t) {
        return new SmsSettingsDTO.Item(t.getTemplateKey(),
                t.getLabel() != null ? t.getLabel() : t.getTemplateKey(),
                t.isEnabled(),
                t.getTemplateId() != null && !t.getTemplateId().isBlank(),
                t.getUpdatedBy(), t.getUpdatedAt());
    }
}
