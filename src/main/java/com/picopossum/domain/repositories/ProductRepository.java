package com.picopossum.domain.repositories;

import com.picopossum.domain.model.Product;
import com.picopossum.shared.dto.PagedResult;
import com.picopossum.shared.dto.ProductFilter;

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

    Map<String, Object> getProductStats();

    int getNextGeneratedNumericSku();
}
