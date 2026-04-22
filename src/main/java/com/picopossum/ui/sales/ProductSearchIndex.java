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
    
    private volatile Map<String, Product> skuIndex = new ConcurrentHashMap<>();
    private volatile Map<String, Product> barcodeIndex = new ConcurrentHashMap<>();
    private volatile List<Product> allProducts = new ArrayList<>();
    private final ProductRepository productRepository;

    public ProductSearchIndex(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    private void ensureIndexed() {
        if (allProducts.isEmpty()) {
            synchronized (this) {
                if (allProducts.isEmpty()) {
                    buildIndex();
                }
            }
        }
    }

    private void buildIndex() {
        List<Product> products = productRepository.findAllActive();
        
        Map<String, Product> newSkuIndex = new HashMap<>();
        Map<String, Product> newBarcodeIndex = new HashMap<>();
        
        for (Product product : products) {
            if (product.sku() != null && !product.sku().isEmpty()) {
                newSkuIndex.put(product.sku().toLowerCase(), product);
            }
            if (product.barcode() != null && !product.barcode().isEmpty()) {
                newBarcodeIndex.put(product.barcode(), product);
            }
        }
        
        this.skuIndex = new ConcurrentHashMap<>(newSkuIndex);
        this.barcodeIndex = new ConcurrentHashMap<>(newBarcodeIndex);
        this.allProducts = List.copyOf(products);
    }

    public Optional<Product> findBySku(String code) {
        ensureIndexed();
        if (code == null || code.isEmpty()) return Optional.empty();
        
        String lowerCode = code.toLowerCase();
        Product p = skuIndex.get(lowerCode);
        if (p == null) {
            p = barcodeIndex.get(code); 
        }
        return Optional.ofNullable(p);
    }

    public List<Product> searchByName(String query) {
        ensureIndexed();
        List<Product> currentProducts = allProducts;
        if (query == null || query.trim().isEmpty()) {
            return currentProducts.stream()
                .limit(50)
                .collect(Collectors.toList());
        }
        
        String lowerQuery = query.toLowerCase();
        return currentProducts.stream()
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
        buildIndex();
    }
}
