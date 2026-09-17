package com.xsy.scm.admin.module.business.finance.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.admin.module.business.finance.constant.InvoiceStatusEnum;
import com.xsy.scm.admin.module.business.finance.dao.InvoiceDao;
import com.xsy.scm.admin.module.business.finance.domain.entity.InvoiceEntity;
import com.xsy.scm.admin.module.business.finance.domain.form.InvoiceAddForm;
import com.xsy.scm.admin.module.business.finance.domain.form.InvoiceQueryForm;
import com.xsy.scm.admin.module.business.finance.domain.vo.InvoiceVO;
import com.xsy.scm.admin.module.business.finance.manager.InvoiceManager;
import com.xsy.scm.base.common.domain.PageResult;
import com.xsy.scm.base.common.domain.ResponseDTO;
import com.xsy.scm.base.common.domain.ValidateList;
import com.xsy.scm.base.common.util.SmartBeanUtil;
import com.xsy.scm.base.common.util.SmartPageUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

/**
 * 发票 Service
 *
 * <p>支持按税率拆分开票后的**整单红冲联动**（对标蔬东坡 17.1）：
 * 红冲其中一张时，同订单下所有未红冲发票一并红冲；全部红冲后可重新开票。</p>
 *
 * @author xsy-scm
 */
@Service
@Slf4j
public class InvoiceService {

    @Resource
    private InvoiceDao invoiceDao;

    @Resource
    private InvoiceManager invoiceManager;

    /**
     * 分页查询发票
     */
    public ResponseDTO<PageResult<InvoiceVO>> query(InvoiceQueryForm queryForm) {
        queryForm.setDeletedFlag(Boolean.FALSE);
        Page<?> page = SmartPageUtil.convert2PageQuery(queryForm);
        List<InvoiceVO> list = invoiceDao.queryPage(page, queryForm);
        return ResponseDTO.ok(SmartPageUtil.convert2PageResult(page, list));
    }

    /**
     * 登记开票
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> add(InvoiceAddForm addForm) {
        InvoiceEntity entity = SmartBeanUtil.copy(addForm, InvoiceEntity.class);
        entity.setStatus(InvoiceStatusEnum.INVOICED.getValue());
        entity.setDeletedFlag(Boolean.FALSE);
        invoiceManager.save(entity);
        return ResponseDTO.ok();
    }

    /**
     * 红冲：同订单未红冲发票一并红冲（整单联动；幂等）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> redFlush(Long invoiceId) {
        InvoiceEntity entity = invoiceDao.selectById(invoiceId);
        if (entity == null || Boolean.TRUE.equals(entity.getDeletedFlag())) {
            return ResponseDTO.userErrorParam("发票不存在");
        }
        if (InvoiceStatusEnum.RED_FLUSHED.getValue().equals(entity.getStatus())) {
            return ResponseDTO.ok();
        }
        List<InvoiceEntity> orderInvoices = invoiceDao.listByOrderId(entity.getOrderId());
        for (InvoiceEntity invoice : orderInvoices) {
            if (InvoiceStatusEnum.RED_FLUSHED.getValue().equals(invoice.getStatus())) {
                continue;
            }
            InvoiceEntity updateEntity = new InvoiceEntity();
            updateEntity.setInvoiceId(invoice.getInvoiceId());
            updateEntity.setStatus(InvoiceStatusEnum.RED_FLUSHED.getValue());
            invoiceManager.update(updateEntity);
        }
        return ResponseDTO.ok();
    }

    /**
     * 删除发票（逻辑删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> delete(Long invoiceId) {
        invoiceDao.batchUpdateDeleted(Collections.singletonList(invoiceId), Boolean.TRUE);
        return ResponseDTO.ok();
    }

    /**
     * 批量删除发票（逻辑删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> batchDelete(ValidateList<Long> idList) {
        invoiceDao.batchUpdateDeleted(idList, Boolean.TRUE);
        return ResponseDTO.ok();
    }
}
