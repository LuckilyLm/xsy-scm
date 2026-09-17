package com.xsy.scm.admin.module.business.finance.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.admin.module.business.finance.dao.ExternalConfigDao;
import com.xsy.scm.admin.module.business.finance.domain.entity.ExternalConfigEntity;
import com.xsy.scm.admin.module.business.finance.domain.form.ExternalConfigAddForm;
import com.xsy.scm.admin.module.business.finance.domain.form.ExternalConfigQueryForm;
import com.xsy.scm.admin.module.business.finance.domain.form.ExternalConfigUpdateForm;
import com.xsy.scm.admin.module.business.finance.domain.vo.ExternalConfigVO;
import com.xsy.scm.admin.module.business.finance.manager.ExternalConfigManager;
import com.xsy.scm.base.common.domain.PageResult;
import com.xsy.scm.base.common.domain.ResponseDTO;
import com.xsy.scm.base.common.domain.ValidateList;
import com.xsy.scm.base.common.util.SmartBeanUtil;
import com.xsy.scm.base.common.util.SmartPageUtil;
import com.xsy.scm.base.common.util.SmartStringUtil;
import com.xsy.scm.base.module.support.apiencrypt.service.ApiEncryptService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

/**
 * 外部系统配置 Service
 *
 * <p>密钥（appSecret）使用项目统一加解密能力加密存储，**不返回前端、不打印日志**。</p>
 *
 * @author xsy-scm
 */
@Service
@Slf4j
public class ExternalConfigService {

    @Resource
    private ExternalConfigDao externalConfigDao;

    @Resource
    private ExternalConfigManager externalConfigManager;

    @Resource
    private ApiEncryptService apiEncryptService;

    /**
     * 分页查询外部系统配置（不返回密钥，仅返回是否已配置）
     */
    public ResponseDTO<PageResult<ExternalConfigVO>> query(ExternalConfigQueryForm queryForm) {
        queryForm.setDeletedFlag(Boolean.FALSE);
        Page<?> page = SmartPageUtil.convert2PageQuery(queryForm);
        List<ExternalConfigVO> list = externalConfigDao.queryPage(page, queryForm);
        return ResponseDTO.ok(SmartPageUtil.convert2PageResult(page, list));
    }

    /**
     * 新增外部系统配置（密钥加密存储）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> add(ExternalConfigAddForm addForm) {
        ExternalConfigEntity entity = SmartBeanUtil.copy(addForm, ExternalConfigEntity.class);
        entity.setAppSecret(encryptSecret(addForm.getAppSecret()));
        if (entity.getStatus() == null) {
            entity.setStatus(1);
        }
        entity.setDeletedFlag(Boolean.FALSE);
        externalConfigManager.save(entity);
        return ResponseDTO.ok();
    }

    /**
     * 更新外部系统配置：密钥留空表示不修改
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> update(ExternalConfigUpdateForm updateForm) {
        ExternalConfigEntity entity = SmartBeanUtil.copy(updateForm, ExternalConfigEntity.class);
        if (SmartStringUtil.isNotBlank(updateForm.getAppSecret())) {
            entity.setAppSecret(encryptSecret(updateForm.getAppSecret()));
        } else {
            entity.setAppSecret(null);
        }
        externalConfigManager.update(entity);
        return ResponseDTO.ok();
    }

    /**
     * 删除外部系统配置（逻辑删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> delete(Long configId) {
        externalConfigDao.batchUpdateDeleted(Collections.singletonList(configId), Boolean.TRUE);
        return ResponseDTO.ok();
    }

    /**
     * 批量删除外部系统配置（逻辑删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> batchDelete(ValidateList<Long> idList) {
        externalConfigDao.batchUpdateDeleted(idList, Boolean.TRUE);
        return ResponseDTO.ok();
    }

    /**
     * 加密密钥（空白密钥原样返回）
     */
    private String encryptSecret(String secret) {
        if (SmartStringUtil.isBlank(secret)) {
            return secret;
        }
        return apiEncryptService.encrypt(secret);
    }
}
