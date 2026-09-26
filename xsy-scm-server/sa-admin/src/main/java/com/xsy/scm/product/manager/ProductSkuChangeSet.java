package com.xsy.scm.product.manager;

import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.product.domain.entity.ProductSkuEntity;
import com.xsy.scm.product.domain.form.ProductSkuForm;

import java.util.*;

import static com.xsy.scm.product.constant.ProductErrorCode.*;

public record ProductSkuChangeSet(List<ProductSkuForm> inserted, List<ProductSkuForm> updated, List<Long> removedIds) {
    public static ProductSkuChangeSet between(List<ProductSkuEntity> existing, List<ProductSkuForm> requested) {
        Set<Long> ids = new LinkedHashSet<>();
        existing.forEach(e -> ids.add(e.getId()));
        Set<Long> seen = new HashSet<>();
        List<ProductSkuForm> inserted = new ArrayList<>(), updated = new ArrayList<>();
        for (var form : requested) {
            Long id = form.getSkuId();
            if (id == null) {
                inserted.add(form);
                continue;
            }
            if (!ids.contains(id) || !seen.add(id)) throw new ScmBusinessException(SKU_NOT_OWNED);
            if (form.getVersion() == null || form.getVersion() < 0) throw new ScmBusinessException(VERSION_CONFLICT);
            updated.add(form);
        }
        ids.removeAll(seen);
        return new ProductSkuChangeSet(List.copyOf(inserted), List.copyOf(updated), List.copyOf(ids));
    }
}
