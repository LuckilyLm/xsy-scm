package com.xsy.scm.admin.module.business.finance.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.admin.module.business.finance.constant.PaymentStatusEnum;
import com.xsy.scm.admin.module.business.finance.dao.PaymentDao;
import com.xsy.scm.admin.module.business.finance.domain.entity.PaymentEntity;
import com.xsy.scm.admin.module.business.finance.domain.form.PaymentAddForm;
import com.xsy.scm.admin.module.business.finance.domain.form.PaymentQueryForm;
import com.xsy.scm.admin.module.business.finance.domain.vo.PaymentVO;
import com.xsy.scm.admin.module.business.finance.manager.PaymentManager;
import com.xsy.scm.admin.module.business.finance.manager.ReceivableManager;
import com.xsy.scm.base.common.domain.PageResult;
import com.xsy.scm.base.common.domain.ResponseDTO;
import com.xsy.scm.base.common.exception.BusinessException;
import com.xsy.scm.base.common.util.SmartBeanUtil;
import com.xsy.scm.base.common.util.SmartPageUtil;
import com.xsy.scm.base.module.support.serialnumber.constant.SerialNumberIdEnum;
import com.xsy.scm.base.module.support.serialnumber.service.SerialNumberService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 收款单 Service
 *
 * <p>客户回款登记（待确认），财务确认收款后核销应收（09-01 已定）。</p>
 *
 * @author xsy-scm
 */
@Service
@Slf4j
public class PaymentService {

    @Resource
    private PaymentManager paymentManager;

    @Resource
    private PaymentDao paymentDao;

    @Resource
    private ReceivableManager receivableManager;

    @Resource
    private SerialNumberService serialNumberService;

    @Resource
    private ReceivableService receivableService;

    /**
     * 登记收款单（客户回款），初始状态「待确认」。
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> add(PaymentAddForm form) {
        PaymentEntity entity = SmartBeanUtil.copy(form, PaymentEntity.class);
        entity.setPaymentNo(serialNumberService.generate(SerialNumberIdEnum.PAYMENT));
        if (entity.getPayTime() == null) {
            entity.setPayTime(LocalDateTime.now());
        }
        entity.setAmount(entity.getAmount().setScale(2, RoundingMode.HALF_UP));
        entity.setStatus(PaymentStatusEnum.PENDING.getValue());
        entity.setDeletedFlag(Boolean.FALSE);
        paymentManager.save(entity);
        return ResponseDTO.ok();
    }

    /**
     * 确认收款：核销应收（更新应收 received / balance，余额归零转「已结清」），收款单转「已确认」。
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> confirm(Long paymentId) {
        PaymentEntity payment = paymentManager.getById(paymentId);
        if (payment == null) {
            throw new BusinessException("收款单不存在");
        }
        if (!PaymentStatusEnum.PENDING.getValue().equals(payment.getStatus())) {
            throw new BusinessException("仅待确认收款单可以确认");
        }
        if (payment.getReceivableId() != null) {
            receivableService.writeOff(payment.getReceivableId(), payment.getAmount());
        }
        payment.setStatus(PaymentStatusEnum.CONFIRMED.getValue());
        paymentManager.update(payment);
        return ResponseDTO.ok();
    }

    /**
     * 分页查询收款单
     */
    public ResponseDTO<PageResult<PaymentVO>> query(PaymentQueryForm queryForm) {
        queryForm.setDeletedFlag(Boolean.FALSE);
        Page<?> page = SmartPageUtil.convert2PageQuery(queryForm);
        List<PaymentVO> list = paymentDao.queryPage(page, queryForm);
        PageResult<PaymentVO> pageResult = SmartPageUtil.convert2PageResult(page, list);
        return ResponseDTO.ok(pageResult);
    }
}
