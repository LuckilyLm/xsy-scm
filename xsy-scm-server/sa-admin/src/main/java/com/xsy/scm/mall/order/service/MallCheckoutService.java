package com.xsy.scm.mall.order.service;

import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.mall.account.constant.MallErrorCode;
import com.xsy.scm.mall.account.domain.vo.RequestMallCustomer;
import com.xsy.scm.mall.address.dao.MallCustomerAddressDao;
import com.xsy.scm.mall.address.domain.vo.MallAddressVO;
import com.xsy.scm.mall.cart.constant.MallCartAvailabilityEnum;
import com.xsy.scm.mall.cart.dao.MallCartDao;
import com.xsy.scm.mall.catalog.domain.vo.MallProductVO;
import com.xsy.scm.mall.catalog.service.MallCatalogService;
import com.xsy.scm.mall.order.domain.form.MallCheckoutItemForm;
import com.xsy.scm.mall.order.domain.form.MallCheckoutPreviewForm;
import com.xsy.scm.mall.order.domain.form.MallOrderSubmitForm;
import com.xsy.scm.mall.order.domain.vo.MallCheckoutItemVO;
import com.xsy.scm.mall.order.domain.vo.MallCheckoutPreviewVO;
import com.xsy.scm.mall.order.domain.vo.MallCheckoutPromotionVO;
import com.xsy.scm.mall.order.domain.vo.MallOrderSubmitVO;
import com.xsy.scm.order.constant.ScmOrderSourceEnum;
import com.xsy.scm.order.domain.dto.MallOrderCreationResult;
import com.xsy.scm.order.domain.form.OrderAddressForm;
import com.xsy.scm.order.domain.form.SalesOrderAddForm;
import com.xsy.scm.order.domain.form.SalesOrderItemForm;
import com.xsy.scm.order.domain.vo.SalesOrderDetailVO;
import com.xsy.scm.order.service.SalesOrderService;
import com.xsy.scm.pricing.constant.ScmUnavailableReasonEnum;
import com.xsy.scm.pricing.constant.ScmUnpricedReasonEnum;
import com.xsy.scm.promotion.domain.form.PromotionDiscountPreviewForm;
import com.xsy.scm.promotion.domain.vo.PromotionDiscountVO;
import com.xsy.scm.promotion.service.PromotionDiscountService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 商城结算与下单。
 *
 * <p>
 * 不建立第二套商品、库存、订单或财务逻辑：可见性与客户价复用目录服务，下单复用正式销售订单服务并标记 {@code MALL} 来源。
 * 客户身份一律取自登录会话，请求体里的商品与地址都必须属于当前客户；价格在提交时由服务端重算，与预览指纹不一致即拒绝。
 */
@Service
@RequiredArgsConstructor
public class MallCheckoutService {
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final int SCALE = 4;

    private final MallCatalogService catalogService;
    private final MallCustomerAddressDao addressDao;
    private final MallCartDao cartDao;
    private final SalesOrderService salesOrderService;
    private final PromotionDiscountService promotionDiscountService;
    private final MallSettlementService settlementService;

    @Transactional(readOnly = true)
    public MallCheckoutPreviewVO preview(RequestMallCustomer customer, MallCheckoutPreviewForm form) {
        Map<Long, BigDecimal> quantities = normalize(form.getItems());
        Map<Long, MallProductVO> products = resolveProducts(customer, quantities.keySet());

        MallCheckoutPreviewVO result = new MallCheckoutPreviewVO();
        BigDecimal estimated = BigDecimal.ZERO.setScale(SCALE);
        List<Long> unavailable = new ArrayList<>();
        int available = 0;
        for (Map.Entry<Long, BigDecimal> entry : quantities.entrySet()) {
            MallCheckoutItemVO item = toItem(entry.getKey(), entry.getValue(), products.get(entry.getKey()));
            result.getItems().add(item);
            if (MallCartAvailabilityEnum.AVAILABLE.name().equals(item.getAvailability())) {
                available++;
                estimated = estimated.add(item.getLineAmount());
            } else {
                unavailable.add(entry.getKey());
            }
        }
        result.setEstimatedAmount(estimated);
        result.setAvailableCount(available);
        result.setTotalCount(quantities.size());
        result.setUnavailableSkuIds(unavailable);
        if (form.getAddressId() != null) {
            result.setAddress(addressDao.detailById(customer.getCustomerId(), form.getAddressId()));
        }
        result.setPriceFingerprint(fingerprint(quantities, products));
        // 优惠试算：预览不占用券（ADR-009）。券不可用或门槛未达不拖垮整个预览——
        // 商品与金额照常返回，优惠错误单独提示，由客户换券或放弃用券。
        MallCheckoutPromotionVO promotion = null;
        try {
            promotion = previewPromotion(customer.getCustomerId(), form.getCouponInstanceId(), quantities, products);
            result.setPromotion(promotion);
        } catch (ScmBusinessException exception) {
            result.setPromotionError(exception.getMessage());
        }
        // 结算方式可用性按「优惠后应付」计算：余额够不够付、账期占多少额度，看的都是实际要付的钱而不是原价。
        BigDecimal payable = promotion == null ? estimated : promotion.getPayableAmount();
        result.setSettlementOptions(settlementService.options(customer, payable));
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public MallOrderSubmitVO submit(RequestMallCustomer customer, MallOrderSubmitForm form, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ScmBusinessException(MallErrorCode.CHECKOUT_IDEMPOTENCY_REQUIRED);
        }
        Map<Long, BigDecimal> quantities = normalize(form.getItems());
        // 幂等指纹只绑稳定的客户请求（客户 / 地址 ID / 备注 / 期望配送时间 / 预选券 / 规格与数量），不含地址内容快照：
        // 地址会被同一客户的其他采购账号编辑，用快照算指纹会让「第一次已成功、重试时地址已改」认不出原订单。
        // 它只取请求里的地址 ID，因此不需要先把地址读出来。
        Map<String, Object> idempotencyRequest = idempotencyRequest(customer, form, quantities);
        // 幂等预检必须排在所有「会被后续操作改变的前置校验」之前：
        // ① 排在价格校验前——第一次已下单成功但响应丢失时，重试遇变价会被误报成「价格已变化」；
        // ② 排在读地址前——重试期间地址可能已被同一客户的其他采购账号删除，
        // 先读地址会直接抛「地址不存在」，把一笔已经成功的订单藏起来。
        // 只有真正的新请求才继续校验地址与价格。
        Optional<MallOrderCreationResult> replayed = salesOrderService.replayMallOrder(idempotencyRequest,
                idempotencyKey);
        if (replayed.isPresent()) {
            return toSubmitVO(replayed.get().order());
        }
        if (form.getSettlementMethod() == null || form.getSettlementMethod().isBlank()) {
            throw new ScmBusinessException(MallErrorCode.CHECKOUT_SETTLEMENT_METHOD_REQUIRED);
        }
        MallAddressVO address = addressDao.detailById(customer.getCustomerId(), form.getAddressId());
        if (address == null) {
            throw new ScmBusinessException(MallErrorCode.ADDRESS_NOT_FOUND);
        }
        SalesOrderAddForm orderForm = orderForm(customer, form, address, quantities);
        // 只有新请求才按当前价格严格校验
        Map<Long, MallProductVO> products = resolveProducts(customer, quantities.keySet());
        for (Long skuId : quantities.keySet()) {
            MallProductVO product = products.get(skuId);
            if (product == null || product.getUnitPrice() == null) {
                throw new ScmBusinessException(MallErrorCode.CHECKOUT_ITEM_UNAVAILABLE);
            }
        }
        // 价格指纹不一致说明预览后价格变了，不能按旧价静默下单
        if (!fingerprint(quantities, products).equals(form.getPriceFingerprint())) {
            throw new ScmBusinessException(MallErrorCode.CHECKOUT_PRICE_CHANGED);
        }
        // 预选券在提交时按当前状态完整试算一次（归属 / AVAILABLE / 模板生效 / 门槛）：
        // 让不成立的券在下单前失败，而不是把一笔确认时必然冻结失败的单塞进后台。
        // 试算不锁券；全标品自动确认仍会在冻结时按最新券状态重新校验。
        MallCheckoutPromotionVO promotion = null;
        if (form.getCouponInstanceId() != null) {
            promotion = previewPromotion(customer.getCustomerId(), form.getCouponInstanceId(), quantities, products);
        }
        // 结算方式：用与预览同一口径的应付金额重新判定可用性，避免客户端拿旧预览的结论下单。
        BigDecimal gross = estimatedAmount(quantities, products);
        BigDecimal payable = promotion == null ? gross : promotion.getPayableAmount();
        boolean enabled = settlementService.options(customer, payable).stream()
                .anyMatch(option -> form.getSettlementMethod().equals(option.getMethod())
                        && Boolean.TRUE.equals(option.getEnabled()));
        if (!enabled) {
            throw new ScmBusinessException(MallErrorCode.CHECKOUT_SETTLEMENT_METHOD_UNAVAILABLE);
        }

        var creation = salesOrderService.createFromMall(orderForm, idempotencyRequest, idempotencyKey);
        if (!creation.replayed()) {
            requireMatchingLockedPrices(creation.order(), quantities, products);
            cartDao.removeBySkus(customer.getAccountId(), new ArrayList<>(quantities.keySet()), ScmOperator.current());
        }
        return toSubmitVO(creation.order());
    }

    private static void requireMatchingLockedPrices(SalesOrderDetailVO order, Map<Long, BigDecimal> quantities,
            Map<Long, MallProductVO> products) {
        // 正式订单提交会重新解析价格；必须在同一事务提交前核对最终快照，覆盖变价和协议到期窗口。
        if (order.getItems() == null || order.getItems().size() != quantities.size()) {
            throw new ScmBusinessException(MallErrorCode.CHECKOUT_PRICE_CHANGED);
        }
        var matchedSkuIds = new HashSet<Long>();
        for (var item : order.getItems()) {
            BigDecimal quantity = quantities.get(item.getSkuId());
            MallProductVO product = products.get(item.getSkuId());
            if (!matchedSkuIds.add(item.getSkuId()) || quantity == null || product == null
                    || product.getUnitPrice() == null || item.getOrderedQuantity() == null
                    || item.getLockedUnitPrice() == null || quantity.compareTo(item.getOrderedQuantity()) != 0
                    || product.getUnitPrice().compareTo(item.getLockedUnitPrice()) != 0) {
                throw new ScmBusinessException(MallErrorCode.CHECKOUT_PRICE_CHANGED);
            }
        }
    }

    /**
     * 结算优惠试算：只取可结算行（与预估金额同一口径），行 id 用顺序号占位—— 预览阶段没有订单行，分摊结果只汇总展示、不回映射到具体行。
     */
    private MallCheckoutPromotionVO previewPromotion(Long customerId, Long couponInstanceId,
            Map<Long, BigDecimal> quantities, Map<Long, MallProductVO> products) {
        PromotionDiscountPreviewForm form = new PromotionDiscountPreviewForm();
        form.setCustomerId(customerId);
        form.setCouponInstanceId(couponInstanceId);
        List<PromotionDiscountPreviewForm.Line> lines = new ArrayList<>();
        long sequence = 0;
        for (Map.Entry<Long, BigDecimal> entry : quantities.entrySet()) {
            MallProductVO product = products.get(entry.getKey());
            if (product == null || product.getUnitPrice() == null) {
                continue;
            }
            PromotionDiscountPreviewForm.Line line = new PromotionDiscountPreviewForm.Line();
            line.setOrderItemId(++sequence);
            line.setSkuId(entry.getKey());
            line.setQuantity(entry.getValue());
            line.setBaseAmount(product.getUnitPrice().multiply(entry.getValue()).setScale(SCALE, RoundingMode.HALF_UP));
            lines.add(line);
        }
        if (lines.isEmpty()) {
            return null;
        }
        form.setLines(lines);
        PromotionDiscountVO computed = promotionDiscountService.preview(form);
        MallCheckoutPromotionVO promotion = new MallCheckoutPromotionVO();
        promotion.setSpecialDiscount(computed.getSpecialDiscount());
        promotion.setActivityDiscount(computed.getActivityDiscount());
        promotion.setCouponDiscount(computed.getCouponDiscount());
        promotion.setDiscountAmount(computed.getDiscountAmount());
        promotion.setPayableAmount(computed.getFinalAmount());
        promotion.setCouponInstanceId(computed.getCouponInstanceId());
        promotion.setCouponName(computed.getCouponName());
        promotion.setAppliedActivityNames(computed.getAppliedActivities().stream()
                .map(PromotionDiscountVO.AppliedActivityVO::getActivityName).toList());
        promotion.setGiftDescriptions(computed.getGifts().stream().map(gift -> gift.getProductName() + " ×"
                + gift.getQuantity().toPlainString() + " " + (gift.getSaleUnit() == null ? "" : gift.getSaleUnit()))
                .toList());
        return promotion;
    }

    private SalesOrderAddForm orderForm(RequestMallCustomer customer, MallOrderSubmitForm form, MallAddressVO address,
            Map<Long, BigDecimal> quantities) {
        SalesOrderAddForm orderForm = new SalesOrderAddForm();
        // 客户只从登录身份取，不采信请求体
        orderForm.setCustomerId(customer.getCustomerId());
        orderForm.setOrderSource(ScmOrderSourceEnum.MALL.name());
        orderForm.setRemark(form.getRemark());
        orderForm.setExpectDeliveryTime(parseDelivery(form.getExpectDeliveryDate()));
        OrderAddressForm orderAddress = new OrderAddressForm();
        orderAddress.setReceiverName(address.getReceiverName());
        orderAddress.setReceiverPhone(address.getReceiverPhone());
        orderAddress.setAddress(fullAddress(address));
        orderForm.setAddress(orderAddress);
        List<SalesOrderItemForm> items = new ArrayList<>();
        int sortOrder = 0;
        for (Map.Entry<Long, BigDecimal> entry : quantities.entrySet()) {
            SalesOrderItemForm item = new SalesOrderItemForm();
            item.setSkuId(entry.getKey());
            item.setOrderedQuantity(entry.getValue().toPlainString());
            // 商城不提供改价入口，价格一律由订单服务在提交时重新解析
            item.setManualPriceOverride(false);
            item.setSortOrder(sortOrder++);
            items.add(item);
        }
        orderForm.setItems(items);
        // 客户预选券随订单持久化：确认命令默认带入它完成冻结（操作员可改可去），后台订单不经过这里。
        orderForm.setMallCouponInstanceId(form.getCouponInstanceId());
        // 结算方式同为「客户约定」：只记录选择，实际资金动作由余额 / 授信 / 收款各自领域在既有节点驱动。
        orderForm.setSettlementMethod(form.getSettlementMethod());
        return orderForm;
    }

    /** 可结算行的预估金额合计（与预览 {@code estimatedAmount} 同一口径：只累计有客户价的行）。 */
    private static BigDecimal estimatedAmount(Map<Long, BigDecimal> quantities, Map<Long, MallProductVO> products) {
        BigDecimal total = BigDecimal.ZERO.setScale(SCALE);
        for (Map.Entry<Long, BigDecimal> entry : quantities.entrySet()) {
            MallProductVO product = products.get(entry.getKey());
            if (product == null || product.getUnitPrice() == null) {
                continue;
            }
            total = total.add(product.getUnitPrice().multiply(entry.getValue()).setScale(SCALE, RoundingMode.HALF_UP));
        }
        return total;
    }

    private Map<Long, MallProductVO> resolveProducts(RequestMallCustomer customer, Iterable<Long> skuIds) {
        List<Long> ids = new ArrayList<>();
        skuIds.forEach(ids::add);
        return catalogService.visibleProducts(customer, ids).stream().collect(Collectors.toMap(MallProductVO::getSkuId,
                Function.identity(), (left, right) -> left, LinkedHashMap::new));
    }

    /** 合并重复规格并校验数量；保持提交顺序 */
    private static Map<Long, BigDecimal> normalize(List<MallCheckoutItemForm> items) {
        Map<Long, BigDecimal> quantities = new LinkedHashMap<>();
        for (MallCheckoutItemForm item : items) {
            BigDecimal quantity;
            try {
                quantity = new BigDecimal(item.getQuantity()).setScale(SCALE, RoundingMode.UNNECESSARY);
            } catch (ArithmeticException | NumberFormatException exception) {
                throw new ScmBusinessException(MallErrorCode.CART_QUANTITY_INVALID);
            }
            if (quantity.signum() <= 0) {
                throw new ScmBusinessException(MallErrorCode.CART_QUANTITY_INVALID);
            }
            quantities.merge(item.getSkuId(), quantity, BigDecimal::add);
        }
        if (quantities.isEmpty()) {
            throw new ScmBusinessException(MallErrorCode.CHECKOUT_EMPTY);
        }
        return quantities;
    }

    private static MallCheckoutItemVO toItem(Long skuId, BigDecimal quantity, MallProductVO product) {
        MallCheckoutItemVO item = new MallCheckoutItemVO();
        item.setSkuId(skuId);
        item.setQuantity(quantity);
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
        item.setNonStandard(product.getNonStandard());
        item.setUnitPrice(product.getUnitPrice());
        item.setPriceStatus(product.getPriceStatus());
        item.setPriceSource(product.getPriceSource());
        if (product.getUnitPrice() == null) {
            item.setAvailability(MallCartAvailabilityEnum.NO_PRICE.name());
            item.setUnavailableReason(ScmUnpricedReasonEnum.NO_PRICE_SOURCE.name());
            return item;
        }
        item.setAvailability(MallCartAvailabilityEnum.AVAILABLE.name());
        item.setLineAmount(product.getUnitPrice().multiply(quantity).setScale(SCALE, RoundingMode.HALF_UP));
        return item;
    }

    /**
     * 商城下单的幂等请求指纹：只含客户请求里稳定的字段。
     *
     * <p>
     * 不能用 {@code SalesOrderAddForm} 本身：它带收货地址内容快照，而地址会被同一客户的其他采购账号编辑； 同一幂等键重试时服务端重新读到的地址已经变了，请求哈希跟着变，第一次成功的订单就回放不出来。
     * 这里改用地址 ID（稳定）代表收货地址，地址内容仍会在建单时冻结进订单快照。
     *
     * <p>
     * 规格按 SKU 排序后再入指纹，使「同一批商品、提交顺序不同」视为同一请求； 用 {@link LinkedHashMap} 而非 {@code Map.of}：备注与期望配送日期可以为空，{@code Map.of} 不接受
     * null。
     */
    private static Map<String, Object> idempotencyRequest(RequestMallCustomer customer, MallOrderSubmitForm form,
            Map<Long, BigDecimal> quantities) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("customerId", customer.getCustomerId());
        request.put("addressId", form.getAddressId());
        request.put("remark", form.getRemark());
        request.put("expectDeliveryDate", form.getExpectDeliveryDate());
        request.put("couponInstanceId", form.getCouponInstanceId());
        request.put("settlementMethod", form.getSettlementMethod());
        request.put("items", quantities.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(entry -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("skuId", entry.getKey());
            item.put("quantity", entry.getValue().toPlainString());
            return item;
        }).toList());
        return request;
    }

    /**
     * 价格指纹：按 SKU 排序的 {@code skuId:quantity:unitPrice} 摘要。 只用于判断「预览到提交之间价格或数量是否变了」，不是安全边界。
     */
    private static String fingerprint(Map<Long, BigDecimal> quantities, Map<Long, MallProductVO> products) {
        String canonical = quantities.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(entry -> {
            MallProductVO product = products.get(entry.getKey());
            String price = product == null || product.getUnitPrice() == null
                    ? "null"
                    : product.getUnitPrice().toPlainString();
            return entry.getKey() + ":" + entry.getValue().toPlainString() + ":" + price;
        }).collect(Collectors.joining(";"));
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }

    private static OffsetDateTime parseDelivery(String date) {
        if (date == null || date.isBlank()) {
            return null;
        }
        return LocalDate.parse(date).atStartOfDay(SHANGHAI).toOffsetDateTime();
    }

    private static String fullAddress(MallAddressVO address) {
        return java.util.stream.Stream
                .of(address.getProvinceName(), address.getCityName(), address.getDistrictName(),
                        address.getDetailAddress())
                .filter(part -> part != null && !part.isBlank()).collect(Collectors.joining(" "));
    }

    private static MallOrderSubmitVO toSubmitVO(SalesOrderDetailVO order) {
        MallOrderSubmitVO result = new MallOrderSubmitVO();
        result.setOrderId(order.getOrderId());
        result.setOrderNo(order.getOrderNo());
        result.setStatus(order.getStatus());
        result.setOrderSource(order.getOrderSource());
        result.setOrderedTotalAmount(order.getOrderedTotalAmount());
        result.setCreatedAt(order.getCreatedAt());
        return result;
    }
}
