package com.project2.ism.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project2.ism.DTO.Vendor.VendorCredentialsRequestDTO;
import com.project2.ism.DTO.Vendor.VendorCredentialsResponseDTO;
import com.project2.ism.Model.Payment.PaymentVendor;
import com.project2.ism.Model.Payment.PaymentVendorCredentials;
import com.project2.ism.Repository.PaymentProductRepository;
import com.project2.ism.Repository.PaymentVendorCredentialsRepository;
import com.project2.ism.Repository.PaymentVendorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The Edit form needs the saved keys and user ids; the list must never carry them.
 */
class PaymentVendorCredentialsServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private PaymentVendorCredentialsRepository repo;
    private PaymentVendorCredentialsService service;
    private PaymentVendorCredentials saved;

    @BeforeEach
    void setUp() {
        repo = mock(PaymentVendorCredentialsRepository.class);
        service = new PaymentVendorCredentialsService(repo, mock(PaymentVendorRepository.class), mock(PaymentProductRepository.class));

        PaymentVendor vendor = new PaymentVendor();
        ReflectionTestUtils.setField(vendor, "id", 3L);
        vendor.setVendorName("SuivrePay");

        saved = new PaymentVendorCredentials();
        ReflectionTestUtils.setField(saved, "id", 9L);
        saved.setPaymentVendor(vendor);
        saved.setBaseUrlUat("https://uat.example/api");
        saved.setSecretKeyUat("uat-secret");
        saved.setSaltKeyUat("uat-salt");
        saved.setEncryptDecryptKeyUat("uat-enc");
        saved.setUserIdUat("uat-user");
        saved.setBaseUrlProd("https://prod.example/api");
        saved.setSecretKeyProd("prod-secret");
        saved.setSaltKeyProd("prod-salt");
        saved.setEncryptDecryptKeyProd("prod-enc");
        saved.setUserIdProd("prod-user");
        saved.setActiveEnvironment("PROD");
        saved.setIsActive(true);

        when(repo.findById(9L)).thenReturn(Optional.of(saved));
        when(repo.findAll()).thenReturn(List.of(saved));
        when(repo.save(any(PaymentVendorCredentials.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void editForm_getsTheSavedKeysAndUserIds() {
        VendorCredentialsResponseDTO dto = service.getOne(9L);

        assertEquals("uat-secret", dto.getSecretKeyUat());
        assertEquals("uat-salt", dto.getSaltKeyUat());
        assertEquals("uat-enc", dto.getEncryptDecryptKeyUat());
        assertEquals("uat-user", dto.getUserIdUat());
        assertEquals("prod-secret", dto.getSecretKeyProd());
        assertEquals("prod-salt", dto.getSaltKeyProd());
        assertEquals("prod-enc", dto.getEncryptDecryptKeyProd());
        assertEquals("prod-user", dto.getUserIdProd());
        // and everything it had before
        assertEquals("https://uat.example/api", dto.getBaseUrlUat());
        assertEquals("https://prod.example/api", dto.getBaseUrlProd());
        assertEquals(3L, dto.getVendorId());
        assertEquals("PROD", dto.getActiveEnvironment());
    }

    @Test
    void theList_neverCarriesKeysOrUserIds() throws Exception {
        VendorCredentialsResponseDTO row = service.getAll().get(0);

        assertNull(row.getSecretKeyUat());
        assertNull(row.getSaltKeyProd());
        assertNull(row.getUserIdUat());

        JsonNode json = mapper.readTree(mapper.writeValueAsString(service.getAll()));
        String text = json.toString();
        assertFalse(text.contains("uat-secret"));
        assertFalse(text.contains("prod-secret"));
        assertFalse(text.contains("secretKey"), text);
        assertFalse(text.contains("saltKey"), text);
        assertFalse(text.contains("encryptDecryptKey"), text);
        assertFalse(text.contains("userId"), text);
        assertTrue(text.contains("baseUrlUat"));   // the rest of the row is unchanged
    }

    @Test
    void createAndUpdateResponses_doNotEchoTheKeysBack() {
        VendorCredentialsRequestDTO request = new VendorCredentialsRequestDTO();
        request.setActiveEnvironment("PROD");
        request.setIsActive(true);
        request.setSecretKeyProd("new-prod-secret");

        VendorCredentialsResponseDTO response = service.update(9L, request);

        assertNull(response.getSecretKeyProd());
        assertNull(response.getSecretKeyUat());
    }

    @Test
    void aFieldLeftEmptyInTheForm_keepsItsSavedValue_andANewValueReplacesIt() {
        VendorCredentialsRequestDTO request = new VendorCredentialsRequestDTO();
        request.setActiveEnvironment("UAT");
        request.setIsActive(true);
        request.setSecretKeyUat("");               // left empty
        request.setSaltKeyUat(null);               // not sent
        request.setUserIdUat("changed-user");      // changed

        service.update(9L, request);

        assertEquals("uat-secret", saved.getSecretKeyUat());
        assertEquals("uat-salt", saved.getSaltKeyUat());
        assertEquals("changed-user", saved.getUserIdUat());
        assertEquals("prod-secret", saved.getSecretKeyProd());   // untouched
        assertEquals("UAT", saved.getActiveEnvironment());
    }

    @Test
    void emptyColumnsInTheDatabase_comeBackEmptyNotAsText() throws Exception {
        saved.setSecretKeyProd(null);
        saved.setUserIdProd(null);

        VendorCredentialsResponseDTO dto = service.getOne(9L);
        JsonNode json = mapper.readTree(mapper.writeValueAsString(dto));

        assertNull(dto.getSecretKeyProd());
        assertFalse(json.has("secretKeyProd"));      // left out; the form shows it empty
        assertTrue(json.has("secretKeyUat"));
    }
}
