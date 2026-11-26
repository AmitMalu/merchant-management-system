package com.project2.ism.DTO.SmsDTO;

import java.time.LocalDateTime;
import java.util.List;

/** What the SMS Settings screen shows: every SMS the system can send and whether it is switched on. */
public record SmsSettingsDTO(boolean surepassSwitchedOn, List<Item> items) {

    /**
     * @param key           our name for the message (used by the toggle call)
     * @param label         the name shown to admins
     * @param enabled       the admin's switch
     * @param templateReady true once a DLT template id is set, so Surepass can send it
     */
    public record Item(String key, String label, boolean enabled, boolean templateReady,
                       String updatedBy, LocalDateTime updatedAt) {
    }
}
