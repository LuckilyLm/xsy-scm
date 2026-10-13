package com.xsy.scm.mall.order.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.delivery.constant.ScmDeliveryFulfillmentStatusEnum;
import com.xsy.scm.delivery.dao.DeliveryRouteOrderDao;
import com.xsy.scm.delivery.domain.entity.DeliveryRouteOrderEntity;
import com.xsy.scm.delivery.domain.vo.DeliveryOrderProgressVO;
import com.xsy.scm.mall.account.constant.MallErrorCode;
import com.xsy.scm.mall.account.domain.vo.RequestMallCustomer;
import com.xsy.scm.mall.catalog.dao.MallCatalogDao;
import com.xsy.scm.mall.order.constant.MallOrderDisplayStatusEnum;
import com.xsy.scm.mall.order.domain.form.MallOrderQueryForm;
import com.xsy.scm.mall.order.domain.vo.MallOrderDeliveryVO;
import com.xsy.scm.mall.order.domain.vo.MallOrderDiscountVO;
import com.xsy.scm.mall.order.domain.vo.MallOrderItemVO;
import com.xsy.scm.mall.order.domain.vo.MallOrderProgressNodeVO;
import com.xsy.scm.mall.order.domain.vo.MallOrderTimelineVO;
import com.xsy.scm.mall.order.domain.vo.MallOrderVO;
import com.xsy.scm.order.constant.ScmOrderOperationTypeEnum;
import com.xsy.scm.order.constant.ScmOrderStatusEnum;
import com.xsy.scm.order.dao.OrderAddressSnapshotDao;
import com.xsy.scm.order.dao.OrderOperationLogDao;
import com.xsy.scm.order.dao.SalesOrderDao;
import com.xsy.scm.order.dao.SalesOrderItemDao;
import com.xsy.scm.order.domain.entity.OrderOperationLogEntity;
import com.xsy.scm.order.domain.entity.SalesOrderEntity;
import com.xsy.scm.order.domain.entity.SalesOrderItemEntity;
import com.xsy.scm.promotion.domain.vo.OrderDiscountVO;
import com.xsy.scm.promotion.domain.vo.PromotionDiscountVO;
import com.xsy.scm.promotion.service.PromotionDiscountService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.enumeration.UserTypeEnum;
import net.lab1024.sa.base.module.support.file.service.FileService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 商城订单只读查询。
 *
 * <p>
 * 订单事实只来自正式销售订单，履约进度只来自配送域的生效线路订单；本服务不写任何表， 也不做状态推导之外的业务判断。归属一律由 {@code customer_id + order_source='MALL'} 收窄，客户端传入的 id
 * 不被信任。
 */
@Service
@RequiredArgsConstructor
public class MallOrderQueryService {
    /** 列表卡片最多展示的缩略图数量，与设计图一致 */
    private static final int THUMBNAIL_LIMIT = 3;
    private static final String MALL_OPERATOR_PREFIX = UserTypeEnum.MALL_CUSTOMER.getValue() + ":";
    private static final String NON_STANDARD = "NON_STANDARD";

    /**
     * 客户可见的订单操作类型白名单。
     *
     * <p>
     * 时间线直接回读 {@code xsy_order_operation_log}，但那张表是<b>内部审计日志</b>：
     * {@code CREDIT_OVERRIDE}（超额授信放行）带的是内部审批原因，{@code UPDATE} 带改价/改量原因， {@code RESERVE_STOCK} 是仓库内部动作 ——
     * 这些都不是给客户看的。后台审计记录保持完整， 商城侧只收窄展示，不删除任何原始记录。
     *
     * <p>
     * 售后类操作（{@code RETURN} / {@code REFUND}）在售后功能落地、各自的原因口径定下来之后再逐个加入， 不预先放行。
     */
    private static final Set<String> CUSTOMER_VISIBLE_OPERATIONS = Set.of(ScmOrderOperationTypeEnum.CREATE.name(),
            ScmOrderOperationTypeEnum.SUBMIT.name(), ScmOrderOperationTypeEnum.ACTUAL_QUANTITY.name(),
            ScmOrderOperationTypeEnum.CONFIRM.name(), ScmOrderOperationTypeEnum.CANCEL.name());

    private final SalesOrderDao salesOrderDao;
    private final SalesOrderItemDao salesOrderItemDao;
    private final OrderAddressSnapshotDao addressSnapshotDao;
    private final OrderOperationLogDao orderOperationLogDao;
    private final DeliveryRouteOrderDao deliveryRouteOrderDao;
    private final MallCatalogDao mallCatalogDao;
    private final PromotionDiscountService promotionDiscountService;
    private final FileService fileService;

    @Transactional(readOnly = true)
    public PageResult<MallOrderVO> list(RequestMallCustomer customer, MallOrderQueryForm form) {
        Page<SalesOrderEntity> page = new Page<>(form.getPageNum(), form.getPageSize(), true);
        List<SalesOrderEntity> orders = salesOrderDao.listByCustomer(page, customer.getCustomerId(),
                form.getDisplayStatus());
        List<Long> orderIds = orders.stream().map(SalesOrderEntity::getId).toList();
        Map<Long,
                List<SalesOrderItemEntity>> itemsByOrder = orderIds.isEmpty()
                        ? Map.of()
                        : salesOrderItemDao.listByOrderIds(orderIds).stream()
                                .collect(Collectors.groupingBy(SalesOrderItemEntity::getOrderId));
        Map<Long, DeliveryRouteOrderEntity> fulfillmentByOrder = fulfillmentByOrder(orderIds);
        Map<Long, String> imageUrls = imageUrls(itemsByOrder.values().stream().flatMap(List::stream).toList());

        List<MallOrderVO> rows = orders.stream().map(order -> summary(order,
                itemsByOrder.getOrDefault(order.getId(), List.of()), fulfillmentByOrder.get(order.getId()), imageUrls))
                .toList();
        PageResult<MallOrderVO> result = new PageResult<>();
        result.setPageNum(page.getCurrent());
        result.setPageSize(page.getSize());
        result.setTotal(page.getTotal());
        result.setPages(page.getPages());
        result.setList(rows);
        result.setEmptyFlag(rows.isEmpty());
        return result;
    }

    @Transactional(readOnly = true)
    public MallOrderVO detail(RequestMallCustomer customer, Long orderId) {
        SalesOrderEntity order = requireOwnedOrder(customer, orderId);
        List<SalesOrderItemEntity> items = salesOrderItemDao.list(orderId);
        Map<Long, String> imageUrls = imageUrls(items);
        MallOrderVO result = summary(order, items, fulfillmentByOrder(List.of(orderId)).get(orderId), imageUrls);
        result.setItems(items.stream().sorted(
                Comparator.comparing(SalesOrderItemEntity::getSortOrder).thenComparing(SalesOrderItemEntity::getId))
                .map(row -> item(row, imageUrls)).toList());
        addressSnapshotDao.list(orderId).stream().findFirst().ifPresent(address -> {
            result.setReceiverName(address.getReceiverName());
            result.setReceiverPhone(address.getReceiverPhone());
            result.setAddress(address.getAddress());
        });
        // 处理记录按客户可见白名单收窄：内部审计日志（授信放行、改价原因、库存预留）不进客户时间线
        result.setTimeline(orderOperationLogDao.listByOrderId(orderId).stream().filter(
                log -> log.getOperationType() != null && CUSTOMER_VISIBLE_OPERATIONS.contains(log.getOperationType()))
                .map(this::timeline).toList());
        // 配送详情与进度只读配送域的正式事实：线路 / 司机 / 车辆 / 发车 / 完成 + 订单级签收。
        // 线路被移出或逻辑删除时不返回投影，页面按「暂无配送信息」处理。
        DeliveryOrderProgressVO deliveryProgress = deliveryRouteOrderDao.selectProgressByOrderIds(List.of(orderId))
                .stream().findFirst().orElse(null);
        result.setDelivery(delivery(deliveryProgress));
        result.setProgress(progressNodes(order, deliveryProgress));
        result.setDiscount(discount(orderId));
        return result;
    }

    private MallOrderDiscountVO discount(Long orderId) {
        OrderDiscountVO frozen = promotionDiscountService.getByOrder(orderId);
        List<PromotionDiscountVO.GiftEntitlementVO> gifts = promotionDiscountService.listGifts(orderId);
        if (frozen == null && gifts.isEmpty()) {
            return null;
        }
        MallOrderDiscountVO result = new MallOrderDiscountVO();
        result.setDiscountAmount(frozen == null ? BigDecimal.ZERO.setScale(4) : frozen.getDiscountAmount());
        if (frozen != null) {
            Map<String, Object> coupon = frozen.getCouponSnapshot();
            if (coupon != null && coupon.get("couponName") instanceof String name) {
                result.setCouponName(name);
            }
            Map<String, Object> activities = frozen.getActivitySnapshot();
            if (activities != null && activities.get("applied") instanceof List<?> applied) {
                result.setActivityNames(applied.stream().filter(Map.class::isInstance).map(Map.class::cast)
                        .map(row -> row.get("activityName")).filter(String.class::isInstance)
                        .map(String.class::cast).toList());
            }
        }
        result.setGiftDescriptions(gifts.stream()
                .map(gift -> gift.getProductName() + " ×" + gift.getQuantity().toPlainString() + " "
                        + (gift.getSaleUnit() == null ? "" : gift.getSaleUnit()))
                .toList());
        return result;
    }

    /** 按归属取订单；取不到即 404。取消与再来一单复用同一条归属判定。 */
    @Transactional(readOnly = true)
    public SalesOrderEntity requireOwnedOrder(RequestMallCustomer customer, Long orderId) {
        SalesOrderEntity order = salesOrderDao.selectByIdAndCustomer(orderId, customer.getCustomerId());
        if (order == null) {
            throw new ScmBusinessException(MallErrorCode.ORDER_NOT_FOUND);
        }
        return order;
    }

    /** 该订单当前生效的履约状态；未排车返回 null。 */
    @Transactional(readOnly = true)
    public String activeFulfillmentStatus(Long orderId) {
        DeliveryRouteOrderEntity fulfillment = fulfillmentByOrder(List.of(orderId)).get(orderId);
        return fulfillment == null ? null : fulfillment.getFulfillmentStatus();
    }

    /** 取生效中的线路订单整行：状态、签收时刻与签收备注都来自同一条正式事实，不另外存快照。 */
    private Map<Long, DeliveryRouteOrderEntity> fulfillmentByOrder(List<Long> orderIds) {
        if (orderIds.isEmpty()) {
            return Map.of();
        }
        return deliveryRouteOrderDao.selectActiveByOrderIds(orderIds).stream().collect(
                Collectors.toMap(DeliveryRouteOrderEntity::getOrderId, Function.identity(), (first, ignored) -> first));
    }

    /** 订单行不存图，展示时按 SKU 回读主图；取不到图的 SKU 不进 map，由客户端兜底。 */
    private Map<Long, String> imageUrls(List<SalesOrderItemEntity> items) {
        return skuImageUrls(
                items.stream().map(SalesOrderItemEntity::getSkuId).filter(Objects::nonNull).distinct().toList());
    }

    /** 按 SKU 批量取主图 URL；取不到图的 SKU 不进 map。订单与售后共用同一套图片解析。 */
    public Map<Long, String> skuImageUrls(List<Long> skuIds) {
        if (skuIds.isEmpty()) {
            return Map.of();
        }
        Map<Long,
                String> fileKeys = mallCatalogDao.primaryImagesBySkuIds(skuIds).stream()
                        .filter(row -> row.getPrimaryImageKey() != null).collect(Collectors.toMap(row -> row.getSkuId(),
                                row -> row.getPrimaryImageKey(), (first, ignored) -> first));
        if (fileKeys.isEmpty()) {
            return Map.of();
        }
        Map<String, String> urls = new HashMap<>();
        fileService.getFileList(fileKeys.values().stream().distinct().toList())
                .forEach(file -> urls.put(file.getFileKey(), file.getFileUrl()));
        Map<Long, String> result = new HashMap<>();
        fileKeys.forEach((skuId, key) -> {
            String url = urls.get(key);
            if (url != null) {
                result.put(skuId, url);
            }
        });
        return result;
    }

    private MallOrderVO summary(SalesOrderEntity order, List<SalesOrderItemEntity> items,
            DeliveryRouteOrderEntity fulfillment, Map<Long, String> imageUrls) {
        String fulfillmentStatus = fulfillment == null ? null : fulfillment.getFulfillmentStatus();
        MallOrderDisplayStatusEnum displayStatus = MallOrderDisplayStatusEnum.derive(order.getStatus(),
                fulfillmentStatus);
        MallOrderVO result = new MallOrderVO();
        result.setOrderId(order.getId());
        result.setOrderNo(order.getOrderNo());
        result.setStatus(order.getStatus());
        result.setDisplayStatus(displayStatus.name());
        result.setFulfillmentStatus(fulfillmentStatus);
        result.setCancelable(displayStatus.cancelable());
        result.setOrderedTotalAmount(order.getOrderedTotalAmount());
        result.setSettlementTotalAmount(order.getSettlementTotalAmount());
        result.setExpectDeliveryTime(order.getExpectDeliveryTime());
        result.setRemark(order.getRemark());
        result.setCreatedAt(order.getCreatedAt());
        result.setSubmittedAt(order.getSubmittedAt());
        result.setConfirmedAt(order.getConfirmedAt());
        result.setCancelledAt(order.getCancelledAt());
        result.setCancelReason(order.getCancelReason());
        // 签收时刻与备注直接回读配送事实，不在订单侧另存快照，避免两处口径漂移
        if (fulfillment != null) {
            result.setSignedAt(fulfillment.getSignedAt());
            result.setSignReason(fulfillment.getSignReason());
        }
        result.setItemCount(items.size());
        result.setItemThumbnails(items.stream().map(item -> imageUrls.get(item.getSkuId())).filter(Objects::nonNull)
                .limit(THUMBNAIL_LIMIT).toList());
        return result;
    }

    private MallOrderItemVO item(SalesOrderItemEntity row, Map<Long, String> imageUrls) {
        MallOrderItemVO result = new MallOrderItemVO();
        result.setItemId(row.getId());
        result.setSkuId(row.getSkuId());
        result.setProductName(row.getProductNameSnapshot());
        result.setSpecName(row.getSpecNameSnapshot());
        result.setSaleUnit(row.getSaleUnitSnapshot());
        result.setProductType(row.getProductTypeSnapshot());
        result.setNonStandard(NON_STANDARD.equals(row.getProductTypeSnapshot()));
        result.setPrimaryImageUrl(imageUrls.get(row.getSkuId()));
        result.setOrderedQuantity(row.getOrderedQuantity());
        result.setActualQuantity(row.getActualQuantity());
        result.setUnitPrice(row.getLockedUnitPrice());
        result.setOrderedLineAmount(row.getOrderedLineAmount());
        result.setSettlementLineAmount(row.getSettlementLineAmount());
        return result;
    }

    /**
     * 线路尚未排车（或线路已逻辑删除）时返回 {@code null}：客户看到「暂无配送信息」， 而不是拿半条线路编出配送详情。
     */
    private static MallOrderDeliveryVO delivery(DeliveryOrderProgressVO row) {
        if (row == null) {
            return null;
        }
        MallOrderDeliveryVO result = new MallOrderDeliveryVO();
        result.setRouteNo(row.getRouteNo());
        result.setRouteName(row.getRouteName());
        result.setDeliveryDate(row.getDeliveryDate());
        result.setWarehouseName(row.getWarehouseNameSnapshot());
        result.setDriverName(row.getDriverNameSnapshot());
        result.setDriverPhone(row.getDriverPhoneSnapshot());
        result.setVehicleNo(row.getVehicleNoSnapshot());
        result.setPlannedDepartureTime(row.getPlannedDepartureTime());
        result.setDispatchedAt(row.getDispatchedAt());
        result.setCompletedAt(row.getCompletedAt());
        return result;
    }

    /**
     * 配送进度节点：{@code done} 只按「对应正式事实是否存在」判定，不推导状态、不预测下一步。
     *
     * <p>
     * 已取消订单只保留「已提交 + 已取消」两格：取消之后不会再发车，继续列出后面几格会让客户以为还有后续。 异常签收（拒收/破损等）在最后一格显式写成「配送异常」并带上原因，不冒充正常签收。
     */
    private static List<MallOrderProgressNodeVO> progressNodes(SalesOrderEntity order, DeliveryOrderProgressVO row) {
        List<MallOrderProgressNodeVO> nodes = new ArrayList<>();
        nodes.add(node("SUBMITTED", "已提交订单", null,
                order.getSubmittedAt() == null ? order.getCreatedAt() : order.getSubmittedAt(), true));
        if (ScmOrderStatusEnum.CANCELLED.name().equals(order.getStatus())) {
            nodes.add(node("CANCELLED", "订单已取消", order.getCancelReason(), order.getCancelledAt(), true));
            return nodes;
        }
        nodes.add(node("CONFIRMED", "后台已接单", null, order.getConfirmedAt(), order.getConfirmedAt() != null));
        OffsetDateTime dispatchedAt = row == null ? null : row.getDispatchedAt();
        nodes.add(node("DISPATCHED", "已发车配送", null, dispatchedAt, dispatchedAt != null));
        OffsetDateTime signedAt = row == null ? null : row.getSignedAt();
        boolean abnormal = row != null
                && ScmDeliveryFulfillmentStatusEnum.EXCEPTION.name().equals(row.getFulfillmentStatus());
        nodes.add(node("SIGNED", abnormal ? "配送异常" : "已签收", row == null ? null : row.getSignReason(), signedAt,
                signedAt != null));
        return nodes;
    }

    private static MallOrderProgressNodeVO node(String code, String text, String detail, OffsetDateTime time,
            boolean done) {
        MallOrderProgressNodeVO result = new MallOrderProgressNodeVO();
        result.setCode(code);
        result.setText(text);
        result.setDetail(detail);
        result.setTime(time);
        result.setDone(done);
        return result;
    }

    private MallOrderTimelineVO timeline(OrderOperationLogEntity log) {
        MallOrderTimelineVO result = new MallOrderTimelineVO();
        result.setLogId(log.getId());
        result.setOperationType(log.getOperationType());
        result.setOperatorName(operatorLabel(log.getOperator()));
        result.setReason(log.getReason());
        result.setCreatedAt(log.getCreatedAt());
        return result;
    }

    /**
     * 客户侧只看「谁做的」这一层：本人操作显示「客户」，其余一律「平台」。
     *
     * <p>
     * 刻意不把内部员工姓名透给客户：对客户没有意义，也属于内部人员信息；后台仍按原样显示员工姓名。
     */
    private static String operatorLabel(String operator) {
        if (operator == null || operator.isBlank()) {
            return "系统";
        }
        return operator.startsWith(MALL_OPERATOR_PREFIX) ? "客户" : "平台";
    }
}
