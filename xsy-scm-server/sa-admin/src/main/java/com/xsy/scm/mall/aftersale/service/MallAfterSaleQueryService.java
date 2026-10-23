package com.xsy.scm.mall.aftersale.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.mall.account.constant.MallErrorCode;
import com.xsy.scm.mall.account.domain.vo.RequestMallCustomer;
import com.xsy.scm.mall.aftersale.constant.MallAfterSaleDisplayStatusEnum;
import com.xsy.scm.mall.aftersale.dao.MallAfterSaleFundingSourceDao;
import com.xsy.scm.mall.aftersale.domain.form.MallAfterSaleQueryForm;
import com.xsy.scm.mall.aftersale.domain.vo.MallAfterSaleItemVO;
import com.xsy.scm.mall.aftersale.domain.vo.MallAfterSaleFundingTimeVO;
import com.xsy.scm.mall.aftersale.domain.vo.MallAfterSaleRefundVO;
import com.xsy.scm.mall.aftersale.domain.vo.MallAfterSaleVO;
import com.xsy.scm.mall.order.constant.MallOrderProgressNodeEnum;
import com.xsy.scm.mall.order.domain.vo.MallOrderProgressNodeVO;
import com.xsy.scm.mall.order.service.MallOrderQueryService;
import com.xsy.scm.order.constant.ScmOrderReturnStatusEnum;
import com.xsy.scm.order.dao.OrderRefundDao;
import com.xsy.scm.order.dao.OrderRefundFundingSourceDao;
import com.xsy.scm.order.dao.OrderReturnDao;
import com.xsy.scm.order.dao.OrderReturnItemDao;
import com.xsy.scm.order.dao.SalesOrderDao;
import com.xsy.scm.order.dao.SalesOrderItemDao;
import com.xsy.scm.order.domain.entity.OrderRefundEntity;
import com.xsy.scm.order.domain.entity.OrderReturnEntity;
import com.xsy.scm.order.domain.entity.OrderReturnItemEntity;
import com.xsy.scm.order.domain.entity.SalesOrderEntity;
import com.xsy.scm.order.domain.entity.SalesOrderItemEntity;
import com.xsy.scm.order.manager.OrderAmountCalculator;
import com.xsy.scm.payment.constant.ScmPaymentMethodEnum;
import com.xsy.scm.payment.constant.ScmRefundFundingAllocationStatusEnum;
import com.xsy.scm.payment.dao.RefundFundingAllocationDao;
import com.xsy.scm.payment.domain.entity.RefundFundingAllocationEntity;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.domain.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 商城售后只读查询。
 *
 * <p>
 * 售后数据只来自正式退货单、退款单与资金分摊行；归属一律按退货单的 {@code customer_id} 与登录客户逐值比较， 本服务不写任何表。渠道失败码、内部失败原因等内部信息不下发客户。
 */
@Service
@RequiredArgsConstructor
public class MallAfterSaleQueryService {
    /**
     * 列表卡片最多展示的缩略图数量，与订单列表一致
     */
    private static final int THUMBNAIL_LIMIT = 3;

    private final OrderReturnDao orderReturnDao;
    private final OrderReturnItemDao orderReturnItemDao;
    private final OrderRefundDao orderRefundDao;
    private final OrderRefundFundingSourceDao orderRefundFundingSourceDao;
    private final SalesOrderDao salesOrderDao;
    private final SalesOrderItemDao salesOrderItemDao;
    private final RefundFundingAllocationDao refundFundingAllocationDao;
    private final MallAfterSaleFundingSourceDao fundingSourceDao;
    private final MallOrderQueryService orderQueryService;

    @Transactional(readOnly = true)
    public PageResult<MallAfterSaleVO> list(RequestMallCustomer customer, MallAfterSaleQueryForm form) {
        Page<OrderReturnEntity> page = new Page<>(form.getPageNum(), form.getPageSize(), true);
        List<OrderReturnEntity> returns = orderReturnDao.listByCustomer(page, customer.getCustomerId(),
            form.getOrderId());
        List<MallAfterSaleVO> rows = assemble(returns, false);
        PageResult<MallAfterSaleVO> result = new PageResult<>();
        result.setPageNum(page.getCurrent());
        result.setPageSize(page.getSize());
        result.setTotal(page.getTotal());
        result.setPages(page.getPages());
        result.setList(rows);
        result.setEmptyFlag(rows.isEmpty());
        return result;
    }

    /**
     * 按归属取退货单；取不到即 404。申请与撤销复用同一条归属判定。
     */
    @Transactional(readOnly = true)
    public OrderReturnEntity requireOwnedReturn(RequestMallCustomer customer, Long returnId) {
        OrderReturnEntity entity = orderReturnDao.selectById(returnId);
        if (entity == null || !customer.getCustomerId().equals(entity.getCustomerId())) {
            throw new ScmBusinessException(MallErrorCode.AFTERSALE_NOT_FOUND);
        }
        return entity;
    }

    @Transactional(readOnly = true)
    public MallAfterSaleVO detail(RequestMallCustomer customer, Long returnId) {
        return assemble(List.of(requireOwnedReturn(customer, returnId)), true).getFirst();
    }

    private List<MallAfterSaleVO> assemble(List<OrderReturnEntity> returns, boolean detail) {
        if (returns.isEmpty()) {
            return List.of();
        }
        List<Long> returnIds = returns.stream().map(OrderReturnEntity::getId).toList();
        Map<Long, List<OrderReturnItemEntity>> itemsByReturn = orderReturnItemDao.listByReturnIds(returnIds).stream()
            .collect(Collectors.groupingBy(OrderReturnItemEntity::getReturnId));
        List<Long> orderIds = returns.stream().map(OrderReturnEntity::getOrderId).distinct().toList();
        Map<Long, SalesOrderEntity> ordersById = salesOrderDao.selectBatchIds(orderIds).stream()
            .collect(Collectors.toMap(SalesOrderEntity::getId, Function.identity()));
        Map<Long, SalesOrderItemEntity> orderItemsById = salesOrderItemDao.listByOrderIds(orderIds).stream()
            .collect(Collectors.toMap(SalesOrderItemEntity::getId, Function.identity()));
        // 一张退货单至多一张退款单（批准时按净额生成一次）；取 id 最小的一条，不把异常数据放大成列表。
        Map<Long, OrderRefundEntity> refundsByReturn = orderRefundDao.listByReturnIds(returnIds).stream()
            .collect(Collectors.toMap(OrderRefundEntity::getReturnId, Function.identity(), (first, ignored) -> first));
        List<Long> refundIds = refundsByReturn.values().stream().map(OrderRefundEntity::getId).toList();
        Map<Long, OffsetDateTime> fundedAtByRefund = refundIds.isEmpty()
            ? Map.of()
            : fundingSourceDao.completedRefunds(refundIds).stream().collect(
                Collectors.toMap(MallAfterSaleFundingTimeVO::getRefundId, MallAfterSaleFundingTimeVO::getSucceededAt));
        Map<Long, String> imageUrls = orderQueryService.skuImageUrls(orderItemsById.values().stream()
            .map(SalesOrderItemEntity::getSkuId).filter(Objects::nonNull).distinct().toList());
        return returns.stream()
            .map(entity -> toVo(entity, itemsByReturn.getOrDefault(entity.getId(), List.of()),
                ordersById.get(entity.getOrderId()), orderItemsById, refundsByReturn.get(entity.getId()), imageUrls,
                fundedAtByRefund, detail))
            .toList();
    }

    private MallAfterSaleVO toVo(OrderReturnEntity entity, List<OrderReturnItemEntity> items, SalesOrderEntity order,
        Map<Long, SalesOrderItemEntity> orderItemsById, OrderRefundEntity refund, Map<Long, String> imageUrls,
        Map<Long, OffsetDateTime> fundedAtByRefund, boolean detail) {
        OffsetDateTime fundedAt = refund == null ? null : fundedAtByRefund.get(refund.getId());
        MallAfterSaleVO result = new MallAfterSaleVO();
        result.setReturnId(entity.getId());
        result.setReturnNo(entity.getReturnNo());
        result.setOrderId(entity.getOrderId());
        result.setOrderNo(order == null ? null : order.getOrderNo());
        result.setStatus(entity.getStatus());
        result.setDisplayStatus(
            MallAfterSaleDisplayStatusEnum.derive(entity.getStatus(), refund != null, fundedAt != null).name());
        result.setReason(entity.getReason());
        result.setDecisionReason(entity.getDecisionReason());
        result.setRequestedAmount(OrderAmountCalculator.orderAmount(items.stream()
            .map(item -> OrderAmountCalculator.lineAmount(item.getRequestedQuantity(), item.getLockedUnitPrice()))
            .toList()));
        result.setApprovedAmount(entity.getApprovedAmount());
        result.setCancelable(ScmOrderReturnStatusEnum.PENDING.name().equals(entity.getStatus()));
        result.setCreatedAt(entity.getCreatedAt());
        result.setApprovedAt(entity.getApprovedAt());
        result.setRejectedAt(entity.getRejectedAt());
        result.setCancelledAt(entity.getCancelledAt());
        result.setItemCount(items.size());
        result.setItemThumbnails(items.stream().map(item -> orderItemsById.get(item.getOrderItemId()))
            .filter(Objects::nonNull).map(orderItem -> imageUrls.get(orderItem.getSkuId())).filter(Objects::nonNull)
            .limit(THUMBNAIL_LIMIT).toList());
        if (detail) {
            result.setItems(items.stream()
                .map(item -> itemVo(item, orderItemsById.get(item.getOrderItemId()), imageUrls)).toList());
            result.setRefund(refundVo(refund, fundedAt));
            result.setProgress(progressNodes(entity, refund, fundedAt));
        }
        return result;
    }

    private MallAfterSaleItemVO itemVo(OrderReturnItemEntity item, SalesOrderItemEntity orderItem,
        Map<Long, String> imageUrls) {
        MallAfterSaleItemVO result = new MallAfterSaleItemVO();
        result.setReturnItemId(item.getId());
        result.setOrderItemId(item.getOrderItemId());
        result.setRequestedQuantity(item.getRequestedQuantity());
        result.setApprovedQuantity(item.getApprovedQuantity());
        result.setLockedUnitPrice(item.getLockedUnitPrice());
        result.setEstimatedAmount(
            OrderAmountCalculator.lineAmount(item.getRequestedQuantity(), item.getLockedUnitPrice()));
        result.setApprovedAmount(item.getApprovedAmount());
        if (orderItem != null) {
            result.setProductName(orderItem.getProductNameSnapshot());
            result.setSpecName(orderItem.getSpecNameSnapshot());
            result.setUnit(orderItem.getSaleUnitSnapshot());
            result.setPrimaryImageUrl(imageUrls.get(orderItem.getSkuId()));
        }
        return result;
    }

    /**
     * 退款结果与原路退回明细。资金分摊存在时按分摊行给出每个资金来源的实退进度；无分摊计划的旧路径（纯余额返还） 以余额流水记录拼出一行，客户看到的结果一致。
     */
    private MallAfterSaleRefundVO refundVo(OrderRefundEntity refund, OffsetDateTime fundedAt) {
        if (refund == null) {
            return null;
        }
        MallAfterSaleRefundVO result = new MallAfterSaleRefundVO();
        result.setRefundId(refund.getId());
        result.setRefundNo(refund.getRefundNo());
        result.setRefundAmount(refund.getRefundAmount());
        result.setStatus(refund.getStatus());
        result.setCompletedAt(fundedAt);
        List<RefundFundingAllocationEntity> allocations = refundFundingAllocationDao.listByRefund(refund.getId());
        if (!allocations.isEmpty()) {
            Map<Long,
                OffsetDateTime> succeededAtByAllocation = fundingSourceDao.allocationSuccessTimes(refund.getId())
                    .stream().filter(row -> row.getSucceededAt() != null).collect(Collectors.toMap(
                        MallAfterSaleFundingTimeVO::getAllocationId, MallAfterSaleFundingTimeVO::getSucceededAt));
            result.setAllocations(allocations.stream().map(row -> {
                MallAfterSaleRefundVO.Allocation allocation = new MallAfterSaleRefundVO.Allocation();
                allocation.setMethod(row.getSourceMethod());
                allocation.setAllocatedAmount(row.getAllocatedAmount());
                OffsetDateTime sourceCompletedAt = succeededAtByAllocation.get(row.getId());
                allocation.setStatus(ScmRefundFundingAllocationStatusEnum.SUCCEEDED.name().equals(row.getStatus())
                    && sourceCompletedAt == null
                        ? ScmRefundFundingAllocationStatusEnum.FAILED.name()
                        : row.getStatus());
                allocation.setSucceededAt(sourceCompletedAt);
                return allocation;
            }).toList());
            return result;
        }
        if (fundedAt == null) {
            return result;
        }
        orderRefundFundingSourceDao.selectBalanceReturns(List.of(refund.getId())).stream()
            .filter(fact -> fact.amount().compareTo(refund.getRefundAmount()) == 0).findFirst().ifPresent(fact -> {
                MallAfterSaleRefundVO.Allocation allocation = new MallAfterSaleRefundVO.Allocation();
                allocation.setMethod(ScmPaymentMethodEnum.BALANCE.name());
                allocation.setAllocatedAmount(fact.amount());
                allocation.setStatus(ScmRefundFundingAllocationStatusEnum.SUCCEEDED.name());
                allocation.setSucceededAt(fundedAt);
                result.getAllocations().add(allocation);
            });
        return result;
    }

    /**
     * 售后进度节点：{@code done} 只按「对应正式记录是否存在」判定，不推导状态、不预测下一步。
     *
     * <p>
     * 被驳回与已撤销在对应节点即止；审核通过后没有退款单（净额为 0）时末节点为「无需退款」。
     */
    private static List<MallOrderProgressNodeVO> progressNodes(OrderReturnEntity entity, OrderRefundEntity refund,
        OffsetDateTime fundedAt) {
        List<MallOrderProgressNodeVO> nodes = new ArrayList<>();
        nodes.add(node(MallOrderProgressNodeEnum.SUBMITTED.name(), "已提交申请", null, entity.getCreatedAt(), true));
        String status = entity.getStatus();
        if (ScmOrderReturnStatusEnum.REJECTED.name().equals(status)) {
            nodes.add(node(MallOrderProgressNodeEnum.REJECTED.name(), "申请已驳回", entity.getDecisionReason(),
                entity.getRejectedAt(), true));
            return nodes;
        }
        if (ScmOrderReturnStatusEnum.CANCELLED.name().equals(status)) {
            nodes.add(node(MallOrderProgressNodeEnum.CANCELLED.name(), "申请已撤销", null, entity.getCancelledAt(), true));
            return nodes;
        }
        if (ScmOrderReturnStatusEnum.PENDING.name().equals(status)) {
            nodes.add(node(MallOrderProgressNodeEnum.REVIEW.name(), "等待平台审核", null, null, false));
            return nodes;
        }
        nodes.add(node(MallOrderProgressNodeEnum.REVIEW.name(), "审核通过", null, entity.getApprovedAt(), true));
        if (refund == null) {
            nodes.add(node(MallOrderProgressNodeEnum.REFUND.name(), "无需退款", null, entity.getApprovedAt(), true));
            return nodes;
        }
        boolean completed = fundedAt != null;
        nodes.add(
            node(MallOrderProgressNodeEnum.REFUND.name(), completed ? "退款完成" : "退款处理中", null, fundedAt, completed));
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
}
