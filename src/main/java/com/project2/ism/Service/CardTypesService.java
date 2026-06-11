package com.project2.ism.Service;

import com.project2.ism.Model.CardTypes;
import com.project2.ism.Repository.CardTypesRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class CardTypesService {

    @Autowired
    private CardTypesRepository cardTypesRepository;

    // Get all card types
    public List<CardTypes> getAllCardTypes() {
        return cardTypesRepository.findAllByOrderByCardNameAsc();
    }

    // Get card type by ID
    public Optional<CardTypes> getCardTypeById(Long id) {
        return cardTypesRepository.findById(id);
    }

    // Get card type by name
    public Optional<CardTypes> getCardTypeByName(String cardName) {
        return cardTypesRepository.findByCardNameIgnoreCase(cardName);
    }

    // Create new card type
    public CardTypes createCardType(CardTypes cardType) {
        // Check if card type already exists
        if (cardTypesRepository.existsByCardNameIgnoreCase(cardType.getCardName())) {
            throw new RuntimeException("Card type with name '" + cardType.getCardName() + "' already exists");
        }
        return cardTypesRepository.save(cardType);
    }

    // Update card type
    public CardTypes updateCardType(Long id, CardTypes cardTypeDetails) {
        CardTypes cardType = cardTypesRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Card type not found with id: " + id));

        // Check if the new name conflicts with existing card types (excluding current one)
        if (!cardType.getCardName().equalsIgnoreCase(cardTypeDetails.getCardName()) &&
                cardTypesRepository.existsByCardNameIgnoreCase(cardTypeDetails.getCardName())) {
            throw new RuntimeException("Card type with name '" + cardTypeDetails.getCardName() + "' already exists");
        }

        cardType.setCardName(cardTypeDetails.getCardName());
        return cardTypesRepository.save(cardType);
    }

    // Delete card type
    public void deleteCardType(Long id) {
        CardTypes cardType = cardTypesRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Card type not found with id: " + id));
        cardTypesRepository.delete(cardType);
    }

    // Check if card type exists
    public boolean cardTypeExists(String cardName) {
        return cardTypesRepository.existsByCardNameIgnoreCase(cardName);
    }

    // Search card types
    public List<CardTypes> searchCardTypes(String searchTerm) {
        return cardTypesRepository.findByCardNameContainingIgnoreCase(searchTerm);
    }
}