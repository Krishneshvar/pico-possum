package com.picopossum.ui.sales;

import com.picopossum.domain.model.Product;
import com.picopossum.domain.repositories.ProductRepository;
import com.picopossum.shared.dto.PagedResult;
import com.picopossum.shared.dto.ProductFilter;

import java.util.*;
import java.util.stream.Collectors;

public class ProductSearchIndex {
    
    private final Map<String, Product> skuIndex = new HashMap<>();
    private final List<Product> allProducts = new ArrayList<>();
    private final ProductRepository productRepository;

    public ProductSearchIndex(ProductRepository productRepository) {
        this.productRepository = productRepository;
        buildIndex();
    }

    private void buildIndex() {
        ProductFilter filter = new ProductFilter(null, List.of("active"), null, 0, 10000, "name", "ASC");
        PagedResult<Product> result = productRepository.findProducts(filter);
        
        allProducts.clear();
        allProducts.addAll(result.items());
        
        for (Product product : allProducts) {
            if (product.sku() != null && !product.sku().isEmpty()) {
                skuIndex.put(product.sku().toLowerCase(), product);
            }
        }
    }

    public Optional<Product> findBySku(String code) {
        if (code == null || code.isEmpty()) return Optional.empty();
        return Optional.ofNullable(skuIndex.get(code.toLowerCase()));
    }

    public List<Product> searchByName(String query) {
        if (query == null || query.trim().isEmpty()) {
            return allProducts.stream()
                .limit(50)
                .collect(Collectors.toList());
        }
        
        String lowerQuery = query.toLowerCase();
        return allProducts.stream()
            .filter(p -> {
                String productName = p.name() != null ? p.name().toLowerCase() : "";
                String sku = p.sku() != null ? p.sku().toLowerCase() : "";
                return productName.contains(lowerQuery) || 
                       sku.contains(lowerQuery);
            })
            .limit(50)
            .collect(Collectors.toList());
    }

    public void refresh() {
        skuIndex.clear();
        allProducts.clear();
        buildIndex();
    }
}
