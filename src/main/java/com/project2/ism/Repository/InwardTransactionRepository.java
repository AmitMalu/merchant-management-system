package com.project2.ism.Repository;

import com.project2.ism.Model.InventoryTransactions.InwardTransactions;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Repository
public interface InwardTransactionRepository extends JpaRepository<InwardTransactions,Long> {

    @Query("SELECT it.vendor.id, COUNT(it) FROM InwardTransactions it GROUP BY it.vendor.id")
    List<Object[]> groupByVendor();

    default Map<String, Long> countGroupByVendor() {
        return groupByVendor().stream()
                .collect(Collectors.toMap(
                        row -> String.valueOf(row[0]),
                        row -> (Long) row[1]
                ));
    }
}
