package com.xsy.scm.mall.order.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.mall.account.constant.MallErrorCode;
import com.xsy.scm.mall.account.domain.vo.RequestMallCustomer;
import com.xsy.scm.mall.order.domain.form.MallOrderQueryForm;
import com.xsy.scm.mall.order.domain.vo.MallOrderItemVO;
import com.xsy.scm.mall.order.domain.vo.MallOrderVO;
import com.xsy.scm.order.dao.OrderAddressSnapshotDao;
import com.xsy.scm.order.dao.SalesOrderDao;
import com.xsy.scm.order.dao.SalesOrderItemDao;
import com.xsy.scm.order.domain.entity.SalesOrderEntity;
import com.xsy.scm.order.domain.entity.SalesOrderItemEntity;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.domain.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MallOrderQueryService {
    private final SalesOrderDao salesOrderDao;
    private final SalesOrderItemDao salesOrderItemDao;
    private final OrderAddressSnapshotDao addressSnapshotDao;

    @Transactional(readOnly = true)
    public PageResult<MallOrderVO> list(RequestMallCustomer customer, MallOrderQueryForm form) {
        Page<SalesOrderEntity> page = new Page<>(form.getPageNum(), form.getPageSize(), true);
        List<MallOrderVO> rows = salesOrderDao.listByCustomer(page, customer.getCustomerId()).stream()
                .map(this::summary)
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
        SalesOrderEntity order = salesOrderDao.selectByIdAndCustomer(orderId, customer.getCustomerId());
        if (order == null) {
            throw new ScmBusinessException(MallErrorCode.ORDER_NOT_FOUND);
        }
        MallOrderVO result = summary(order);
        result.setItems(salesOrderItemDao.list(orderId).stream()
                .sorted(Comparator.comparing(SalesOrderItemEntity::getSortOrder)
                        .thenComparing(SalesOrderItemEntity::getId))
                .map(this::item)
                .toList());
        addressSnapshotDao.list(orderId).stream().findFirst().ifPresent(address -> {
            result.setReceiverName(address.getReceiverName());
            result.setReceiverPhone(address.getReceiverPhone());
            result.setAddress(address.getAddress());
        });
        return result;
    }

    private MallOrderVO summary(SalesOrderEntity order) {
        MallOrderVO result = new MallOrderVO();
        result.setOrderId(order.getId());
        result.setOrderNo(order.getOrderNo());
        result.setStatus(order.getStatus());
        result.setOrderedTotalAmount(order.getOrderedTotalAmount());
        result.setSettlementTotalAmount(order.getSettlementTotalAmount());
        result.setExpectDeliveryTime(order.getExpectDeliveryTime());
        result.setRemark(order.getRemark());
        result.setCreatedAt(order.getCreatedAt());
        result.setSubmittedAt(order.getSubmittedAt());
        result.setConfirmedAt(order.getConfirmedAt());
        return result;
    }

    private MallOrderItemVO item(SalesOrderItemEntity row) {
        MallOrderItemVO result = new MallOrderItemVO();
        result.setItemId(row.getId());
        result.setSkuId(row.getSkuId());
        result.setProductName(row.getProductNameSnapshot());
        result.setSpecName(row.getSpecNameSnapshot());
        result.setSaleUnit(row.getSaleUnitSnapshot());
        result.setProductType(row.getProductTypeSnapshot());
        result.setOrderedQuantity(row.getOrderedQuantity());
        result.setActualQuantity(row.getActualQuantity());
        result.setUnitPrice(row.getLockedUnitPrice());
        result.setOrderedLineAmount(row.getOrderedLineAmount());
        return result;
    }
}
