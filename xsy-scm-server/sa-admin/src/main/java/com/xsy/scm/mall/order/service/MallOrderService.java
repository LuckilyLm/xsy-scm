package com.xsy.scm.mall.order.service;

import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.delivery.constant.ScmDeliveryFulfillmentStatusEnum;
import com.xsy.scm.mall.account.constant.MallErrorCode;
import com.xsy.scm.mall.account.domain.vo.RequestMallCustomer;
import com.xsy.scm.mall.cart.service.MallCartService;
import com.xsy.scm.mall.catalog.domain.vo.MallProductVO;
import com.xsy.scm.mall.catalog.service.MallCatalogService;
import com.xsy.scm.mall.catalog.service.MallQuantityPolicy;
import com.xsy.scm.mall.order.domain.form.MallOrderCancelForm;
import com.xsy.scm.mall.order.domain.vo.MallOrderVO;
import com.xsy.scm.mall.order.domain.vo.MallReorderVO;
import com.xsy.scm.order.constant.ScmOrderStatusEnum;
import com.xsy.scm.order.dao.SalesOrderItemDao;
import com.xsy.scm.order.domain.entity.SalesOrderEntity;
import com.xsy.scm.order.domain.entity.SalesOrderItemEntity;
import com.xsy.scm.order.domain.form.OrderCancelForm;
import com.xsy.scm.order.manager.OrderStateMachine;
import com.xsy.scm.order.service.SalesOrderService;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 商城订单的客户侧写操作：受控取消与再来一单。
 *
 * <p>
 * 两条路径都不新建订单状态机、不复制历史价格：取消走正式 {@link SalesOrderService} 的取消命令， 再来一单只把历史明细的数量写回购物车，价格留到结算时由服务端按当前客户价重新解析。
 */
@Service
@RequiredArgsConstructor
public class MallOrderService {
    private final MallOrderQueryService orderQueryService;
    private final SalesOrderService salesOrderService;
    private final SalesOrderItemDao salesOrderItemDao;
    private final MallCartService cartService;
    private final MallCatalogService catalogService;

    /**
     * 客户取消订单先校验订单归属；已经取消的订单继续交给正式订单服务处理幂等重试，避免重复点击被误判为失败。
     */
    @Transactional(rollbackFor = Exception.class)
    public MallOrderVO cancel(RequestMallCustomer customer, Long orderId, MallOrderCancelForm form,
        String idempotencyKey) {
        SalesOrderEntity order = orderQueryService.requireOwnedOrder(customer, orderId);
        if (!ScmOrderStatusEnum.CANCELLED.name().equals(order.getStatus())) {
            requireCancellable(order, orderId);
        }
        OrderCancelForm cancelForm = new OrderCancelForm();
        cancelForm.setOrderId(orderId);
        cancelForm.setVersion(order.getVersion());
        cancelForm.setReason(form.getReason());
        salesOrderService.cancelFromMall(cancelForm, idempotencyKey);
        return orderQueryService.detail(customer, orderId);
    }

    /**
     * 把「能不能取消」翻译成客户可读的原因；真正的限制在订单状态机。
     */
    private void requireCancellable(SalesOrderEntity order, Long orderId) {
        if (!OrderStateMachine.canTransition(order.getStatus(), ScmOrderStatusEnum.CANCELLED.name())) {
            throw new ScmBusinessException(MallErrorCode.ORDER_NOT_CANCELABLE);
        }
        String fulfillmentStatus = orderQueryService.activeFulfillmentStatus(orderId);
        if (fulfillmentStatus != null && !ScmDeliveryFulfillmentStatusEnum.PENDING.name().equals(fulfillmentStatus)) {
            throw new ScmBusinessException(MallErrorCode.ORDER_ALREADY_FULFILLING);
        }
    }

    /**
     * 再来一单：把历史明细按原数量写回服务端购物车。
     *
     * <p>
     * 逐条按当前可见性与客户价重新校验，不可见的进 {@code skipped} 并给出原因；数量取历史下单量且是覆盖而不是累加，
     * 重复点击得到同一结果。历史成交价一律不复制：真正下单时的价格由结算与订单服务按当时的客户价重新解析。
     */
    @Transactional(rollbackFor = Exception.class)
    public MallReorderVO reorder(RequestMallCustomer customer, Long orderId) {
        orderQueryService.requireOwnedOrder(customer, orderId);
        List<SalesOrderItemEntity> items = salesOrderItemDao.list(orderId);
        if (items.isEmpty()) {
            throw new ScmBusinessException(MallErrorCode.REORDER_NOTHING_ADDED);
        }
        List<Long> skuIds = items.stream().map(SalesOrderItemEntity::getSkuId).distinct().toList();
        Map<Long, MallProductVO> visible = catalogService.visibleProducts(customer, skuIds).stream()
            .collect(Collectors.toMap(MallProductVO::getSkuId, Function.identity(), (first, ignored) -> first));

        MallReorderVO result = new MallReorderVO();
        for (SalesOrderItemEntity item : items) {
            MallProductVO product = visible.get(item.getSkuId());
            if (product == null) {
                result.getSkipped().add(skipped(item, "商品已下架或对当前客户不可见"));
                continue;
            }
            if (product.getUnitPrice() == null) {
                result.getSkipped().add(skipped(item, "暂无客户价，请联系业务员询价"));
                continue;
            }
            if (!MallQuantityPolicy.accepts(product, item.getOrderedQuantity())) {
                result.getSkipped().add(skipped(item, "原购买数量不符合当前起订量和递增量"));
                continue;
            }
            cartService.setQuantity(customer, item.getSkuId(), item.getOrderedQuantity());
            result.setAddedCount(result.getAddedCount() + 1);
        }
        return result;
    }

    private static MallReorderVO.Skipped skipped(SalesOrderItemEntity item, String reason) {
        MallReorderVO.Skipped row = new MallReorderVO.Skipped();
        row.setSkuId(item.getSkuId());
        row.setProductName(item.getProductNameSnapshot());
        row.setReason(reason);
        return row;
    }
}
