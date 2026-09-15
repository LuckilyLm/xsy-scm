package net.lab1024.sa.admin.module.scm.product.service;

import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.common.constant.ScmOperator;
import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.product.dao.ProductSpuDao;
import net.lab1024.sa.admin.module.scm.product.domain.entity.ProductSpuEntity;
import net.lab1024.sa.admin.module.scm.product.domain.form.*;
import net.lab1024.sa.admin.module.scm.product.manager.*;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.util.*;
import static net.lab1024.sa.admin.module.scm.product.constant.ProductErrorCode.*;

@Service
@RequiredArgsConstructor
public class ProductSpuService {
    private final ProductSpuDao dao;
    private final ProductCategoryService categories;
    private final ProductAggregateValidator validator;
    private final ProductSkuSyncManager skus;
    private final ProductImageSyncManager images;

    @Transactional
    public Long add(ProductSpuAddForm form) {
        validator.validateSpu(form); categories.requireSelectableCategory(form.getCategoryId());
        var skuChanges=ProductSkuChangeSet.between(List.of(),form.getSkuList());
        var imageChanges=ProductImageChangeSet.between(List.of(),form.getImages());
        var entity=new ProductSpuEntity(); apply(entity,form); entity.setCreatedAt(entity.getUpdatedAt()); entity.setCreatedBy(entity.getUpdatedBy());
        try { dao.insert(entity); skus.sync(entity.getId(),skuChanges); images.sync(entity.getId(),imageChanges); }
        catch (DuplicateKeyException e) { throw duplicate(e); }
        return entity.getId();
    }
    @Transactional
    public void update(ProductSpuUpdateForm form) {
        validator.validateSpu(form); categories.requireSelectableCategory(form.getCategoryId());
        var entity=require(form.getSpuId(),form.getVersion());
        var skuChanges=ProductSkuChangeSet.between(skus.existing(entity.getId()),form.getSkuList());
        var imageChanges=ProductImageChangeSet.between(images.existing(entity.getId()),form.getImages());
        apply(entity,form); entity.setVersion(form.getVersion());
        try {
            if (dao.updateById(entity)!=1) throw new ScmBusinessException(VERSION_CONFLICT);
            skus.sync(entity.getId(),skuChanges); images.sync(entity.getId(),imageChanges);
        } catch (DuplicateKeyException e) { throw duplicate(e); }
    }
    @Transactional
    public void updateStatus(ProductStatusForm form) {
        var entity=require(form.getSpuId(),form.getVersion()); entity.setStatus(form.getStatus()); stamp(entity);
        if (dao.updateById(entity)!=1) throw new ScmBusinessException(VERSION_CONFLICT);
    }
    @Transactional
    public void delete(ProductDeleteForm form) {
        var entity=require(form.getSpuId(),form.getVersion()); stamp(entity);
        if (dao.updateById(entity)!=1) throw new ScmBusinessException(VERSION_CONFLICT);
        skus.remove(skus.existing(entity.getId()).stream().map(s -> s.getId()).toList());
        images.remove(images.existing(entity.getId()).stream().map(i -> i.getId()).toList());
        dao.deleteById(entity.getId());
    }
    private ProductSpuEntity require(Long id,Integer version) {
        var entity=dao.selectById(id);
        if (entity==null) throw new ScmBusinessException(PRODUCT_NOT_FOUND);
        if (version==null || !Objects.equals(entity.getVersion(),version)) throw new ScmBusinessException(VERSION_CONFLICT);
        return entity;
    }
    private void apply(ProductSpuEntity entity,ProductSpuAddForm form) {
        BeanUtils.copyProperties(form,entity,"version");
        entity.setSpuCode(ProductAggregateValidator.normalizeCode(form.getSpuCode())); entity.setName(form.getName().trim());
        entity.setAlias(ProductAggregateValidator.trimToNull(form.getAlias())); entity.setDescription(ProductAggregateValidator.trimToNull(form.getDescription())); stamp(entity);
    }
    private void stamp(ProductSpuEntity entity) { entity.setUpdatedAt(OffsetDateTime.now()); entity.setUpdatedBy(ScmOperator.current()); }
    private ScmBusinessException duplicate(DuplicateKeyException e) {
        String constraint=e.getMostSpecificCause().getMessage();
        if(constraint!=null && constraint.contains("uk_product_sku_code")) return new ScmBusinessException(SKU_CODE_DUPLICATE);
        if(constraint!=null && constraint.contains("uk_product_sku_barcode")) return new ScmBusinessException(SKU_BARCODE_DUPLICATE);
        return new ScmBusinessException(PRODUCT_CODE_DUPLICATE);
    }
}
