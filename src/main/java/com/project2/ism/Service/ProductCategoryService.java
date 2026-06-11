package com.project2.ism.Service;


import com.project2.ism.Model.ProductCategory;
import com.project2.ism.Repository.ProductCategoryRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ProductCategoryService {

    private final ProductCategoryRepository repository;

    public ProductCategoryService(ProductCategoryRepository repository) {
        this.repository = repository;
    }

    public List<ProductCategory> getAllCategories() {
        return repository.findAll();
    }

    public Optional<ProductCategory> getCategoryById(Long id) {
        return repository.findById(id);
    }

    public ProductCategory createCategory(ProductCategory category) {
        // Optional duplicate check
        if (repository.existsByCategoryName(category.getCategoryName())) {
            throw new RuntimeException("Category name already exists");
        }
        if (repository.existsByCategoryCode(category.getCategoryCode())) {
            throw new RuntimeException("Category code already exists");
        }
        return repository.save(category);
    }

    public ProductCategory updateCategory(Long id, ProductCategory categoryDetails) {
        ProductCategory category = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Category not found with id " + id));

        category.setCategoryName(categoryDetails.getCategoryName());
        category.setCategoryCode(categoryDetails.getCategoryCode());
        category.setProductCount(categoryDetails.getProductCount());

        return repository.save(category);
    }

    public void deleteCategory(Long id) {
        if (!repository.existsById(id)) {
            throw new RuntimeException("Category not found with id " + id);
        }
        repository.deleteById(id);
    }
}