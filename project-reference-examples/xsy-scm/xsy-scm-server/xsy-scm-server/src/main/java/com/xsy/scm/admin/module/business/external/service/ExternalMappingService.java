package com.xsy.scm.admin.module.business.external.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.admin.module.business.external.constant.MappingStatusEnum;
import com.xsy.scm.admin.module.business.external.dao.ExternalMappingDao;
import com.xsy.scm.admin.module.business.external.domain.entity.ExternalMappingEntity;
import com.xsy.scm.admin.module.business.external.domain.form.ExternalMappingAddForm;
import com.xsy.scm.admin.module.business.external.domain.form.ExternalMappingImportForm;
import com.xsy.scm.admin.module.business.external.domain.form.ExternalMappingItemForm;
import com.xsy.scm.admin.module.business.external.domain.form.ExternalMappingQueryForm;
import com.xsy.scm.admin.module.business.external.domain.form.ExternalMappingUpdateForm;
import com.xsy.scm.admin.module.business.external.domain.vo.ExternalMappingVO;
import com.xsy.scm.admin.module.business.external.manager.ExternalMappingManager;
import com.xsy.scm.base.common.domain.PageResult;
import com.xsy.scm.base.common.domain.ResponseDTO;
import com.xsy.scm.base.common.domain.ValidateList;
import com.xsy.scm.base.common.util.SmartBeanUtil;
import com.xsy.scm.base.common.util.SmartPageUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/**
 * 外部平台映射 Service
 *
 * <p>对标蔬东坡 17.3 / 17.4：商品 / 客户 / 供应商映射，支持一对多与单位转换系数；
 * 映射本地可维护、可导入导出。</p>
 *
 * @author xsy-scm
 */
@Service
@Slf4j
public class ExternalMappingService {

    @Resource
    private ExternalMappingDao externalMappingDao;

    @Resource
    private ExternalMappingManager externalMappingManager;

    /**
     * 分页查询映射
     */
    public ResponseDTO<PageResult<ExternalMappingVO>> query(ExternalMappingQueryForm queryForm) {
        queryForm.setDeletedFlag(Boolean.FALSE);
        Page<?> page = SmartPageUtil.convert2PageQuery(queryForm);
        List<ExternalMappingVO> list = externalMappingDao.queryPage(page, queryForm);
        return ResponseDTO.ok(SmartPageUtil.convert2PageResult(page, list));
    }

    /**
     * 导出映射（按条件全量返回，交后台管理生成文件）
     */
    public ResponseDTO<List<ExternalMappingVO>> export(ExternalMappingQueryForm queryForm) {
        queryForm.setDeletedFlag(Boolean.FALSE);
        return ResponseDTO.ok(externalMappingDao.listByCondition(queryForm));
    }

    /**
     * 新增映射（同平台 + 同对象 + 同系统ID + 同外部ID 视为重复）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> add(ExternalMappingAddForm addForm) {
        Long count = externalMappingDao.countByMapping(addForm.getSystemType(), addForm.getBizType(),
                addForm.getLocalId(), addForm.getExternalId());
        if (count != null && count > 0) {
            return ResponseDTO.userErrorParam("该映射已存在");
        }
        ExternalMappingEntity entity = buildEntity(addForm);
        externalMappingManager.save(entity);
        return ResponseDTO.ok();
    }

    /**
     * 批量导入映射（已存在的自动跳过，返回成功 / 跳过条数）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> importMappings(ExternalMappingImportForm importForm) {
        int success = 0;
        int skip = 0;
        for (ExternalMappingItemForm item : importForm.getItems()) {
            Long count = externalMappingDao.countByMapping(importForm.getSystemType(), importForm.getBizType(),
                    item.getLocalId(), item.getExternalId());
            if (count != null && count > 0) {
                skip++;
                continue;
            }
            ExternalMappingEntity entity = new ExternalMappingEntity();
            entity.setSystemType(importForm.getSystemType());
            entity.setBizType(importForm.getBizType());
            entity.setLocalId(item.getLocalId());
            entity.setExternalId(item.getExternalId());
            entity.setConvertRatio(defaultRatio(item.getConvertRatio()));
            entity.setStatus(MappingStatusEnum.ENABLED.getValue());
            entity.setDeletedFlag(Boolean.FALSE);
            externalMappingManager.save(entity);
            success++;
        }
        return ResponseDTO.okMsg("导入成功 " + success + " 条，跳过重复 " + skip + " 条");
    }

    /**
     * 更新映射
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> update(ExternalMappingUpdateForm updateForm) {
        ExternalMappingEntity entity = buildEntity(updateForm);
        entity.setMappingId(updateForm.getMappingId());
        externalMappingManager.update(entity);
        return ResponseDTO.ok();
    }

    /**
     * 删除映射（逻辑删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> delete(Long mappingId) {
        externalMappingDao.batchUpdateDeleted(Collections.singletonList(mappingId), Boolean.TRUE);
        return ResponseDTO.ok();
    }

    /**
     * 批量删除映射（逻辑删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> batchDelete(ValidateList<Long> idList) {
        externalMappingDao.batchUpdateDeleted(idList, Boolean.TRUE);
        return ResponseDTO.ok();
    }

    private ExternalMappingEntity buildEntity(ExternalMappingAddForm addForm) {
        ExternalMappingEntity entity = SmartBeanUtil.copy(addForm, ExternalMappingEntity.class);
        entity.setConvertRatio(defaultRatio(addForm.getConvertRatio()));
        if (entity.getStatus() == null) {
            entity.setStatus(MappingStatusEnum.ENABLED.getValue());
        }
        entity.setDeletedFlag(Boolean.FALSE);
        return entity;
    }

    /**
     * 转换系数未填默认 1
     */
    private BigDecimal defaultRatio(BigDecimal convertRatio) {
        return convertRatio == null ? BigDecimal.ONE : convertRatio;
    }
}
