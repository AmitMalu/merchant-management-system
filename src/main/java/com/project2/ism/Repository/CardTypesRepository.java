package com.project2.ism.Repository;

import com.project2.ism.Model.CardTypes;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CardTypesRepository extends JpaRepository<CardTypes, Long> {

    // Find card type by name (case insensitive)
    Optional<CardTypes> findByCardNameIgnoreCase(String cardName);

    // Check if card type exists by name (case insensitive)
    boolean existsByCardNameIgnoreCase(String cardName);

    // Find all card types ordered by name
    List<CardTypes> findAllByOrderByCardNameAsc();

    // Find card types by name containing (for search functionality)
    List<CardTypes> findByCardNameContainingIgnoreCase(String cardName);
}