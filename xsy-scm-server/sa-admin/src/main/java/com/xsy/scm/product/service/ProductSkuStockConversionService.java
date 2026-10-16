package com.xsy.scm.product.service;

import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.product.dao.ProductSkuDao;
import com.xsy.scm.product.dao.ProductSkuStockConversionDao;
import com.xsy.scm.product.domain.entity.ProductSkuEntity;
import com.xsy.scm.product.domain.form.ProductSkuStockConversionDeleteForm;
import com.xsy.scm.product.domain.form.ProductSkuStockConversionForm;
import com.xsy.scm.product.domain.vo.ProductSkuStockConversionVO;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.xsy.scm.common.error.ScmCommonErrorCode.VALIDATION_ERROR;

/** 每个 SKU 可以配置销售单位到不同库存单位的换算；历史订单使用确认时快照，不回溯当前配置。 */
@Service
@RequiredArgsConstructor
public class ProductSkuStockConversionService {
    private final ProductSkuDao skuDao;
    private final ProductSkuStockConversionDao conversionDao;

    @Transactional(readOnly = true)
    public List<ProductSkuStockConversionVO> list(Long skuId) {
        return conversionDao.list(skuId, sku(skuId).getSaleUnit());
    }

    @Transactional(rollbackFor = Exception.class)
    public void save(ProductSkuStockConversionForm form) {
        String saleUnit = sku(form.getSkuId()).getSaleUnit();
        String stockUnit = form.getStockUnit().trim();
        BigDecimal factor = form.getStockUnitsPerSaleUnit();
        if (stockUnit.equals(saleUnit) && factor.compareTo(BigDecimal.ONE) != 0) {
            throw new ScmBusinessException(VALIDATION_ERROR);
        }
        conversionDao.upsert(form.getSkuId(), saleUnit, stockUnit, factor, ScmOperator.current());
    }

    @Transactional(rollbackFor = Exception.class)
    public void remove(ProductSkuStockConversionDeleteForm form) {
        conversionDao.delete(form.getSkuId(), sku(form.getSkuId()).getSaleUnit(), form.getStockUnit().trim(),
                ScmOperator.current());
    }

    private ProductSkuEntity sku(Long skuId) {
        if (skuId == null) {
            throw new ScmBusinessException(VALIDATION_ERROR);
        }
        ProductSkuEntity sku = skuDao.selectById(skuId);
        if (sku == null || sku.getSaleUnit() == null || sku.getSaleUnit().isBlank()) {
            throw new ScmBusinessException(VALIDATION_ERROR);
        }
        return sku;
    }
}
