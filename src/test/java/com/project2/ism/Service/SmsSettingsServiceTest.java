package com.project2.ism.Service;

import com.project2.ism.DTO.SmsDTO.SmsSettingsDTO;
import com.project2.ism.Model.Sms.SmsTemplate;
import com.project2.ism.Repository.SmsTemplateRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** The admin screen: lists every SMS with its switch, and changes only the switch. */
class SmsSettingsServiceTest {

    private final SmsTemplateRepository repo = mock(SmsTemplateRepository.class);
    private final SurepassSmsService surepass = mock(SurepassSmsService.class);
    private final SmsSettingsService service = new SmsSettingsService(repo, surepass);

    private SmsTemplate row(long id, String key, String label, boolean on, String templateId) throws Exception {
        SmsTemplate t = new SmsTemplate();
        var idField = SmsTemplate.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(t, id);
        t.setTemplateKey(key);
        t.setLabel(label);
        t.setEnabled(on);
        t.setTemplateId(templateId);
        return t;
    }

    @Test
    void listsEveryMessage_withItsSwitch_andWhetherItsTemplateIsReady() throws Exception {
        when(surepass.isSurepassSwitchedOn()).thenReturn(false);
        when(repo.findAll()).thenReturn(List.of(
                row(2, "B", "Second", false, null),
                row(1, "A", "First", true, "1777179568172116977")));

        SmsSettingsDTO dto = service.list();

        assertFalse(dto.surepassSwitchedOn());
        assertEquals(List.of("A", "B"), dto.items().stream().map(SmsSettingsDTO.Item::key).toList());
        assertTrue(dto.items().get(0).enabled());
        assertTrue(dto.items().get(0).templateReady());
        assertFalse(dto.items().get(1).enabled());
        assertFalse(dto.items().get(1).templateReady());
    }

    @Test
    void switchingChangesOnlyTheSwitch_andRecordsWho() throws Exception {
        SmsTemplate t = row(1, "A", "First", true, "1777179568172116977");
        when(repo.findByTemplateKey("A")).thenReturn(Optional.of(t));
        when(repo.save(any(SmsTemplate.class))).thenAnswer(i -> i.getArgument(0));

        SmsSettingsDTO.Item item = service.setEnabled("A", false, "admin@example.com");

        assertFalse(item.enabled());
        assertEquals("admin@example.com", item.updatedBy());
        assertEquals("1777179568172116977", t.getTemplateId());
    }

    @Test
    void settingTheSameValueAgain_changesNothing() throws Exception {
        when(repo.findByTemplateKey("A")).thenReturn(Optional.of(row(1, "A", "First", true, null)));

        service.setEnabled("A", true, "admin@example.com");

        verify(repo, never()).save(any());
    }

    @Test
    void anUnknownMessageIsRejected() {
        when(repo.findByTemplateKey("NOPE")).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.setEnabled("NOPE", true, "x"));
    }
}
