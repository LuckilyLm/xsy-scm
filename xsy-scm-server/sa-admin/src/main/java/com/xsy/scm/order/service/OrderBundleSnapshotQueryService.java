package com.xsy.scm.order.service;

import com.xsy.scm.order.dao.OrderBundleComponentDao;
import com.xsy.scm.order.domain.entity.OrderBundleComponentEntity;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 配送等履约模块只读取已确认订单保存的组合组成数据，不写快照表。
 */
@Service
@RequiredArgsConstructor
public class OrderBundleSnapshotQueryService {

    private final OrderBundleComponentDao orderBundleComponentDao;

    public List<OrderBundleComponentEntity> listByOrderItemIds(List<Long> orderItemIds) {
        return orderItemIds == null || orderItemIds.isEmpty()
            ? List.of()
            : orderBundleComponentDao.listByOrderItemIds(orderItemIds);
    }
}
