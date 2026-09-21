package net.lab1024.sa.admin.module.scm.order.manager;

import net.lab1024.sa.admin.module.scm.order.domain.entity.*;
import net.lab1024.sa.admin.module.scm.order.domain.form.*;
import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;

import static net.lab1024.sa.admin.module.scm.order.constant.OrderErrorCode.*;

import java.math.BigDecimal;
import java.util.*;

public record SalesOrderItemChangeSet(List<SalesOrderItemEntity> inserted, List<SalesOrderItemEntity> updated,
                                      List<SalesOrderItemEntity> removed) {
    public static SalesOrderItemChangeSet between(List<SalesOrderItemEntity> existing, List<SalesOrderItemEntity> requested) {
        var unmatched = new LinkedHashMap<Long, SalesOrderItemEntity>();
        existing.forEach(x -> unmatched.put(x.getId(), x));
        var inserted = new ArrayList<SalesOrderItemEntity>();
        var updated = new ArrayList<SalesOrderItemEntity>();
        for (var x : requested) {
            if (x.getId() == null) {
                inserted.add(x);
                continue;
            }
            var old = unmatched.remove(x.getId());
            if (old == null) throw new ScmBusinessException(ORDER_ITEM_NOT_OWNED);
            if (x.getVersion() == null) throw new ScmBusinessException(ORDER_ITEM_VERSION_REQUIRED);
            if (!Objects.equals(x.getVersion(), old.getVersion()))
                throw new ScmBusinessException(ORDER_ITEM_VERSION_CONFLICT);
            x.setCreatedAt(old.getCreatedAt());
            x.setCreatedBy(old.getCreatedBy());
            updated.add(x);
        }
        return new SalesOrderItemChangeSet(inserted, updated, new ArrayList<>(unmatched.values()));
    }
}
