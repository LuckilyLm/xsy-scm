package com.xsy.scm.admin.module.business.trace.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.admin.module.business.trace.dao.TraceInspectDao;
import com.xsy.scm.admin.module.business.trace.domain.entity.TraceInspectEntity;
import com.xsy.scm.admin.module.business.trace.domain.form.TraceInspectAddForm;
import com.xsy.scm.admin.module.business.trace.domain.form.TraceInspectQueryForm;
import com.xsy.scm.admin.module.business.trace.domain.vo.TraceInspectVO;
import com.xsy.scm.admin.module.business.trace.manager.TraceInspectManager;
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
 * 检测报告 Service
 *
 * <p>对标蔬东坡 17.4 / 17.5：支持报告名称与匹配模式（绑定采购单 / 绑定生产批号）。</p>
 *
 * @author xsy-scm
 */
@Service
@Slf4j
public class TraceInspectService {

    /**
     * 状态：有效 / 已作废
     */
    private static final int STATUS_VALID = 1;
    private static final int STATUS_INVALID = 2;

    @Resource
    private TraceInspectDao traceInspectDao;

    @Resource
    private TraceInspectManager traceInspectManager;

    /**
     * 分页查询检测报告
     */
    public ResponseDTO<PageResult<TraceInspectVO>> query(TraceInspectQueryForm queryForm) {
        queryForm.setDeletedFlag(Boolean.FALSE);
        Page<?> page = SmartPageUtil.convert2PageQuery(queryForm);
        List<TraceInspectVO> list = traceInspectDao.queryPage(page, queryForm);
        return ResponseDTO.ok(SmartPageUtil.convert2PageResult(page, list));
    }

    /**
     * 新增检测报告
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> add(TraceInspectAddForm addForm) {
        TraceInspectEntity entity = SmartBeanUtil.copy(addForm, TraceInspectEntity.class);
        entity.setStatus(STATUS_VALID);
        entity.setDeletedFlag(Boolean.FALSE);
        traceInspectManager.save(entity);
        return ResponseDTO.ok();
    }

    /**
     * 作废检测报告（幂等）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> invalidate(Long inspectId) {
        TraceInspectEntity entity = traceInspectDao.selectById(inspectId);
        if (entity == null || Boolean.TRUE.equals(entity.getDeletedFlag())) {
            return ResponseDTO.userErrorParam("检测报告不存在");
        }
        if (Integer.valueOf(STATUS_INVALID).equals(entity.getStatus())) {
            return ResponseDTO.ok();
        }
        TraceInspectEntity updateEntity = new TraceInspectEntity();
        updateEntity.setInspectId(inspectId);
        updateEntity.setStatus(STATUS_INVALID);
        traceInspectManager.update(updateEntity);
        return ResponseDTO.ok();
    }

    /**
     * 删除检测报告（逻辑删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> delete(Long inspectId) {
        traceInspectDao.batchUpdateDeleted(Collections.singletonList(inspectId), Boolean.TRUE);
        return ResponseDTO.ok();
    }

    /**
     * 批量删除检测报告（逻辑删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> batchDelete(ValidateList<Long> idList) {
        traceInspectDao.batchUpdateDeleted(idList, Boolean.TRUE);
        return ResponseDTO.ok();
    }
}
