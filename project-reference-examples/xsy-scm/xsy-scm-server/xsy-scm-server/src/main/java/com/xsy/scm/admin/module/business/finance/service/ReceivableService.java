package com.xsy.scm.admin.module.business.finance.service;

import com.xsy.scm.admin.module.business.customer.constant.PeriodUnitEnum;
import com.xsy.scm.admin.module.business.customer.dao.CustomerPeriodDao;
import com.xsy.scm.admin.module.business.customer.domain.entity.CustomerPeriodEntity;
import com.xsy.scm.admin.module.business.finance.constant.ReceivableStatusEnum;
import com.xsy.scm.admin.module.business.finance.domain.entity.ReceivableEntity;
import com.xsy.scm.admin.module.business.finance.manager.ReceivableManager;
import com.xsy.scm.admin.module.business.order.domain.entity.SaleOrderEntity;
import com.xsy.scm.base.module.support.serialnumber.constant.SerialNumberIdEnum;
import com.xsy.scm.base.module.support.serialnumber.service.SerialNumberService;
import com.xsy.scm.admin.module.business.finance.dao.ReceivableDao;
import com.xsy.scm.admin.module.business.finance.domain.form.ReceivableQueryForm;
import com.xsy.scm.admin.module.business.finance.domain.vo.ReceivableVO;
import com.xsy.scm.base.common.domain.PageResult;
import com.xsy.scm.base.common.domain.ResponseDTO;
import com.xsy.scm.base.common.exception.BusinessException;
import com.xsy.scm.base.common.util.SmartPageUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * 应收单 Service
 *
 * <p>应收在订单签收后生成（09-01 已定），按订单核算金额写入，待收余额初始等于应收金额。</p>
 *
 * @author xsy-scm
 */
@Service
@Slf4j
public class ReceivableService {

    @Resource
    private ReceivableManager receivableManager;

    @Resource
    private ReceivableDao receivableDao;

    @Resource
    private SerialNumberService serialNumberService;

    @Resource
    private CustomerPeriodDao customerPeriodDao;

    /**
     * 按订单生成应收（由订单签收动作调用）
     */
    public void generateFromOrder(SaleOrderEntity order) {
        // 应收金额取订单核算金额（09-01）；尚未核算（实重未定）时依次退回应付金额、下单金额，避免出现 0 元应收。
        // G-04：最终金额一律 HALF_UP 保留 2 位（到分）
        BigDecimal amount = order.getActualAmount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            amount = order.getPayableAmount();
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            amount = order.getTotalAmount();
        }
        amount = (amount == null ? BigDecimal.ZERO : amount).setScale(2, RoundingMode.HALF_UP);

        ReceivableEntity entity = new ReceivableEntity();
        entity.setReceivableNo(serialNumberService.generate(SerialNumberIdEnum.RECEIVABLE));
        entity.setOrderId(order.getOrderId());
        entity.setCustomerId(order.getCustomerId());
        entity.setSettleCustomerId(order.getSettleCustomerId());
        entity.setSettleType(order.getSettleType());
        entity.setAmount(amount);
        entity.setReceivedAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        entity.setBalanceAmount(amount);
        entity.setDueTime(calcDueTime(order.getSettleCustomerId() != null ? order.getSettleCustomerId() : order.getCustomerId()));
        entity.setStatus(ReceivableStatusEnum.PENDING.getValue());
        entity.setDeletedFlag(Boolean.FALSE);
        receivableManager.save(entity);
    }

    /**
     * 按客户「时间型」账期计算到期日；未配置时间型账期时返回 null（按金额账期不产生固定到期日）。
     */
    /**
     * 分页查询应收单
     */
    public ResponseDTO<PageResult<ReceivableVO>> query(ReceivableQueryForm queryForm) {
        queryForm.setDeletedFlag(Boolean.FALSE);
        Page<?> page = SmartPageUtil.convert2PageQuery(queryForm);
        List<ReceivableVO> list = receivableDao.queryPage(page, queryForm);
        PageResult<ReceivableVO> pageResult = SmartPageUtil.convert2PageResult(page, list);
        return ResponseDTO.ok(pageResult);
    }

    /**
     * 核销应收：确认收款时调用，按收款金额冲减待收余额；余额为 0 转「已结清」，否则转「部分收款」。
     */
    @Transactional(rollbackFor = Exception.class)
    public void writeOff(Long receivableId, BigDecimal amount) {
        ReceivableEntity receivable = receivableManager.getById(receivableId);
        if (receivable == null) {
            throw new BusinessException("应收单不存在");
        }
        if (ReceivableStatusEnum.SETTLED.getValue().equals(receivable.getStatus())) {
            throw new BusinessException("应收单已结清");
        }
        BigDecimal writeOff = amount;
        if (writeOff.compareTo(receivable.getBalanceAmount()) > 0) {
            writeOff = receivable.getBalanceAmount();
        }
        BigDecimal received = receivable.getReceivedAmount().add(writeOff);
        BigDecimal balance = receivable.getBalanceAmount().subtract(writeOff);
        receivable.setReceivedAmount(received);
        receivable.setBalanceAmount(balance);
        receivable.setStatus(balance.compareTo(BigDecimal.ZERO) <= 0
                ? ReceivableStatusEnum.SETTLED.getValue()
                : ReceivableStatusEnum.PARTIAL.getValue());
        receivableManager.update(receivable);
    }

    private LocalDateTime calcDueTime(Long customerId) {
        if (customerId == null) {
            return null;
        }
        CustomerPeriodEntity period = customerPeriodDao.queryEffectiveTimePeriod(customerId);
        if (period == null || period.getPeriodValue() == null) {
            return null;
        }
        LocalDateTime now = LocalDateTime.now();
        if (PeriodUnitEnum.MONTH.getValue().equals(period.getPeriodUnit())) {
            return now.plusMonths(period.getPeriodValue());
        }
        return now.plusDays(period.getPeriodValue());
    }
}
