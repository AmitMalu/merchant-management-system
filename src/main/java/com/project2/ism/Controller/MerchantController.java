
package com.project2.ism.Controller;


import com.project2.ism.DTO.MerchantFormDTO;
import com.project2.ism.DTO.MerchantListDTO;

import com.project2.ism.DTO.MerchantViewDTO;
import com.project2.ism.Model.Users.Merchant;
import com.project2.ism.Service.FileStorageService;
import com.project2.ism.Service.MerchantService;
import com.project2.ism.request.MerchantSettingsRequest;
import com.project2.ism.response.MerchantSettingsResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/merchants")
public class MerchantController {

    private final MerchantService merchantService;
    private final FileStorageService fileStorageService;

    public MerchantController(MerchantService merchantService, FileStorageService fileStorageService) {
        this.merchantService = merchantService;
        this.fileStorageService = fileStorageService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> createMerchant(@ModelAttribute MerchantFormDTO merchantDTO) {
        try {
            merchantService.createMerchant(merchantDTO);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Merchant created successfully"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Failed to create merchant: " + e.getMessage()
            ));
        }
    }
    @PutMapping(
            value = "/{id}",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<?> updateMerchant(
            @PathVariable Long id,
            @ModelAttribute MerchantFormDTO formDTO
    ) {
        try {
            Merchant updatedMerchant = merchantService.updateMerchant(id, formDTO);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Merchant updated successfully",
                    "data", updatedMerchant
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Failed to update merchant: " + e.getMessage()
            ));
        }
    }
    @GetMapping
    public ResponseEntity<List<MerchantListDTO>> getAllMerchants() {
        try {
            List<MerchantListDTO> merchants = merchantService.getAllMerchantsForList();
            return ResponseEntity.ok(merchants);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<MerchantViewDTO> getMerchantById(@PathVariable Long id) {
        try {
            MerchantViewDTO merchant = merchantService.getMerchantViewDTOById(id);
            return ResponseEntity.ok(merchant);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }



    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteMerchant(@PathVariable Long id) {
        try {
            merchantService.deleteMerchant(id);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Merchant deleted successfully"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Failed to delete merchant: " + e.getMessage()
            ));
        }
    }

    // Get merchants by franchise ID
    @GetMapping("/franchise/{franchiseId}")
    public ResponseEntity<List<MerchantListDTO>> getMerchantsByFranchise(@PathVariable Long franchiseId) {
        try {
            List<MerchantListDTO> merchants = merchantService.getMerchantsByFranchise(franchiseId);
            return ResponseEntity.ok(merchants);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    // Serve uploaded documents
    @GetMapping("/api/documents/{filename:.+}")
    public ResponseEntity<Resource> serveFile(@PathVariable String filename, HttpServletRequest request) {
        try {
            Resource resource = fileStorageService.loadAsResource("documents/" + filename);

            String contentType = request.getServletContext().getMimeType(resource.getFile().getAbsolutePath());
            if (contentType == null) {
                contentType = "application/octet-stream";
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }


    //admin approval jsx uses this
    // 1. Get unapproved merchants
    @GetMapping("/unapproved")
    public List<Merchant> getUnapprovedMerchants() {
        return merchantService.getUnapprovedMerchants();
    }


    //admin approval jsx uses this
    // 2. Approve merchant
    @PutMapping("/{id}/approve")
    public Merchant approveMerchant(@PathVariable Long id) {
        return merchantService.approveMerchant(id);
    }

    //admin approval jsx uses this
    // 3. Reject merchant
    @DeleteMapping("/{id}/reject")
    public ResponseEntity<String> rejectMerchant(@PathVariable Long id) {
        merchantService.rejectMerchant(id);
        return ResponseEntity.ok("Merchant rejected and deleted successfully.");
    }





    @GetMapping("/profile")
    public ResponseEntity<?> profile(@RequestParam String email) {
        Merchant merchant = merchantService.getMerchantByEmail(email);
        BigDecimal walletBalance = merchantService.getWalletBalance(merchant.getId());
        Long franchiseId = (merchant.getFranchise() != null) ? merchant.getFranchise().getId() : null;

        MerchantListDTO dto = new MerchantListDTO(
                merchant.getId(),
                merchant.getBusinessName(),
                merchant.getContactPerson().getEmail(),
                walletBalance,
                franchiseId
        );
        return ResponseEntity.ok(dto);
    }


    //settlement api .js uses this
    @GetMapping("/products/{id}")
    public ResponseEntity<?> merchantProducts(@PathVariable Long id){
        List<?> products = merchantService.getProductsOfMerchant(id);
        return ResponseEntity.ok(products);
    }



    //settlement api .js uses this
    @GetMapping("/direct-merchant")
    public ResponseEntity<List<MerchantListDTO>> directMerchant() {
        try {
            List<MerchantListDTO> merchants = merchantService.getAllDirectMerchantsForList();
            return ResponseEntity.ok(merchants);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/franchise-merchant")
    public ResponseEntity<List<MerchantListDTO>> franchiseMerchant() {
        try {
            List<MerchantListDTO> merchants = merchantService.getAllFranchiseMerchantsForList();
            return ResponseEntity.ok(merchants);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @PutMapping("/{merchantId}/settings")
    public ResponseEntity<MerchantSettingsResponse>
    updateMerchantSettings(
            @PathVariable Long merchantId,
            @RequestBody MerchantSettingsRequest request) {

        MerchantSettingsResponse response =
                merchantService.updateMerchantSettings(
                        merchantId,
                        request
                );

        return ResponseEntity.ok(response);
    }
}
