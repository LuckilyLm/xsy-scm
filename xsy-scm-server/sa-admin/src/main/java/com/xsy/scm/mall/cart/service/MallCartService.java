package com.xsy.scm.mall.cart.service;

import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.mall.account.constant.MallErrorCode;
import com.xsy.scm.mall.account.domain.vo.RequestMallCustomer;
import com.xsy.scm.mall.cart.constant.MallCartAvailabilityEnum;
import com.xsy.scm.mall.cart.dao.MallCartDao;
import com.xsy.scm.mall.cart.domain.entity.MallCartItemEntity;
import com.xsy.scm.mall.cart.domain.form.MallCartAddForm;
import com.xsy.scm.mall.cart.domain.form.MallCartQuantityForm;
import com.xsy.scm.mall.cart.domain.form.MallCartRemoveForm;
import com.xsy.scm.mall.cart.domain.vo.MallCartItemVO;
import com.xsy.scm.mall.cart.domain.vo.MallCartVO;
import com.xsy.scm.mall.catalog.domain.vo.MallProductVO;
import com.xsy.scm.mall.catalog.service.MallCatalogService;
import com.xsy.scm.pricing.constant.ScmUnavailableReasonEnum;
import com.xsy.scm.pricing.constant.ScmUnpricedReasonEnum;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 服务端购物车：按登录账号隔离，商品可见性、客户价与库存读取时实时解析，不在此固化。
 * 库存与价格的最终校验在结算与下单时由服务端重验。
 */
@Service
@RequiredArgsConstructor
public class MallCartService {
    private static final int SCALE = 4;
    private final MallCartDao cartDao;
    private final MallCatalogService catalogService;

    @Transactional(readOnly = true)
    public MallCartVO cart(RequestMallCustomer customer) {
        return assemble(customer);
    }

    @Transactional(rollbackFor = Exception.class)
    public MallCartVO add(RequestMallCustomer customer, MallCartAddForm form) {
        BigDecimal quantity = parseQuantity(form.getQuantity());
        requireVisible(customer, form.getSkuId());
        MallCartItemEntity existing = cartDao.selectByAccountAndSku(customer.getAccountId(), form.getSkuId());
        if (existing == null) {
            MallCartItemEntity row = new MallCartItemEntity();
            row.setAccountId(customer.getAccountId());
            row.setCustomerId(customer.getCustomerId());
            row.setSkuId(form.getSkuId());
            row.setQuantity(quantity);
            OffsetDateTime now = OffsetDateTime.now();
            row.setCreatedAt(now);
            row.setUpdatedAt(now);
            row.setCreatedBy(ScmOperator.current());
            row.setUpdatedBy(ScmOperator.current());
            cartDao.insert(row);
        } else {
            cartDao.updateQuantity(customer.getAccountId(), form.getSkuId(), existing.getQuantity().add(quantity),
                    ScmOperator.current());
        }
        return assemble(customer);
    }

    @Transactional(rollbackFor = Exception.class)
    public MallCartVO updateQuantity(RequestMallCustomer customer, Long skuId, MallCartQuantityForm form) {
        BigDecimal quantity = parseQuantity(form.getQuantity());
        if (cartDao.selectByAccountAndSku(customer.getAccountId(), skuId) == null) {
            throw new ScmBusinessException(MallErrorCode.CART_ITEM_NOT_FOUND);
        }
        cartDao.updateQuantity(customer.getAccountId(), skuId, quantity, ScmOperator.current());
        return assemble(customer);
    }

    @Transactional(rollbackFor = Exception.class)
    public MallCartVO remove(RequestMallCustomer customer, Long skuId) {
        if (cartDao.removeBySku(customer.getAccountId(), skuId, ScmOperator.current()) == 0) {
            throw new ScmBusinessException(MallErrorCode.CART_ITEM_NOT_FOUND);
        }
        return assemble(customer);
    }

    @Transactional(rollbackFor = Exception.class)
    public MallCartVO removeBatch(RequestMallCustomer customer, MallCartRemoveForm form) {
        cartDao.removeBySkus(customer.getAccountId(), form.getSkuIds().stream().distinct().toList(),
                ScmOperator.current());
        return assemble(customer);
    }

    @Transactional(rollbackFor = Exception.class)
    public MallCartVO clear(RequestMallCustomer customer) {
        cartDao.clear(customer.getAccountId(), ScmOperator.current());
        return assemble(customer);
    }

    private void requireVisible(RequestMallCustomer customer, Long skuId) {
        if (catalogService.visibleProducts(customer, List.of(skuId)).isEmpty()) {
            throw new ScmBusinessException(MallErrorCode.CART_SKU_NOT_VISIBLE);
        }
    }

    private MallCartVO assemble(RequestMallCustomer customer) {
        List<MallCartItemEntity> rows = cartDao.listByAccount(customer.getAccountId());
        MallCartVO result = new MallCartVO();
        result.setTotalCount(rows.size());
        if (rows.isEmpty()) {
            return result;
        }
        Map<Long, MallProductVO> products = catalogService
                .visibleProducts(customer, rows.stream().map(MallCartItemEntity::getSkuId).toList()).stream()
                .collect(Collectors.toMap(MallProductVO::getSkuId, Function.identity()));
        BigDecimal estimated = BigDecimal.ZERO.setScale(SCALE);
        int available = 0;
        for (MallCartItemEntity row : rows) {
            MallCartItemVO item = toItem(row, products.get(row.getSkuId()));
            result.getItems().add(item);
            if (MallCartAvailabilityEnum.AVAILABLE.name().equals(item.getAvailability())) {
                available++;
                estimated = estimated.add(item.getLineAmount());
            }
        }
        result.setAvailableCount(available);
        result.setEstimatedAmount(estimated);
        return result;
    }

    private static MallCartItemVO toItem(MallCartItemEntity row, MallProductVO product) {
        MallCartItemVO item = new MallCartItemVO();
        item.setSkuId(row.getSkuId());
        item.setQuantity(row.getQuantity());
        if (product == null) {
            item.setAvailability(MallCartAvailabilityEnum.UNAVAILABLE.name());
            item.setUnavailableReason(ScmUnavailableReasonEnum.NOT_VISIBLE.name());
            return item;
        }
        item.setProductName(product.getProductName());
        item.setSkuCode(product.getSkuCode());
        item.setSpecName(product.getSpecName());
        item.setSaleUnit(product.getSaleUnit());
        item.setPrimaryImageUrl(product.getPrimaryImageUrl());
        item.setUnitPrice(product.getUnitPrice());
        item.setPriceStatus(product.getPriceStatus());
        item.setPriceSource(product.getPriceSource());
        if (product.getUnitPrice() == null) {
            item.setAvailability(MallCartAvailabilityEnum.NO_PRICE.name());
            item.setUnavailableReason(ScmUnpricedReasonEnum.NO_PRICE_SOURCE.name());
            return item;
        }
        item.setAvailability(MallCartAvailabilityEnum.AVAILABLE.name());
        item.setLineAmount(product.getUnitPrice().multiply(row.getQuantity()).setScale(SCALE, RoundingMode.HALF_UP));
        return item;
    }

    private static BigDecimal parseQuantity(String raw) {
        BigDecimal value;
        try {
            value = new BigDecimal(raw).setScale(SCALE, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException | NumberFormatException exception) {
            throw new ScmBusinessException(MallErrorCode.CART_QUANTITY_INVALID);
        }
        if (value.signum() <= 0) {
            throw new ScmBusinessException(MallErrorCode.CART_QUANTITY_INVALID);
        }
        return value;
    }
}
