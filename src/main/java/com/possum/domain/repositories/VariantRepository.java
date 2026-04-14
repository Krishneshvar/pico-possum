package com.possum.domain.repositories;

import com.possum.domain.model.Variant;
import com.possum.shared.dto.PagedResult;

import java.util.Map;
import java.util.Optional;

public interface VariantRepository {
    long insertVariant(long productId, Variant variant);

    Optional<Variant> findVariantByIdSync(long id);

    int updateVariantById(Variant variant);

    int softDeleteVariant(long id);

    int getNextGeneratedNumericSku();

    PagedResult<Variant> findVariants(String searchTerm,
                                      Long categoryId,
                                      java.util.List<Long> categories,
                                      java.util.List<Long> taxCategories,
                                      java.util.List<String> stockStatus,
                                      java.util.List<String> status,
                                      java.math.BigDecimal minPrice,
                                      java.math.BigDecimal maxPrice,
                                      String sortBy,
                                      String sortOrder,
                                      int currentPage,
                                      int itemsPerPage);

    Map<String, Object> getVariantStats();
}
