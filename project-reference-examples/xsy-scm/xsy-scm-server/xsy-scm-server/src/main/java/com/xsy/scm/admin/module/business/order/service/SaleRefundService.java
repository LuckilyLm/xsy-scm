package com.xsy.scm.admin.module.business.order.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.admin.module.business.order.dao.SaleRefundDao;
import com.xsy.scm.admin.module.business.order.domain.entity.SaleRefundEntity;
import com.xsy.scm.admin.module.business.order.domain.form.SaleRefundAddForm;
import com.xsy.scm.admin.module.business.order.domain.form.SaleRefundQueryForm;
import com.xsy.scm.admin.module.business.order.domain.form.SaleRefundUpdateForm;
import com.xsy.scm.admin.module.business.order.domain.vo.SaleRefundVO;
import com.xsy.scm.admin.module.business.order.manager.SaleRefundManager;
import com.xsy.scm.base.common.domain.PageResult;
import com.xsy.scm.base.common.domain.ResponseDTO;
import com.xsy.scm.base.common.domain.ValidateList;
import com.xsy.scm.base.common.util.SmartBeanUtil;
import com.xsy.scm.base.common.util.SmartPageUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

/**
 * 销售退款单 Service
 *
 * @author xsy-scm
 */
@Service
@Slf4j
public class SaleRefundService {

    @Resource
    private SaleRefundDao refundDao;

    @Resource
    private SaleRefundManager refundManager;

    /**
     * 分页查询退款单（按订单维度）
     */
    public ResponseDTO<PageResult<SaleRefundVO>> query(SaleRefundQueryForm queryForm) {
        queryForm.setDeletedFlag(Boolean.FALSE);
        Page<?> page = SmartPageUtil.convert2PageQuery(queryForm);
        List<SaleRefundVO> list = refundDao.queryPage(page, queryForm);
        return ResponseDTO.ok(SmartPageUtil.convert2PageResult(page, list));
    }

    /**
     * 新增退款单，退款单号取 TKD + yyyyMMdd + 4 位自增主键
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> add(SaleRefundAddForm addForm) {
        SaleRefundEntity entity = SmartBeanUtil.copy(addForm, SaleRefundEntity.class);
        entity.setDeletedFlag(Boolean.FALSE);
        refundManager.save(entity);
        String refundNo = "TKD" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                + String.format("%04d", entity.getRefundId());
        refundDao.updateRefundNo(entity.getRefundId(), refundNo);
        return ResponseDTO.ok();
    }

    /**
     * 更新退款单
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> update(SaleRefundUpdateForm updateForm) {
        SaleRefundEntity entity = SmartBeanUtil.copy(updateForm, SaleRefundEntity.class);
        refundManager.update(entity);
        return ResponseDTO.ok();
    }

    /**
     * 删除退款单（逻辑删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> delete(Long refundId) {
        refundDao.batchUpdateDeleted(Collections.singletonList(refundId), Boolean.TRUE);
        return ResponseDTO.ok();
    }

    /**
     * 批量删除退款单（逻辑删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> batchDelete(ValidateList<Long> idList) {
        refundDao.batchUpdateDeleted(idList, Boolean.TRUE);
        return ResponseDTO.ok();
    }
}
