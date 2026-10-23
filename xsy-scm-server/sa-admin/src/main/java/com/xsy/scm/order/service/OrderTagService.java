package com.xsy.scm.order.service;

import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.scope.ScmDataScopeException;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.order.dao.OrderTagDao;
import com.xsy.scm.order.dao.SalesOrderDao;
import com.xsy.scm.order.domain.form.OrderTagAssignForm;
import com.xsy.scm.order.domain.form.OrderTagCreateForm;
import com.xsy.scm.order.domain.vo.OrderTagVO;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.xsy.scm.common.error.ScmCommonErrorCode.VALIDATION_ERROR;
import static com.xsy.scm.order.constant.OrderErrorCode.ORDER_NOT_FOUND;

/**
 * 分类标签只写独立关系表。所有批量写入先锁定订单，验证当前业务员范围，不能从标签反查越权订单。
 */
@Service
@RequiredArgsConstructor
public class OrderTagService {
    private static final List<String> COLORS = List.of("BLUE", "GREEN", "ORANGE", "RED", "PURPLE", "GRAY");
    private final OrderTagDao tagDao;
    private final SalesOrderDao orderDao;
    private final ScmDataScopeService scopeService;

    @Transactional(readOnly = true)
    public List<OrderTagVO> list() {
        return tagDao.tags();
    }

    @Transactional
    public Long create(OrderTagCreateForm form) {
        String name = form.getName().trim();
        if (name.isEmpty() || !COLORS.contains(form.getColor())) {
            throw new ScmBusinessException(VALIDATION_ERROR);
        }
        OrderTagVO tag = new OrderTagVO();
        tag.setName(name);
        tag.setColor(form.getColor());
        try {
            tagDao.create(tag, ScmOperator.current());
        } catch (DuplicateKeyException duplicate) {
            throw new ScmBusinessException(VALIDATION_ERROR);
        }
        return tag.getTagId();
    }

    @Transactional
    public void apply(OrderTagAssignForm form) {
        if (tagDao.tag(form.getTagId()) == null) {
            throw new ScmBusinessException(VALIDATION_ERROR);
        }
        List<Long> ids = form.getOrderIds().stream().distinct().sorted().toList();
        if (ids.size() != form.getOrderIds().size()) {
            throw new ScmBusinessException(VALIDATION_ERROR);
        }
        var scope = scopeService.resolve().getOrderSellerScope();
        String operator = ScmOperator.current();
        // 先锁定并完整检查所有订单，任何一单不存在或不在数据范围内都整批回滚。
        for (Long id : ids) {
            var order = orderDao.lock(id);
            if (order == null)
                throw new ScmBusinessException(ORDER_NOT_FOUND);
            if (!scope.allows(order.getSellerId()))
                throw new ScmDataScopeException();
        }
        for (Long id : ids) {
            if (form.getAction() == OrderTagAssignForm.Action.ADD) {
                tagDao.attach(id, form.getTagId(), operator);
            } else {
                tagDao.detach(id, form.getTagId());
            }
        }
    }
}
