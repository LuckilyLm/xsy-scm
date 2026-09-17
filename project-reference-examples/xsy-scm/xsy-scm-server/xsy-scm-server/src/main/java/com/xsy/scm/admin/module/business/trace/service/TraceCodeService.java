package com.xsy.scm.admin.module.business.trace.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.admin.module.business.trace.constant.TraceCodeStatusEnum;
import com.xsy.scm.admin.module.business.trace.dao.TraceBatchDao;
import com.xsy.scm.admin.module.business.trace.dao.TraceCodeDao;
import com.xsy.scm.admin.module.business.trace.domain.entity.TraceBatchEntity;
import com.xsy.scm.admin.module.business.trace.domain.entity.TraceCodeEntity;
import com.xsy.scm.admin.module.business.trace.domain.form.TraceCodeGenerateForm;
import com.xsy.scm.admin.module.business.trace.domain.form.TraceCodeQueryForm;
import com.xsy.scm.admin.module.business.trace.domain.vo.TraceCodeVO;
import com.xsy.scm.admin.module.business.trace.manager.TraceCodeManager;
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
 * 溯源码 Service
 *
 * <p>颗粒度已定：**按批次（一码一批，13-01 / G-08）**，溯源码取批次号，天然唯一。</p>
 *
 * @author xsy-scm
 */
@Service
@Slf4j
public class TraceCodeService {

    @Resource
    private TraceCodeDao traceCodeDao;

    @Resource
    private TraceCodeManager traceCodeManager;

    @Resource
    private TraceBatchDao traceBatchDao;

    /**
     * 分页查询溯源码
     */
    public ResponseDTO<PageResult<TraceCodeVO>> query(TraceCodeQueryForm queryForm) {
        queryForm.setDeletedFlag(Boolean.FALSE);
        Page<?> page = SmartPageUtil.convert2PageQuery(queryForm);
        List<TraceCodeVO> list = traceCodeDao.queryPage(page, queryForm);
        return ResponseDTO.ok(SmartPageUtil.convert2PageResult(page, list));
    }

    /**
     * 按批次生成溯源码（一码一批；重复生成被拦截）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> generate(TraceCodeGenerateForm generateForm) {
        TraceBatchEntity batch = traceBatchDao.selectById(generateForm.getBatchId());
        if (batch == null || Boolean.TRUE.equals(batch.getDeletedFlag())) {
            return ResponseDTO.userErrorParam("溯源批次不存在");
        }
        Long count = traceCodeDao.countByBatchId(generateForm.getBatchId());
        if (count != null && count > 0) {
            return ResponseDTO.userErrorParam("该批次已生成溯源码，请勿重复生成");
        }
        TraceCodeEntity entity = new TraceCodeEntity();
        entity.setTraceCode(batch.getBatchNo());
        entity.setCodeType(1);
        entity.setProductId(batch.getProductId());
        entity.setSkuId(batch.getSkuId());
        entity.setBatchId(batch.getBatchId());
        entity.setQrcodeUrl("");
        entity.setStatus(TraceCodeStatusEnum.ENABLED.getValue());
        entity.setDeletedFlag(Boolean.FALSE);
        traceCodeManager.save(entity);
        return ResponseDTO.ok();
    }

    /**
     * 扫码：按溯源码查询（公开扫码入口调用）
     */
    public ResponseDTO<TraceCodeVO> getByCode(String traceCode) {
        TraceCodeEntity entity = traceCodeDao.getByTraceCode(traceCode);
        if (entity == null) {
            return ResponseDTO.userErrorParam("溯源码不存在或已作废");
        }
        return ResponseDTO.ok(SmartBeanUtil.copy(entity, TraceCodeVO.class));
    }

    /**
     * 作废溯源码（幂等）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> invalidate(Long codeId) {
        TraceCodeEntity entity = traceCodeDao.selectById(codeId);
        if (entity == null || Boolean.TRUE.equals(entity.getDeletedFlag())) {
            return ResponseDTO.userErrorParam("溯源码不存在");
        }
        if (TraceCodeStatusEnum.INVALID.getValue().equals(entity.getStatus())) {
            return ResponseDTO.ok();
        }
        TraceCodeEntity updateEntity = new TraceCodeEntity();
        updateEntity.setCodeId(codeId);
        updateEntity.setStatus(TraceCodeStatusEnum.INVALID.getValue());
        traceCodeManager.update(updateEntity);
        return ResponseDTO.ok();
    }

    /**
     * 删除溯源码（逻辑删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> delete(Long codeId) {
        traceCodeDao.batchUpdateDeleted(Collections.singletonList(codeId), Boolean.TRUE);
        return ResponseDTO.ok();
    }

    /**
     * 批量删除溯源码（逻辑删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> batchDelete(ValidateList<Long> idList) {
        traceCodeDao.batchUpdateDeleted(idList, Boolean.TRUE);
        return ResponseDTO.ok();
    }
}
