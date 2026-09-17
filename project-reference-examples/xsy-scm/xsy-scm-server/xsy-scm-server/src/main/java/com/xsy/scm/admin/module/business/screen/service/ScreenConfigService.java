package com.xsy.scm.admin.module.business.screen.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.admin.module.business.screen.constant.ScreenStatusEnum;
import com.xsy.scm.admin.module.business.screen.dao.ScreenConfigDao;
import com.xsy.scm.admin.module.business.screen.domain.entity.ScreenConfigEntity;
import com.xsy.scm.admin.module.business.screen.domain.form.ScreenConfigAddForm;
import com.xsy.scm.admin.module.business.screen.domain.form.ScreenConfigQueryForm;
import com.xsy.scm.admin.module.business.screen.domain.form.ScreenConfigUpdateForm;
import com.xsy.scm.admin.module.business.screen.domain.vo.ScreenConfigVO;
import com.xsy.scm.admin.module.business.screen.manager.ScreenConfigManager;
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
 * 数据大屏配置 Service
 *
 * @author xsy-scm
 */
@Service
@Slf4j
public class ScreenConfigService {

    @Resource
    private ScreenConfigDao screenConfigDao;

    @Resource
    private ScreenConfigManager screenConfigManager;

    /**
     * 分页查询大屏配置
     */
    public ResponseDTO<PageResult<ScreenConfigVO>> query(ScreenConfigQueryForm queryForm) {
        queryForm.setDeletedFlag(Boolean.FALSE);
        Page<?> page = SmartPageUtil.convert2PageQuery(queryForm);
        List<ScreenConfigVO> list = screenConfigDao.queryPage(page, queryForm);
        return ResponseDTO.ok(SmartPageUtil.convert2PageResult(page, list));
    }

    /**
     * 新增大屏配置
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> add(ScreenConfigAddForm addForm) {
        ScreenConfigEntity entity = SmartBeanUtil.copy(addForm, ScreenConfigEntity.class);
        if (entity.getStatus() == null) {
            entity.setStatus(ScreenStatusEnum.ENABLED.getValue());
        }
        entity.setDeletedFlag(Boolean.FALSE);
        screenConfigManager.save(entity);
        return ResponseDTO.ok();
    }

    /**
     * 更新大屏配置
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> update(ScreenConfigUpdateForm updateForm) {
        ScreenConfigEntity entity = SmartBeanUtil.copy(updateForm, ScreenConfigEntity.class);
        screenConfigManager.update(entity);
        return ResponseDTO.ok();
    }

    /**
     * 删除大屏配置（逻辑删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> delete(Long screenId) {
        screenConfigDao.batchUpdateDeleted(Collections.singletonList(screenId), Boolean.TRUE);
        return ResponseDTO.ok();
    }

    /**
     * 批量删除大屏配置（逻辑删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> batchDelete(ValidateList<Long> idList) {
        screenConfigDao.batchUpdateDeleted(idList, Boolean.TRUE);
        return ResponseDTO.ok();
    }
}
