package net.lab1024.sa.admin.module.scm.product.manager;
import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.product.domain.entity.ProductSkuEntity;
import net.lab1024.sa.admin.module.scm.product.domain.form.ProductSkuForm;
import java.util.*;
import static net.lab1024.sa.admin.module.scm.product.constant.ProductErrorCode.*;
public record ProductSkuChangeSet(List<ProductSkuForm> inserted, List<ProductSkuForm> updated, List<Long> removedIds) {
    public static ProductSkuChangeSet between(List<ProductSkuEntity> existing, List<ProductSkuForm> requested) {
        Set<Long> ids=new LinkedHashSet<>(); existing.forEach(e -> ids.add(e.getId()));
        Set<Long> seen=new HashSet<>();
        List<ProductSkuForm> inserted=new ArrayList<>(), updated=new ArrayList<>();
        for (var form:requested) {
            Long id=form.getSkuId();
            if (id==null) { inserted.add(form); continue; }
            if (!ids.contains(id) || !seen.add(id)) throw new ScmBusinessException(SKU_NOT_OWNED);
            if (form.getVersion()==null || form.getVersion()<0) throw new ScmBusinessException(VERSION_CONFLICT);
            updated.add(form);
        }
        ids.removeAll(seen);
        return new ProductSkuChangeSet(List.copyOf(inserted),List.copyOf(updated),List.copyOf(ids));
    }
}
