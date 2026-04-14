package com.possum.ui.sales;

import com.possum.domain.model.Variant;
import com.possum.domain.repositories.VariantRepository;
import com.possum.shared.dto.PagedResult;

import java.util.*;
import java.util.stream.Collectors;

public class ProductSearchIndex {
    
    private final Map<String, Variant> barcodeIndex = new HashMap<>();
    private final Map<String, Variant> skuIndex = new HashMap<>();
    private final List<Variant> allVariants = new ArrayList<>();
    private final VariantRepository variantRepository;

    public ProductSearchIndex(VariantRepository variantRepository) {
        this.variantRepository = variantRepository;
        buildIndex();
    }

    private void buildIndex() {
        PagedResult<Variant> result = variantRepository.findVariants(
            null, null, null, null, null, List.of("active"), null, null, "name", "ASC", 0, 10000
        );
        
        allVariants.addAll(result.items());
        
        for (Variant variant : allVariants) {
            if (variant.sku() != null && !variant.sku().isEmpty()) {
                skuIndex.put(variant.sku().toLowerCase(), variant);
            }
        }
    }

    public Optional<Variant> findByBarcode(String code) {
        if (code == null || code.isEmpty()) return Optional.empty();
        return Optional.ofNullable(barcodeIndex.get(code.toLowerCase()));
    }

    public Optional<Variant> findBySku(String code) {
        if (code == null || code.isEmpty()) return Optional.empty();
        return Optional.ofNullable(skuIndex.get(code.toLowerCase()));
    }

    public List<Variant> searchByName(String query) {
        if (query == null || query.trim().isEmpty()) {
            return allVariants.stream()
                .limit(50)
                .collect(Collectors.toList());
        }
        
        String lowerQuery = query.toLowerCase();
        return allVariants.stream()
            .filter(v -> {
                String productName = v.productName() != null ? v.productName().toLowerCase() : "";
                String variantName = v.name() != null ? v.name().toLowerCase() : "";
                String sku = v.sku() != null ? v.sku().toLowerCase() : "";
                return productName.contains(lowerQuery) || 
                       variantName.contains(lowerQuery) || 
                       sku.contains(lowerQuery);
            })
            .limit(50)
            .collect(Collectors.toList());
    }

    public void refresh() {
        barcodeIndex.clear();
        skuIndex.clear();
        allVariants.clear();
        buildIndex();
    }
}
