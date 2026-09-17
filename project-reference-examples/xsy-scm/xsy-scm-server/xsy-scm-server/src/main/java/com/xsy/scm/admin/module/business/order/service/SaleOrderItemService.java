package com.xsy.scm.admin.module.business.order.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.admin.module.business.customer.dao.CustomerDao;
import com.xsy.scm.admin.module.business.customer.domain.entity.CustomerEntity;
import com.xsy.scm.admin.module.business.order.dao.SaleOrderDao;
import com.xsy.scm.admin.module.business.order.dao.SaleOrderItemDao;
import com.xsy.scm.admin.module.business.order.domain.entity.SaleOrderEntity;
import com.xsy.scm.admin.module.business.order.domain.entity.SaleOrderItemEntity;
import com.xsy.scm.admin.module.business.order.domain.form.SaleOrderItemAddForm;
import com.xsy.scm.admin.module.business.order.domain.form.SaleOrderItemQueryForm;
import com.xsy.scm.admin.module.business.order.domain.form.SaleOrderItemUpdateForm;
import com.xsy.scm.admin.module.business.order.domain.vo.SaleOrderItemVO;
import com.xsy.scm.admin.module.business.order.manager.SaleOrderItemManager;
import com.xsy.scm.admin.module.business.product.domain.bo.ResolvedPriceBO;
import com.xsy.scm.admin.module.business.product.service.ProductPriceService;
import com.xsy.scm.base.common.domain.PageResult;
import com.xsy.scm.base.common.domain.ResponseDTO;
import com.xsy.scm.base.common.domain.ValidateList;
import com.xsy.scm.base.common.exception.BusinessException;
import com.xsy.scm.base.common.util.SmartBeanUtil;
import com.xsy.scm.base.common.util.SmartPageUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 销售订单明细 Service
 *
 * @author xsy-scm
 */
@Service
@Slf4j
public class SaleOrderItemService {

    @Resource
    private SaleOrderItemDao itemDao;

    @Resource
    private SaleOrderItemManager itemManager;

    @Resource
    private SaleOrderDao saleOrderDao;

    @Resource
    private CustomerDao customerDao;

    @Resource
    private ProductPriceService productPriceService;

    /**
     * 分页查询订单明细（按订单维度）
     */
    public ResponseDTO<PageResult<SaleOrderItemVO>> query(SaleOrderItemQueryForm queryForm) {
        queryForm.setDeletedFlag(Boolean.FALSE);
        Page<?> page = SmartPageUtil.convert2PageQuery(queryForm);
        List<SaleOrderItemVO> list = itemDao.queryPage(page, queryForm);
        return ResponseDTO.ok(SmartPageUtil.convert2PageResult(page, list));
    }

    /**
     * 新增订单明细：服务端按价格优先级取价，锁定成交价快照与取价类型
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> add(SaleOrderItemAddForm addForm) {
        SaleOrderItemEntity entity = SmartBeanUtil.copy(addForm, SaleOrderItemEntity.class);
        entity.setDeletedFlag(Boolean.FALSE);
        // 覆盖客户端传入的成交价快照，强制服务端按优先级取价
        fillResolvedPrice(addForm, entity);
        itemManager.save(entity);
        recalcOrderAmount(addForm.getOrderId());
        return ResponseDTO.ok();
    }

    /**
     * 更新订单明细：成交价快照下单时锁定，更新保持不变，仅按数量重算明细金额
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> update(SaleOrderItemUpdateForm updateForm) {
        SaleOrderItemEntity old = itemDao.selectById(updateForm.getItemId());
        if (old == null) {
            throw new BusinessException("订单明细不存在");
        }
        SaleOrderItemEntity entity = SmartBeanUtil.copy(updateForm, SaleOrderItemEntity.class);
        // 成交价快照与取价类型不允许被覆盖
        entity.setSnapshotPrice(old.getSnapshotPrice());
        entity.setPriceType(old.getPriceType());
        BigDecimal quantity = entity.getQuantity() != null ? entity.getQuantity() : old.getQuantity();
        entity.setItemAmount(old.getSnapshotPrice().multiply(quantity));
        itemManager.update(entity);
        Long orderId = entity.getOrderId() != null ? entity.getOrderId() : old.getOrderId();
        recalcOrderAmount(orderId);
        return ResponseDTO.ok();
    }

    /**
     * 删除订单明细（逻辑删除）并重算订单头金额
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> delete(Long itemId) {
        SaleOrderItemEntity item = itemDao.selectById(itemId);
        Long orderId = item != null ? item.getOrderId() : null;
        itemDao.batchUpdateDeleted(Collections.singletonList(itemId), Boolean.TRUE);
        if (orderId != null) {
            recalcOrderAmount(orderId);
        }
        return ResponseDTO.ok();
    }

    /**
     * 批量删除订单明细（逻辑删除）并重算涉及的订单头金额
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> batchDelete(ValidateList<Long> idList) {
        List<SaleOrderItemEntity> items = itemDao.selectBatchIds(idList);
        Set<Long> orderIds = items.stream()
                .map(SaleOrderItemEntity::getOrderId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        itemDao.batchUpdateDeleted(idList, Boolean.TRUE);
        orderIds.forEach(this::recalcOrderAmount);
        return ResponseDTO.ok();
    }

    /**
     * 按价格优先级解析成交价，写入明细快照字段与明细金额
     */
    private void fillResolvedPrice(SaleOrderItemAddForm form, SaleOrderItemEntity entity) {
        SaleOrderEntity order = saleOrderDao.selectById(form.getOrderId());
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        Long customerId = order.getCustomerId();
        Long customerLevelId = null;
        if (customerId != null) {
            CustomerEntity customer = customerDao.selectById(customerId);
            customerLevelId = customer != null ? customer.getCustomerLevelId() : null;
        }
        ResolvedPriceBO resolved = productPriceService.resolvePrice(form.getProductId(), form.getSkuId(), customerId, customerLevelId);
        entity.setSnapshotPrice(resolved.getPrice());
        entity.setPriceType(resolved.getPriceType().getValue());
        BigDecimal quantity = form.getQuantity() != null ? form.getQuantity() : BigDecimal.ZERO;
        entity.setItemAmount(resolved.getPrice().multiply(quantity));
    }

    /**
     * 重算订单头金额：下单金额 = 明细金额合计；应付 = 下单金额 - 优惠金额
     */
    private void recalcOrderAmount(Long orderId) {
        BigDecimal total = itemDao.sumItemAmount(orderId);
        SaleOrderEntity order = saleOrderDao.selectById(orderId);
        if (order == null) {
            return;
        }
        BigDecimal discount = order.getDiscountAmount() != null ? order.getDiscountAmount() : BigDecimal.ZERO;
        order.setTotalAmount(total);
        order.setPayableAmount(total.subtract(discount));
        saleOrderDao.updateById(order);
    }
}
