package net.lab1024.sa.admin.module.scm.product.service;

import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.common.constant.ScmOperator;
import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.product.dao.ProductSpuDao;
import net.lab1024.sa.admin.module.scm.product.domain.entity.ProductSkuEntity;
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
import static net.lab1024.sa.admin.module.scm.product.manager.ProductAggregateValidator.trimToNull;

@Service
@RequiredArgsConstructor
public class ProductSpuService {
    private final ProductSpuDao dao;
    private final ProductCategoryService categories;
    private final ProductAggregateValidator validator;
    private final ProductSkuSyncManager skus;
    private final ProductImageSyncManager images;
    private final ProductUomService uom;
    private final ProductTagService tags;

    @Transactional
    public Long add(ProductSpuAddForm form) {
        validator.validateSpu(form); categories.requireSelectableCategory(form.getCategoryId());
        uom.assertUsable(form.getSkuList().stream().map(ProductSkuForm::getSaleUnit).toList());
        tags.assertUsable(form.getTagIds());
        var skuChanges=ProductSkuChangeSet.between(List.of(),form.getSkuList());
        var imageChanges=ProductImageChangeSet.between(List.of(),form.getImages());
        var entity=new ProductSpuEntity(); apply(entity,form); entity.setCreatedAt(entity.getUpdatedAt()); entity.setCreatedBy(entity.getUpdatedBy());
        try { dao.insert(entity); skus.sync(entity.getId(),skuChanges); images.sync(entity.getId(),imageChanges); }
        catch (DuplicateKeyException e) { throw duplicate(e); }
        tags.replaceTags(List.of(entity.getId()),form.getTagIds());
        return entity.getId();
    }
    @Transactional
    public void update(ProductSpuUpdateForm form) {
        validator.validateSpu(form); categories.requireSelectableCategory(form.getCategoryId());
        var entity=require(form.getSpuId(),form.getVersion());
        var existing=skus.existing(entity.getId());
        var skuChanges=ProductSkuChangeSet.between(existing,form.getSkuList());
        uom.assertUsable(changedUnits(existing,skuChanges));
        tags.assertNewBindings(entity.getId(),form.getTagIds());
        var imageChanges=ProductImageChangeSet.between(images.existing(entity.getId()),form.getImages());
        apply(entity,form); entity.setVersion(form.getVersion());
        try {
            if (dao.updateById(entity)!=1) throw new ScmBusinessException(VERSION_CONFLICT);
            skus.sync(entity.getId(),skuChanges); images.sync(entity.getId(),imageChanges);
        } catch (DuplicateKeyException e) { throw duplicate(e); }
        tags.replaceTags(List.of(entity.getId()),form.getTagIds());
    }
    @Transactional
    public void updateStatus(ProductStatusForm form) {
        var entity=require(form.getSpuId(),form.getVersion());
        // 归档商品退出经营，DB 有 CHECK 兜底；这里先给出可解释的业务错误。
        assertSaleCompatible(entity.getMasterStatus(),form.getStatus());
        entity.setStatus(form.getStatus()); stamp(entity);
        if (dao.updateById(entity)!=1) throw new ScmBusinessException(VERSION_CONFLICT);
    }
    @Transactional
    public void delete(ProductDeleteForm form) {
        var entity=require(form.getSpuId(),form.getVersion());
        if (dao.hasBusinessReference(entity.getId())) throw new ScmBusinessException(PRODUCT_BUSINESS_REFERENCED);
        stamp(entity);
        if (dao.updateById(entity)!=1) throw new ScmBusinessException(VERSION_CONFLICT);
        tags.untagProducts(List.of(entity.getId()));
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
        BeanUtils.copyProperties(form,entity,"version","masterStatus");
        entity.setSpuCode(ProductAggregateValidator.normalizeCode(form.getSpuCode())); entity.setName(form.getName().trim());
        entity.setAlias(trimToNull(form.getAlias())); entity.setDescription(trimToNull(form.getDescription()));
        entity.setMnemonicCode(trimToNull(form.getMnemonicCode())); entity.setBrandName(trimToNull(form.getBrandName()));
        entity.setOrigin(trimToNull(form.getOrigin())); entity.setInvoiceName(trimToNull(form.getInvoiceName()));
        entity.setTaxCategoryCode(trimToNull(form.getTaxCategoryCode()));
        // 主档状态与在售状态正交：表单不传就是「不改」，新增时才落到 ENABLED。
        if (form.getMasterStatus()!=null) entity.setMasterStatus(form.getMasterStatus());
        if (entity.getMasterStatus()==null) entity.setMasterStatus("ENABLED");
        assertSaleCompatible(entity.getMasterStatus(),entity.getStatus());
        stamp(entity);
    }
    private void assertSaleCompatible(String masterStatus,String status) {
        if ("ARCHIVED".equals(masterStatus) && "ON_SHELF".equals(status)) throw new ScmBusinessException(MASTER_STATUS_SALE_CONFLICT);
    }
    /**
     * 只复核新增或改过单位的 SKU：单位字典晚于既有商品建立，
     * 未改动的历史值即便不在字典里也必须允许原样保存，否则每次编辑都会被拦住。
     */
    private Collection<String> changedUnits(List<ProductSkuEntity> existing,ProductSkuChangeSet changes) {
        Map<Long,String> before=new HashMap<>(); existing.forEach(s -> before.put(s.getId(),s.getSaleUnit()));
        List<String> units=new ArrayList<>();
        changes.inserted().forEach(s -> units.add(s.getSaleUnit()));
        changes.updated().forEach(s -> {
            var skuId=s.getSkuId();
            if (skuId==null || !Objects.equals(before.get(skuId),trimToNull(s.getSaleUnit()))) units.add(s.getSaleUnit());
        });
        return units;
    }
    private void stamp(ProductSpuEntity entity) { entity.setUpdatedAt(OffsetDateTime.now()); entity.setUpdatedBy(ScmOperator.current()); }
    private ScmBusinessException duplicate(DuplicateKeyException e) {
        String constraint=e.getMostSpecificCause().getMessage();
        if(constraint!=null && constraint.contains("uk_product_sku_code")) return new ScmBusinessException(SKU_CODE_DUPLICATE);
        if(constraint!=null && constraint.contains("uk_product_sku_barcode")) return new ScmBusinessException(SKU_BARCODE_DUPLICATE);
        return new ScmBusinessException(PRODUCT_CODE_DUPLICATE);
    }
}
