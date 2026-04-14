package com.possum.domain.repositories;

import com.possum.domain.model.Product;
import com.possum.domain.model.TaxRule;
import com.possum.domain.model.Variant;
import com.possum.shared.dto.PagedResult;
import com.possum.shared.dto.ProductFilter;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface ProductRepository {
    long insertProduct(Product product);

    Optional<Product> findProductById(long id);

    Optional<String> findProductImagePath(long id);

    int updateProductById(long productId, Product product);

    int softDeleteProduct(long id);

    PagedResult<Product> findProducts(ProductFilter filter);

    Optional<ProductWithVariants> findProductWithVariants(long productId);

    List<TaxRule> findProductTaxes(long productId);

    void setProductTaxes(long productId, List<Long> taxIds);

    Map<String, Object> getProductStats();

    record ProductWithVariants(Product product, List<Variant> variants) {
    }
}
