package com.project2.ism.Controller;

import com.project2.ism.Model.CardTypes;
import com.project2.ism.Service.CardTypesService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/card-types")
public class CardTypesController {

    @Autowired
    private CardTypesService cardTypesService;

    // Get all card types
    @GetMapping
    public ResponseEntity<List<CardTypes>> getAllCardTypes() {
        try {
            List<CardTypes> cardTypes = cardTypesService.getAllCardTypes();
            return ResponseEntity.ok(cardTypes);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Get card type by ID
    @GetMapping("/{id}")
    public ResponseEntity<CardTypes> getCardTypeById(@PathVariable Long id) {
        try {
            Optional<CardTypes> cardType = cardTypesService.getCardTypeById(id);
            return cardType.map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Create new card type
    @PostMapping
    public ResponseEntity<?> createCardType(@Valid @RequestBody CardTypes cardType) {
        try {
            CardTypes createdCardType = cardTypesService.createCardType(cardType);
            return ResponseEntity.status(HttpStatus.CREATED).body(createdCardType);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error creating card type: " + e.getMessage());
        }
    }

    // Update card type
    @PutMapping("/{id}")
    public ResponseEntity<?> updateCardType(@PathVariable Long id, @Valid @RequestBody CardTypes cardTypeDetails) {
        try {
            CardTypes updatedCardType = cardTypesService.updateCardType(id, cardTypeDetails);
            return ResponseEntity.ok(updatedCardType);
        } catch (RuntimeException e) {
            if (e.getMessage().contains("not found")) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error updating card type: " + e.getMessage());
        }
    }

    // Delete card type
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteCardType(@PathVariable Long id) {
        try {
            cardTypesService.deleteCardType(id);
            return ResponseEntity.ok().body("Card type deleted successfully");
        } catch (RuntimeException e) {
            if (e.getMessage().contains("not found")) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error deleting card type: " + e.getMessage());
        }
    }

    // Search card types
    @GetMapping("/search")
    public ResponseEntity<List<CardTypes>> searchCardTypes(@RequestParam String q) {
        try {
            List<CardTypes> cardTypes = cardTypesService.searchCardTypes(q);
            return ResponseEntity.ok(cardTypes);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Check if card type exists
    @GetMapping("/exists")
    public ResponseEntity<Boolean> cardTypeExists(@RequestParam String cardName) {
        try {
            boolean exists = cardTypesService.cardTypeExists(cardName);
            return ResponseEntity.ok(exists);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}