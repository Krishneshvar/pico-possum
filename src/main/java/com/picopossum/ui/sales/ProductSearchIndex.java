package com.picopossum.ui.sales;

import com.picopossum.domain.model.Product;
import com.picopossum.domain.repositories.ProductRepository;
import com.picopossum.shared.dto.PagedResult;
import com.picopossum.shared.dto.ProductFilter;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

public class ProductSearchIndex {
    
    private final Map<String, Product> skuIndex = new ConcurrentHashMap<>();
    private final Map<String, Product> barcodeIndex = new ConcurrentHashMap<>();
    private final List<Product> allProducts = new CopyOnWriteArrayList<>();
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
            if (product.barcode() != null && !product.barcode().isEmpty()) {
                barcodeIndex.put(product.barcode(), product); // Barcodes often case-sensitive/numeric, but doing lower could cause bugs, let's keep exact string but maybe trimmed?
            }
        }
    }

    public Optional<Product> findBySku(String code) {
        if (code == null || code.isEmpty()) return Optional.empty();
        
        String lowerCode = code.toLowerCase();
        Product p = skuIndex.get(lowerCode);
        if (p == null) {
            p = barcodeIndex.get(code); // Try exact barcode match
        }
        return Optional.ofNullable(p);
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
                String barcode = p.barcode() != null ? p.barcode().toLowerCase() : "";
                return productName.contains(lowerQuery) || 
                       sku.contains(lowerQuery) || barcode.contains(lowerQuery);
            })
            .limit(50)
            .collect(Collectors.toList());
    }

    public synchronized void refresh() {
        skuIndex.clear();
        barcodeIndex.clear();
        allProducts.clear();
        buildIndex();
    }
}
