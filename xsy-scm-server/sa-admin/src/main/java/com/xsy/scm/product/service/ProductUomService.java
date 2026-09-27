package com.xsy.scm.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.common.constant.ScmEnableStatusEnum;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.product.dao.ProductUomDao;
import com.xsy.scm.product.domain.entity.ProductUomEntity;
import com.xsy.scm.product.domain.form.ProductAssistantQueryForm;
import com.xsy.scm.product.domain.form.ProductUomAddForm;
import com.xsy.scm.product.domain.form.ProductUomKeyForm;
import com.xsy.scm.product.domain.form.ProductUomUpdateForm;
import com.xsy.scm.product.domain.vo.ProductUomVO;
import com.xsy.scm.product.manager.ProductAggregateValidator;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import static com.xsy.scm.product.constant.ProductErrorCode.UOM_CODE_DUPLICATE;
import static com.xsy.scm.product.constant.ProductErrorCode.UOM_NAME_DUPLICATE;
import static com.xsy.scm.product.constant.ProductErrorCode.UOM_NOT_FOUND;
import static com.xsy.scm.product.constant.ProductErrorCode.UOM_NOT_USABLE;
import static com.xsy.scm.product.constant.ProductErrorCode.UOM_REFERENCED;
import static com.xsy.scm.product.constant.ProductErrorCode.VERSION_CONFLICT;

/**
 * 计量单位辅助资料。字典只作为商品销售单位、供应商采购单位的取值来源，业务字段仍存名称字符串， 所以单位名称在活动行内唯一，被引用后只能停用、不能删除。
 */
@Service
@RequiredArgsConstructor
public class ProductUomService {
    private final ProductUomDao productUomDao;

    public List<ProductUomVO> list(ProductAssistantQueryForm query) {
        return productUomDao.selectWithReference(query == null ? new ProductAssistantQueryForm() : query);
    }

    /**
     * 下拉只出启用单位；停用单位不再出现在新配置里，历史商品字段不受影响。
     */
    public List<ProductUomVO> options() {
        var query = new ProductAssistantQueryForm();
        query.setStatus(ScmEnableStatusEnum.ENABLED.name());
        return productUomDao.selectWithReference(query);
    }

    /**
     * 只复核本次新写入或改动的单位名：必须在字典中且处于启用态，并锁定命中的活动行， 与 {@link #delete} 互斥。字典里没有这个名称时不锁行，调用方按「未维护」放行历史值。
     */
    public void assertUsable(Collection<String> unitNames) {
        var names = unitNames.stream().filter(n -> n != null && !n.isBlank()).map(String::trim).distinct().toList();
        if (names.isEmpty())
            return;
        var found = productUomDao.selectNamesForUpdate(names);
        var enabled = ScmEnableStatusEnum.ENABLED.name();
        if (found.size() != names.size() || found.stream().anyMatch(u -> !enabled.equals(u.getStatus()))) {
            throw new ScmBusinessException(UOM_NOT_USABLE);
        }
    }

    @Transactional
    public Long add(ProductUomAddForm form) {
        var entity = new ProductUomEntity();
        BeanUtils.copyProperties(form, entity, "version");
        entity.setUomCode(ProductAggregateValidator.normalizeCode(form.getUomCode()));
        entity.setName(form.getName().trim());
        assertUnique(entity.getUomCode(), entity.getName(), null);
        stamp(entity);
        entity.setCreatedAt(entity.getUpdatedAt());
        entity.setCreatedBy(entity.getUpdatedBy());
        try {
            productUomDao.insert(entity);
        } catch (DuplicateKeyException e) {
            throw duplicate(e);
        }
        return entity.getId();
    }

    @Transactional
    public void update(ProductUomUpdateForm form) {
        var entity = require(form.getUomId(), form.getVersion());
        entity.setCategory(form.getCategory());
        entity.setPrecisionScale(form.getPrecisionScale());
        entity.setStatus(form.getStatus());
        entity.setSortOrder(form.getSortOrder());
        entity.setVersion(form.getVersion());
        stamp(entity);
        if (productUomDao.updateById(entity) != 1)
            throw new ScmBusinessException(VERSION_CONFLICT);
    }

    /**
     * 行锁后复核引用数，避免「校验时为 0、提交时已被并发引用」。
     */
    @Transactional
    public void delete(ProductUomKeyForm form) {
        var entity = productUomDao.selectForUpdate(form.getUomId());
        if (entity == null)
            throw new ScmBusinessException(UOM_NOT_FOUND);
        if (!Objects.equals(entity.getVersion(), form.getVersion()))
            throw new ScmBusinessException(VERSION_CONFLICT);
        if (productUomDao.selectVoById(entity.getId()).getReferencedCount() > 0) {
            throw new ScmBusinessException(UOM_REFERENCED);
        }
        stamp(entity);
        if (productUomDao.updateById(entity) != 1)
            throw new ScmBusinessException(VERSION_CONFLICT);
        productUomDao.deleteById(entity.getId());
    }

    /**
     * 与 uk_scm_uom_code_active / uk_scm_uom_name_active 同域的应用级预检，给出可解释的错误码。
     */
    private void assertUnique(String code, String name, Long self) {
        if (productUomDao.selectCount(new LambdaQueryWrapper<ProductUomEntity>().eq(ProductUomEntity::getUomCode, code)
                .ne(self != null, ProductUomEntity::getId, self)) > 0)
            throw new ScmBusinessException(UOM_CODE_DUPLICATE);
        if (productUomDao.selectCount(new LambdaQueryWrapper<ProductUomEntity>().eq(ProductUomEntity::getName, name)
                .ne(self != null, ProductUomEntity::getId, self)) > 0)
            throw new ScmBusinessException(UOM_NAME_DUPLICATE);
    }

    private ProductUomEntity require(Long id, Integer version) {
        var entity = productUomDao.selectById(id);
        if (entity == null)
            throw new ScmBusinessException(UOM_NOT_FOUND);
        if (!Objects.equals(entity.getVersion(), version))
            throw new ScmBusinessException(VERSION_CONFLICT);
        return entity;
    }

    private void stamp(ProductUomEntity entity) {
        entity.setUpdatedAt(OffsetDateTime.now());
        entity.setUpdatedBy(ScmOperator.current());
    }

    private ScmBusinessException duplicate(DuplicateKeyException e) {
        String constraint = e.getMostSpecificCause().getMessage();
        if (constraint != null && constraint.contains("uk_scm_uom_name_active"))
            return new ScmBusinessException(UOM_NAME_DUPLICATE);
        return new ScmBusinessException(UOM_CODE_DUPLICATE);
    }
}
