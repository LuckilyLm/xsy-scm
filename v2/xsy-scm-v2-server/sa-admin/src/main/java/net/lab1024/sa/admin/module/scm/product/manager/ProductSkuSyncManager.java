package net.lab1024.sa.admin.module.scm.product.manager;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.common.constant.ScmOperator;
import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.product.dao.ProductSkuDao;
import net.lab1024.sa.admin.module.scm.product.domain.entity.ProductSkuEntity;
import net.lab1024.sa.admin.module.scm.product.domain.form.ProductSkuForm;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;
import java.time.OffsetDateTime;
import java.util.*;
import static net.lab1024.sa.admin.module.scm.product.constant.ProductErrorCode.*;

@Component
@RequiredArgsConstructor
public class ProductSkuSyncManager {
    private final ProductSkuDao dao;
    public List<ProductSkuEntity> existing(Long spuId) {
        return dao.selectList(new LambdaQueryWrapper<ProductSkuEntity>().eq(ProductSkuEntity::getSpuId,spuId)
                .orderByAsc(ProductSkuEntity::getSortOrder,ProductSkuEntity::getId));
    }
    public void sync(Long spuId,ProductSkuChangeSet changes) {
        dao.clearDefault(spuId);
        for (var form:changes.updated()) {
            var entity=entity(spuId,form); entity.setId(form.getSkuId()); entity.setVersion(form.getVersion());
            if (dao.updateById(entity)!=1) throw new ScmBusinessException(VERSION_CONFLICT);
        }
        for (var form:changes.inserted()) {
            var entity=entity(spuId,form); entity.setVersion(0); entity.setCreatedAt(entity.getUpdatedAt()); entity.setCreatedBy(entity.getUpdatedBy());
            dao.insert(entity);
        }
        remove(changes.removedIds());
    }
    public void remove(List<Long> ids) {
        if (ids.isEmpty()) return;
        dao.update(null,new LambdaUpdateWrapper<ProductSkuEntity>().in(ProductSkuEntity::getId,ids)
            .set(ProductSkuEntity::getUpdatedAt,OffsetDateTime.now()).set(ProductSkuEntity::getUpdatedBy,ScmOperator.current())
            .setSql("version = version + 1"));
        dao.deleteByIds(ids);
    }
    private ProductSkuEntity entity(Long spuId,ProductSkuForm form) {
        var entity=new ProductSkuEntity(); BeanUtils.copyProperties(form,entity);
        entity.setSpuId(spuId); entity.setSkuCode(ProductAggregateValidator.normalizeCode(form.getSkuCode()));
        entity.setBarcode(ProductAggregateValidator.trimToNull(form.getBarcode())); entity.setSpecName(form.getSpecName().trim());
        entity.setSaleUnit(form.getSaleUnit().trim()); entity.setUpdatedAt(OffsetDateTime.now()); entity.setUpdatedBy(ScmOperator.current());
        return entity;
    }
}
