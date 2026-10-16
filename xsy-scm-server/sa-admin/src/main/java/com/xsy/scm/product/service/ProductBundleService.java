package com.xsy.scm.product.service;

import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.common.constant.ScmProductTypeEnum;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.util.ScmDecimalStrings;
import com.xsy.scm.product.dao.ProductBundleComponentDao;
import com.xsy.scm.product.dao.ProductSkuDao;
import com.xsy.scm.product.domain.entity.ProductBundleComponentEntity;
import com.xsy.scm.product.domain.entity.ProductSkuEntity;
import com.xsy.scm.product.domain.form.ProductBundleReplaceForm;
import com.xsy.scm.product.domain.vo.ProductBundleComponentVO;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.xsy.scm.common.error.ScmCommonErrorCode.VERSION_CONFLICT;
import static com.xsy.scm.product.constant.ProductErrorCode.BUNDLE_COMPONENT_IN_USE;
import static com.xsy.scm.product.constant.ProductErrorCode.BUNDLE_COMPONENT_INVALID;

@Service
@RequiredArgsConstructor
public class ProductBundleService {
    private final ProductBundleComponentDao bundleDao;
    private final ProductSkuDao productSkuDao;

    public List<ProductBundleComponentVO> components(Long bundleSkuId) {
        return bundleDao.detailByBundleSku(bundleSkuId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void replace(ProductBundleReplaceForm form) {
        List<Long> skuIds = java.util.stream.Stream
                .concat(java.util.stream.Stream.of(form.getBundleSkuId()),
                        form.getComponents().stream().map(ProductBundleReplaceForm.Component::getSkuId))
                .filter(Objects::nonNull).distinct().sorted().toList();
        Map<Long, ProductSkuEntity> locked = productSkuDao.lockByIds(skuIds).stream()
                .collect(Collectors.toMap(ProductSkuEntity::getId, Function.identity()));
        ProductSkuEntity bundle = locked.get(form.getBundleSkuId());
        if (bundle == null || !Objects.equals(bundle.getVersion(), form.getVersion())) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
        if (!ScmProductTypeEnum.STANDARD.name().equals(bundle.getProductType())
                || bundleDao.countByComponentSku(form.getBundleSkuId()) > 0) {
            throw new ScmBusinessException(BUNDLE_COMPONENT_IN_USE);
        }
        Set<Long> distinct = new HashSet<>();
        for (ProductBundleReplaceForm.Component item : form.getComponents()) {
            if (item == null || item.getSkuId() == null || !distinct.add(item.getSkuId())
                    || item.getSkuId().equals(form.getBundleSkuId())) {
                throw new ScmBusinessException(BUNDLE_COMPONENT_INVALID);
            }
            BigDecimal quantity = ScmDecimalStrings.parseScale4Required(item.getQuantityPerBundleSaleUnit());
            ProductSkuEntity component = locked.get(item.getSkuId());
            if (quantity.signum() <= 0 || component == null || component.getSaleUnit() == null
                    || !ScmProductTypeEnum.STANDARD.name().equals(component.getProductType())
                    || !bundleDao.listByBundleSku(item.getSkuId()).isEmpty()) {
                throw new ScmBusinessException(BUNDLE_COMPONENT_INVALID);
            }
        }
        String operator = ScmOperator.current();
        bundleDao.softDeleteByBundleSku(form.getBundleSkuId(), operator);
        OffsetDateTime now = OffsetDateTime.now();
        for (ProductBundleReplaceForm.Component item : form.getComponents()) {
            ProductBundleComponentEntity row = new ProductBundleComponentEntity();
            row.setBundleSkuId(form.getBundleSkuId());
            row.setComponentSkuId(item.getSkuId());
            row.setQuantityPerBundleSaleUnit(
                    ScmDecimalStrings.parseScale4Required(item.getQuantityPerBundleSaleUnit()));
            row.setCreatedAt(now);
            row.setUpdatedAt(now);
            row.setCreatedBy(operator);
            row.setUpdatedBy(operator);
            bundleDao.insert(row);
        }
        if (productSkuDao.bumpVersion(form.getBundleSkuId(), form.getVersion(), operator) != 1) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
    }
}
