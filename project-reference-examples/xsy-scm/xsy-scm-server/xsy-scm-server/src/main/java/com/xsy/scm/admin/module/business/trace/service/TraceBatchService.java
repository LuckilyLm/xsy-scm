package com.xsy.scm.admin.module.business.trace.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.admin.module.business.trace.constant.ShelfLifeUnitEnum;
import com.xsy.scm.admin.module.business.trace.constant.TraceBatchStatusEnum;
import com.xsy.scm.admin.module.business.trace.dao.TraceBatchDao;
import com.xsy.scm.admin.module.business.trace.domain.entity.TraceBatchEntity;
import com.xsy.scm.admin.module.business.trace.domain.form.TraceBatchAddForm;
import com.xsy.scm.admin.module.business.trace.domain.form.TraceBatchQueryForm;
import com.xsy.scm.admin.module.business.trace.domain.form.TraceBatchUpdateForm;
import com.xsy.scm.admin.module.business.trace.domain.vo.TraceBatchVO;
import com.xsy.scm.admin.module.business.trace.manager.TraceBatchManager;
import com.xsy.scm.base.common.domain.PageResult;
import com.xsy.scm.base.common.domain.ResponseDTO;
import com.xsy.scm.base.common.domain.ValidateList;
import com.xsy.scm.base.common.util.SmartBeanUtil;
import com.xsy.scm.base.common.util.SmartPageUtil;
import com.xsy.scm.base.module.support.serialnumber.constant.SerialNumberIdEnum;
import com.xsy.scm.base.module.support.serialnumber.service.SerialNumberService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

/**
 * 溯源批次 Service（生产批号模式）
 *
 * <p>对标蔬东坡 17.5：生产批号与库存批次解耦；保质期支持按天 / 按月（按月按自然月计算）。</p>
 *
 * @author xsy-scm
 */
@Service
@Slf4j
public class TraceBatchService {

    @Resource
    private TraceBatchDao traceBatchDao;

    @Resource
    private TraceBatchManager traceBatchManager;

    @Resource
    private SerialNumberService serialNumberService;

    /**
     * 分页查询溯源批次
     */
    public ResponseDTO<PageResult<TraceBatchVO>> query(TraceBatchQueryForm queryForm) {
        queryForm.setDeletedFlag(Boolean.FALSE);
        Page<?> page = SmartPageUtil.convert2PageQuery(queryForm);
        List<TraceBatchVO> list = traceBatchDao.queryPage(page, queryForm);
        return ResponseDTO.ok(SmartPageUtil.convert2PageResult(page, list));
    }

    /**
     * 新增溯源批次，批次号由编号生成器生成（PCB + 日期 + 流水）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> add(TraceBatchAddForm addForm) {
        TraceBatchEntity entity = SmartBeanUtil.copy(addForm, TraceBatchEntity.class);
        entity.setBatchNo(serialNumberService.generate(SerialNumberIdEnum.TRACE_BATCH));
        entity.setExpireDate(calcExpireDate(addForm.getProduceDate(), addForm.getShelfLifeUnit(), addForm.getShelfLifeValue()));
        entity.setStatus(TraceBatchStatusEnum.VALID.getValue());
        entity.setDeletedFlag(Boolean.FALSE);
        traceBatchManager.save(entity);
        return ResponseDTO.ok();
    }

    /**
     * 更新溯源批次（按最新保质期重算到期日期）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> update(TraceBatchUpdateForm updateForm) {
        TraceBatchEntity entity = SmartBeanUtil.copy(updateForm, TraceBatchEntity.class);
        entity.setExpireDate(calcExpireDate(updateForm.getProduceDate(), updateForm.getShelfLifeUnit(), updateForm.getShelfLifeValue()));
        traceBatchManager.update(entity);
        return ResponseDTO.ok();
    }

    /**
     * 删除溯源批次（逻辑删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> delete(Long batchId) {
        traceBatchDao.batchUpdateDeleted(Collections.singletonList(batchId), Boolean.TRUE);
        return ResponseDTO.ok();
    }

    /**
     * 批量删除溯源批次（逻辑删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> batchDelete(ValidateList<Long> idList) {
        traceBatchDao.batchUpdateDeleted(idList, Boolean.TRUE);
        return ResponseDTO.ok();
    }

    /**
     * 计算到期日期：按月按自然月（如 1 月 5 日 + 1 月 = 2 月 5 日）
     */
    private LocalDate calcExpireDate(LocalDate produceDate, Integer unit, Integer value) {
        if (produceDate == null || unit == null || value == null || value <= 0) {
            return null;
        }
        if (ShelfLifeUnitEnum.MONTH.getValue().equals(unit)) {
            return produceDate.plusMonths(value);
        }
        return produceDate.plusDays(value);
    }
}
