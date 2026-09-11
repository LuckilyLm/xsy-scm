package com.xianshuyuan.scm.mall.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xianshuyuan.scm.common.api.PageData;
import com.xianshuyuan.scm.common.exception.BusinessException;
import com.xianshuyuan.scm.mall.mapper.MallOrderMapper;
import com.xianshuyuan.scm.mall.vo.MallOrderItemResponse;
import com.xianshuyuan.scm.mall.vo.MallOrderResponse;
import com.xianshuyuan.scm.order.converter.SalesOrderConverter;
import com.xianshuyuan.scm.order.entity.OrderStatus;
import com.xianshuyuan.scm.order.entity.SalesOrderEntity;
import com.xianshuyuan.scm.order.entity.SalesOrderItemEntity;
import com.xianshuyuan.scm.order.mapper.SalesOrderItemMapper;
import com.xianshuyuan.scm.order.vo.SalesOrderItemResponse;
import com.xianshuyuan.scm.order.vo.SalesOrderResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 商城订单查询。只返回登录客户自己的订单，不暴露后台专用字段。
 */
@Service
@RequiredArgsConstructor
public class MallOrderService {

    private final MallOrderMapper orders;
    private final SalesOrderItemMapper items;

    public PageData<MallOrderResponse> page(long customerId, long page, long pageSize, OrderStatus status,
                                            String keyword) {
        Page<SalesOrderEntity> pager = new Page<>(page, pageSize);
        List<SalesOrderEntity> rows = orders.selectPageByCustomer(pager, customerId, status, keyword);
        if (rows.isEmpty()) {
            return new PageData<>(List.of(), page, pageSize, pager.getTotal());
        }
        Map<Long, List<SalesOrderItemEntity>> grouped = items.selectActiveByOrderIds(
                        rows.stream().map(SalesOrderEntity::getId).toList()).stream()
                .collect(Collectors.groupingBy(SalesOrderItemEntity::getOrderId));
        List<MallOrderResponse> list = rows.stream()
                .map(order -> toResponse(order, grouped.getOrDefault(order.getId(), List.of())))
                .toList();
        return new PageData<>(list, page, pageSize, pager.getTotal());
    }

    public MallOrderResponse detail(long customerId, long id) {
        SalesOrderEntity order = orders.selectActiveByIdAndCustomer(id, customerId);
        if (order == null) {
            throw new BusinessException(MallErrorCodes.ORDER_NOT_FOUND);
        }
        return toResponse(order, items.selectActiveByOrderId(id));
    }

    private static MallOrderResponse toResponse(SalesOrderEntity order, List<SalesOrderItemEntity> rows) {
        SalesOrderResponse response = SalesOrderConverter.toResponse(order, rows);
        List<MallOrderItemResponse> items = response.items().stream()
                .map(MallOrderService::toItem)
                .toList();
        return new MallOrderResponse(response.id(), response.orderNo(), response.status(), response.source(),
                response.totalAmount(), order.getCreatedAt(), order.getSubmittedAt(), order.getConfirmedAt(), items);
    }

    private static MallOrderItemResponse toItem(SalesOrderItemResponse item) {
        return new MallOrderItemResponse(item.id(), item.skuId(), item.productName(), item.specName(),
                item.specValues(), item.saleUnit(), item.productType(), item.orderedQuantity(),
                item.actualQuantity(), item.unitPrice(), item.priceSource(), item.amount());
    }
}
