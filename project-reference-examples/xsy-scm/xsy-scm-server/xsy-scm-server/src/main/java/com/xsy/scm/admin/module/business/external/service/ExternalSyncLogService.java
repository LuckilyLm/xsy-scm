package com.xsy.scm.admin.module.business.external.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.admin.module.business.external.constant.ExternalSyncStatusEnum;
import com.xsy.scm.admin.module.business.external.dao.ExternalSyncLogDao;
import com.xsy.scm.admin.module.business.external.domain.entity.ExternalSyncLogEntity;
import com.xsy.scm.admin.module.business.external.domain.form.ExternalSyncLogAddForm;
import com.xsy.scm.admin.module.business.external.domain.form.ExternalSyncLogQueryForm;
import com.xsy.scm.admin.module.business.external.domain.vo.ExternalSyncLogVO;
import com.xsy.scm.admin.module.business.external.manager.ExternalSyncLogManager;
import com.xsy.scm.base.common.domain.PageResult;
import com.xsy.scm.base.common.domain.ResponseDTO;
import com.xsy.scm.base.common.domain.ValidateList;
import com.xsy.scm.base.common.util.SmartBeanUtil;
import com.xsy.scm.base.common.util.SmartPageUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * 外部平台同步日志 Service
 *
 * <p>对标蔬东坡 17.3 / 17.4：上报 / 拉取结果留痕，失败可重试（重试只递增次数并回到「待同步」，
 * 保留原始失败记录以便审计）。</p>
 *
 * @author xsy-scm
 */
@Service
@Slf4j
public class ExternalSyncLogService {

    @Resource
    private ExternalSyncLogDao externalSyncLogDao;

    @Resource
    private ExternalSyncLogManager externalSyncLogManager;

    /**
     * 分页查询同步日志
     */
    public ResponseDTO<PageResult<ExternalSyncLogVO>> query(ExternalSyncLogQueryForm queryForm) {
        queryForm.setDeletedFlag(Boolean.FALSE);
        Page<?> page = SmartPageUtil.convert2PageQuery(queryForm);
        List<ExternalSyncLogVO> list = externalSyncLogDao.queryPage(page, queryForm);
        return ResponseDTO.ok(SmartPageUtil.convert2PageResult(page, list));
    }

    /**
     * 登记同步日志（对接执行器调用）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> add(ExternalSyncLogAddForm addForm) {
        ExternalSyncLogEntity entity = SmartBeanUtil.copy(addForm, ExternalSyncLogEntity.class);
        entity.setRetryCount(0);
        if (entity.getSyncTime() == null) {
            entity.setSyncTime(LocalDateTime.now());
        }
        entity.setDeletedFlag(Boolean.FALSE);
        externalSyncLogManager.save(entity);
        return ResponseDTO.ok();
    }

    /**
     * 重试：仅失败日志可重试，回到「待同步」并递增重试次数（幂等）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> retry(Long logId) {
        ExternalSyncLogEntity entity = externalSyncLogDao.selectById(logId);
        if (entity == null || Boolean.TRUE.equals(entity.getDeletedFlag())) {
            return ResponseDTO.userErrorParam("同步日志不存在");
        }
        if (ExternalSyncStatusEnum.PENDING.getValue().equals(entity.getSyncStatus())) {
            return ResponseDTO.okMsg("该日志已在待同步队列中");
        }
        if (!ExternalSyncStatusEnum.FAILED.getValue().equals(entity.getSyncStatus())) {
            return ResponseDTO.userErrorParam("仅失败的同步日志可重试");
        }
        ExternalSyncLogEntity updateEntity = new ExternalSyncLogEntity();
        updateEntity.setLogId(logId);
        updateEntity.setSyncStatus(ExternalSyncStatusEnum.PENDING.getValue());
        updateEntity.setRetryCount((entity.getRetryCount() == null ? 0 : entity.getRetryCount()) + 1);
        externalSyncLogManager.update(updateEntity);
        return ResponseDTO.okMsg("已重新加入同步队列");
    }

    /**
     * 删除同步日志（逻辑删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> delete(Long logId) {
        externalSyncLogDao.batchUpdateDeleted(Collections.singletonList(logId), Boolean.TRUE);
        return ResponseDTO.ok();
    }

    /**
     * 批量删除同步日志（逻辑删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> batchDelete(ValidateList<Long> idList) {
        externalSyncLogDao.batchUpdateDeleted(idList, Boolean.TRUE);
        return ResponseDTO.ok();
    }
}
