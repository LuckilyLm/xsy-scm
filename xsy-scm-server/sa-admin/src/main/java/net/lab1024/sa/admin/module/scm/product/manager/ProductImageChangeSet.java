package net.lab1024.sa.admin.module.scm.product.manager;

import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.product.domain.entity.ProductImageEntity;
import net.lab1024.sa.admin.module.scm.product.domain.form.ProductImageForm;

import java.util.*;

import static net.lab1024.sa.admin.module.scm.product.constant.ProductErrorCode.*;

public record ProductImageChangeSet(List<ProductImageForm> inserted, List<ProductImageForm> updated,
                                    List<Long> removedIds) {
    public static ProductImageChangeSet between(List<ProductImageEntity> existing, List<ProductImageForm> requested) {
        Set<Long> ids = new LinkedHashSet<>();
        existing.forEach(e -> ids.add(e.getId()));
        Set<Long> seen = new HashSet<>();
        List<ProductImageForm> inserted = new ArrayList<>(), updated = new ArrayList<>();
        for (var form : requested) {
            Long id = form.getImageId();
            if (id == null) {
                inserted.add(form);
                continue;
            }
            if (!ids.contains(id) || !seen.add(id)) throw new ScmBusinessException(IMAGE_NOT_OWNED);
            if (form.getVersion() == null || form.getVersion() < 0) throw new ScmBusinessException(VERSION_CONFLICT);
            updated.add(form);
        }
        ids.removeAll(seen);
        return new ProductImageChangeSet(List.copyOf(inserted), List.copyOf(updated), List.copyOf(ids));
    }
}
