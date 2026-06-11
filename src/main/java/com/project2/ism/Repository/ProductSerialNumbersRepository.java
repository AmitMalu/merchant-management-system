package com.project2.ism.Repository;

import com.project2.ism.Model.InventoryTransactions.ProductSerialNumbers;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductSerialNumbersRepository extends JpaRepository<ProductSerialNumbers, Long> {

    Optional<ProductSerialNumbers> findByMid(String mid);

}
