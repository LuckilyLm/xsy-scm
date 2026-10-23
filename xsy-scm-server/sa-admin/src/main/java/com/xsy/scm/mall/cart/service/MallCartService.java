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
import com.xsy.scm.mall.cart.domain.vo.MallCartEstimateVO;
import com.xsy.scm.mall.cart.domain.vo.MallCartItemVO;
import com.xsy.scm.mall.cart.domain.vo.MallCartVO;
import com.xsy.scm.mall.catalog.domain.vo.MallProductVO;
import com.xsy.scm.mall.catalog.service.MallCatalogService;
import com.xsy.scm.mall.catalog.service.MallQuantityPolicy;
import com.xsy.scm.pricing.constant.ScmUnavailableReasonEnum;
import com.xsy.scm.pricing.constant.ScmUnpricedReasonEnum;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 服务端购物车：按登录账号隔离，商品可见性、客户价与库存读取时实时解析，不在此固化。 库存与价格的最终校验在结算与下单时由服务端重验。
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

    /**
     * 所选商品的预估金额与可结算行数。
     *
     * <p>
     * 客户端不得自行累计业务金额，勾选变化后由本接口给出权威结果。
     */
    @Transactional(readOnly = true)
    public MallCartEstimateVO estimate(RequestMallCustomer customer, List<Long> skuIds) {
        MallCartVO cart = assemble(customer);
        Set<Long> selected = new HashSet<>(skuIds);
        BigDecimal amount = BigDecimal.ZERO.setScale(SCALE);
        List<Long> unavailable = new ArrayList<>();
        int available = 0;
        for (MallCartItemVO item : cart.getItems()) {
            if (!selected.contains(item.getSkuId())) {
                continue;
            }
            if (MallCartAvailabilityEnum.AVAILABLE.name().equals(item.getAvailability())) {
                available++;
                amount = amount.add(item.getLineAmount());
            } else {
                unavailable.add(item.getSkuId());
            }
        }
        MallCartEstimateVO result = new MallCartEstimateVO();
        result.setEstimatedAmount(amount);
        result.setAvailableCount(available);
        result.setSelectedCount(selected.size());
        result.setUnavailableSkuIds(unavailable);
        return result;
    }

    /**
     * 加购是累加语义。并发两个「数量 +1」若各自读旧值再覆盖写，会丢一次；因此先在行锁上排队， 行不存在时靠部分唯一索引串行化首次插入，冲突方回退到锁行累加。
     */
    @Transactional(rollbackFor = Exception.class)
    public MallCartVO add(RequestMallCustomer customer, MallCartAddForm form) {
        MallProductVO product = requireVisible(customer, form.getSkuId());
        boolean quickAdd = Boolean.TRUE.equals(form.getQuickAdd());
        BigDecimal quantity = quickAdd
                ? (product.getMinOrderQty() == null ? BigDecimal.ONE : product.getMinOrderQty())
                : parseQuantity(form.getQuantity());
        MallCartItemEntity existing = cartDao.lockByAccountAndSku(customer.getAccountId(), form.getSkuId());
        while (existing == null) {
            MallQuantityPolicy.require(product, quantity);
            if (cartDao.insertOnConflictDoNothing(newRow(customer, form.getSkuId(), quantity)) == 1) {
                return assemble(customer);
            }
            // 唯一索引冲突说明并发方已建行；锁行后转为累加。若对方随即又删掉，循环重试插入。
            existing = cartDao.lockByAccountAndSku(customer.getAccountId(), form.getSkuId());
        }
        BigDecimal increment = quickAdd
                ? (product.getStepQty() == null ? BigDecimal.ONE : product.getStepQty()) : quantity;
        BigDecimal total = existing.getQuantity().add(increment);
        MallQuantityPolicy.require(product, total);
        cartDao.updateQuantity(customer.getAccountId(), form.getSkuId(), total, ScmOperator.current());
        return assemble(customer);
    }

    private static MallCartItemEntity newRow(RequestMallCustomer customer, Long skuId, BigDecimal quantity) {
        MallCartItemEntity row = new MallCartItemEntity();
        row.setAccountId(customer.getAccountId());
        row.setCustomerId(customer.getCustomerId());
        row.setSkuId(skuId);
        row.setQuantity(quantity);
        OffsetDateTime now = OffsetDateTime.now();
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        row.setCreatedBy(ScmOperator.current());
        row.setUpdatedBy(ScmOperator.current());
        return row;
    }

    @Transactional(rollbackFor = Exception.class)
    public MallCartVO updateQuantity(RequestMallCustomer customer, Long skuId, MallCartQuantityForm form) {
        BigDecimal quantity = parseQuantity(form.getQuantity());
        if (cartDao.selectByAccountAndSku(customer.getAccountId(), skuId) == null) {
            throw new ScmBusinessException(MallErrorCode.CART_ITEM_NOT_FOUND);
        }
        MallQuantityPolicy.require(requireVisible(customer, skuId), quantity);
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

    /**
     * 把某规格的数量<b>设为</b>指定值（覆盖，不是累加）。「再来一单」用它。
     *
     * <p>
     * 刻意不写成累加：再来一单重复点击必须得到同一结果，累加会让客户点两次就买两份。 可见性由调用方先按当前客户价重新校验，本方法只管数量落库。
     */
    @Transactional(rollbackFor = Exception.class)
    public void setQuantity(RequestMallCustomer customer, Long skuId, BigDecimal quantity) {
        MallQuantityPolicy.require(requireVisible(customer, skuId), quantity);
        MallCartItemEntity existing = cartDao.selectByAccountAndSku(customer.getAccountId(), skuId);
        if (existing == null) {
            MallCartItemEntity row = new MallCartItemEntity();
            row.setAccountId(customer.getAccountId());
            row.setCustomerId(customer.getCustomerId());
            row.setSkuId(skuId);
            row.setQuantity(quantity);
            OffsetDateTime now = OffsetDateTime.now();
            row.setCreatedAt(now);
            row.setUpdatedAt(now);
            row.setCreatedBy(ScmOperator.current());
            row.setUpdatedBy(ScmOperator.current());
            cartDao.insert(row);
            return;
        }
        cartDao.updateQuantity(customer.getAccountId(), skuId, quantity, ScmOperator.current());
    }

    private MallProductVO requireVisible(RequestMallCustomer customer, Long skuId) {
        List<MallProductVO> visible = catalogService.visibleProducts(customer, List.of(skuId));
        if (visible.isEmpty()) {
            throw new ScmBusinessException(MallErrorCode.CART_SKU_NOT_VISIBLE);
        }
        return visible.get(0);
    }

    private MallCartVO assemble(RequestMallCustomer customer) {
        List<MallCartItemEntity> rows = cartDao.listByAccount(customer.getAccountId());
        MallCartVO result = new MallCartVO();
        result.setTotalCount(rows.size());
        if (rows.isEmpty()) {
            return result;
        }
        Map<Long,
                MallProductVO> products = catalogService
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
        item.setMinOrderQty(product.getMinOrderQty());
        item.setStepQty(product.getStepQty());
        item.setPrimaryImageUrl(product.getPrimaryImageUrl());
        item.setUnitPrice(product.getUnitPrice());
        item.setPriceStatus(product.getPriceStatus());
        item.setPriceSource(product.getPriceSource());
        if (product.getUnitPrice() == null) {
            item.setAvailability(MallCartAvailabilityEnum.NO_PRICE.name());
            item.setUnavailableReason(ScmUnpricedReasonEnum.NO_PRICE_SOURCE.name());
            return item;
        }
        if (!MallQuantityPolicy.accepts(product, row.getQuantity())) {
            item.setAvailability(MallCartAvailabilityEnum.QUANTITY_INVALID.name());
            item.setUnavailableReason(MallErrorCode.CART_QUANTITY_RULE_INVALID.name());
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
