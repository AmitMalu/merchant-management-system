//package com.project2.ism.Controller;
//
//
//import com.project2.ism.DTO.FranchiseFormDTO;
//import com.project2.ism.Model.Users.Franchise;
//import com.project2.ism.Service.FranchiseService;
//import jakarta.validation.Valid;
//import org.springframework.http.MediaType;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.*;
//
//import java.util.List;
//import java.util.Map;
//
//@RestController
//@RequestMapping("/franchise")
//public class FranchiseController {
//
//    private final FranchiseService franchiseService;
//
//    public FranchiseController(FranchiseService franchiseService) {
//        this.franchiseService = franchiseService;
//    }
//
//    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
//    public ResponseEntity<?> createFranchise(@ModelAttribute FranchiseFormDTO formDTO) {
//        franchiseService.createFranchise(formDTO);
//        return ResponseEntity.ok(Map.of(
//                "success", true,
//                "message", "Franchise created successfully"
//        ));
//    }
//
//    @GetMapping
//    public ResponseEntity<List<Franchise>> getAllFranchises() {
//        return ResponseEntity.ok(franchiseService.getAllFranchises());
//    }
//
//    @GetMapping("/{id}")
//    public ResponseEntity<Franchise> getFranchiseById(@PathVariable Long id) {
//        return ResponseEntity.ok(franchiseService.getFranchiseById(id));
//    }
//
//    @PutMapping("/{id}")
//    public ResponseEntity<Franchise> updateFranchise(@PathVariable Long id,
//                                                     @Valid @RequestBody Franchise franchise) {
//        return ResponseEntity.ok(franchiseService.updateFranchise(id, franchise));
//    }
//
//    @DeleteMapping("/{id}")
//    public ResponseEntity<Void> deleteFranchise(@PathVariable Long id) {
//        franchiseService.deleteFranchise(id);
//        return ResponseEntity.noContent().build();
//    }
//}




package com.project2.ism.Controller;


import com.project2.ism.DTO.*;

import com.project2.ism.Model.Users.Franchise;
import com.project2.ism.Service.FileStorageService;
import com.project2.ism.Service.FranchiseService;
import com.project2.ism.Service.MerchantService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/franchise")
public class FranchiseController {

    private final FranchiseService franchiseService;
    private final MerchantService merchantService;
    private final FileStorageService fileStorageService;

    public FranchiseController(FranchiseService franchiseService, MerchantService merchantService, FileStorageService fileStorageService) {
        this.franchiseService = franchiseService;
        this.merchantService = merchantService;
        this.fileStorageService = fileStorageService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> createFranchise(@ModelAttribute FranchiseFormDTO formDTO) {
        try {
            franchiseService.createFranchise(formDTO);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Franchise created successfully"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Failed to create franchise: " + e.getMessage()
            ));
        }
    }

    @PutMapping(
            value = "/{id}",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<?> updateFranchise(
            @PathVariable Long id,
            @ModelAttribute FranchiseFormDTO formDTO
    ) {
        try {
            Franchise updatedFranchise = franchiseService.updateFranchise(id, formDTO);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Franchise updated successfully",
                    "data", updatedFranchise
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Failed to update franchise: " + e.getMessage()
            ));
        }
    }


    //settlement api .js uses this
    @GetMapping
    public ResponseEntity<List<FranchiseListDTO>> getAllFranchises() {
        try {
            List<FranchiseListDTO> franchises = franchiseService.getAllFranchisesWithMerchantCount();
            return ResponseEntity.ok(franchises);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<FranchiseDetailsDTO> getFranchiseById(@PathVariable Long id) {
        try {
            FranchiseViewDTO franchise = franchiseService.getFranchiseViewDTOById(id);
            List<MerchantListDTO> merchants = merchantService.getMerchantsByFranchise(id);
            FranchiseDetailsDTO response = new FranchiseDetailsDTO(franchise, merchants);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }



    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteFranchise(@PathVariable Long id) {
        try {
            franchiseService.deleteFranchise(id);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Franchise deleted successfully"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Failed to delete franchise: " + e.getMessage()
            ));
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

    @GetMapping("/profile")
    public ResponseEntity<?> profile(@RequestParam String email) {
        Franchise franchise = franchiseService.getFranchiseByEmail(email);
        BigDecimal walletBalance = franchiseService.getWalletBalance(franchise.getId());
        FranchiseListDTO dto = new FranchiseListDTO(
                franchise.getId(),
                franchise.getFranchiseName(),
                franchise.getContactPerson().getEmail(),
                walletBalance
        );
        return ResponseEntity.ok(dto);
    }




    //settlement api .js uses this
    @GetMapping("/products/{id}")
    public ResponseEntity<?> franchiseProducts(@PathVariable Long id){
        List<?> products = franchiseService.getProductsOfFranchise(id);
        return ResponseEntity.ok(products);
    }

    @GetMapping("/serial-num-to-dispatch") // outwardId
    public ResponseEntity<List<ProductSerialDTO>> getSerialNums(@RequestParam Long productId, @RequestParam Long franchiseId) {
        List<ProductSerialDTO> serialNums = franchiseService.getValidPSN(productId,franchiseId)
                .stream()
                .map(ProductSerialDTO::fromEntity)
                .toList();

        return ResponseEntity.ok(serialNums);
    }





}