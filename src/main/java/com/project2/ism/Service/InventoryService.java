package com.project2.ism.Service;

import com.project2.ism.DTO.InventoryDTO;
import com.project2.ism.Model.InventoryTransactions.ProductSerialNumbers;
import com.project2.ism.Model.Product;
import com.project2.ism.Repository.ProductRepository;
import com.project2.ism.Repository.ProductSerialsRepository;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

@Service
public class InventoryService {


    private final ProductRepository productRepo;


    private final ProductSerialsRepository serialRepo;

    public InventoryService(ProductRepository productRepo, ProductSerialsRepository serialRepo) {
        this.productRepo = productRepo;
        this.serialRepo = serialRepo;
    }

    public Page<InventoryDTO> getInventory(String search, Pageable pageable) {
        Page<Product> products;

        if (search != null && !search.trim().isEmpty()) {
            products = productRepo.searchProducts(search, pageable);
        } else {
            products = productRepo.findAll(pageable);
        }

        return products.map(p -> {
            List<ProductSerialNumbers> serials =
                    serialRepo.findByProduct_Id(p.getId());

            int totalQty = serials.size();

            int returnedQty = (int) serials.stream()
                    .filter(s -> s.getReturnTransaction() != null)
                    .count();

            int availableQty = (int) serials.stream()
                    .filter(s -> s.getOutwardTransaction() == null && s.getReturnTransaction() == null)
                    .count();

            return new InventoryDTO(
                    p.getProductCode(),
                    p.getProductName(),
                    p.getVendor().getName(),
                    totalQty,
                    returnedQty,
                    availableQty
            );
        });
    }
}

