package com.project2.ism.Repository;

import com.project2.ism.Model.VendorTransactions;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TransactionRepository extends JpaRepository<VendorTransactions,Long> {
}
