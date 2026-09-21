package net.lab1024.sa.admin.module.scm.order.service;

import net.lab1024.sa.admin.module.scm.order.domain.entity.*;
import net.lab1024.sa.admin.module.scm.order.domain.form.*;
import net.lab1024.sa.admin.module.scm.order.domain.vo.*;
import net.lab1024.sa.admin.module.scm.order.dao.*;
import net.lab1024.sa.admin.module.scm.order.manager.*;
import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.common.constant.ScmOperator;

import static net.lab1024.sa.admin.module.scm.order.constant.OrderErrorCode.*;
import static net.lab1024.sa.admin.module.scm.common.error.ScmCommonErrorCode.VERSION_CONFLICT;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.BeanUtils;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.function.Function;

import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.util.SmartPageUtil;

@Service
@RequiredArgsConstructor
public class OrderRefundService {
    private final OrderRefundDao refunds;
    private final SalesOrderService orders;
    private final OrderIdempotencyService idempotency;

    public PageResult<OrderRefundVO> query(OrderRefundQueryForm f) {
        var page = SmartPageUtil.convert2PageQuery(f);
        return SmartPageUtil.convert2PageResult(page, refunds.query(page, f).stream().map(this::vo).toList());
    }

    private OrderRefundVO vo(OrderRefundEntity r) {
        var v = new OrderRefundVO();
        BeanUtils.copyProperties(r, v);
        v.setRefundId(r.getId());
        return v;
    }

    public OrderRefundVO detail(Long id) {
        var r = refunds.selectById(id);
        if (r == null) throw new ScmBusinessException(ORDER_REFUND_NOT_FOUND);
        return vo(r);
    }

    @Transactional(rollbackFor = Exception.class)
    public OrderRefundVO complete(OrderRefundCompleteForm f, String key) {
        var claim = idempotency.claim("ORDER_REFUND_COMPLETE:" + f.getRefundId(), key, f);
        if (claim.replay()) return idempotency.replay(claim, OrderRefundVO.class);
        var before = detail(f.getRefundId());
        orders.lock(before.getOrderId());
        var r = refunds.lock(f.getRefundId());
        SalesOrderService.version(r.getVersion(), f.getVersion());
        if (!"PENDING".equals(r.getStatus())) throw new ScmBusinessException(ORDER_REFUND_STATUS_INVALID);
        r.setStatus("COMPLETED");
        r.setExternalReference(OrderValidator.trim(f.getExternalReference()));
        r.setCompletedAt(OffsetDateTime.now());
        r.setUpdatedAt(r.getCompletedAt());
        r.setUpdatedBy(ScmOperator.current());
        try {
            if (refunds.updateById(r) != 1) throw new ScmBusinessException(VERSION_CONFLICT);
        } catch (org.springframework.dao.DuplicateKeyException ex) {
            throw new ScmBusinessException(ORDER_REFUND_STATUS_INVALID);
        }
        var result = detail(r.getId());
        idempotency.complete(claim, "ORDER_REFUND", r.getId(), result);
        return result;
    }
}
