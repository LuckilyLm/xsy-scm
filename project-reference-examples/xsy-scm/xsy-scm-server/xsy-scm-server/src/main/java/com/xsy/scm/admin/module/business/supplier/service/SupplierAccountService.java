package com.xsy.scm.admin.module.business.supplier.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.admin.module.business.supplier.constant.SupplierAccountStatusEnum;
import com.xsy.scm.admin.module.business.supplier.dao.SupplierAccountDao;
import com.xsy.scm.admin.module.business.supplier.domain.entity.SupplierAccountEntity;
import com.xsy.scm.admin.module.business.supplier.domain.form.SupplierAccountAddForm;
import com.xsy.scm.admin.module.business.supplier.domain.form.SupplierAccountQueryForm;
import com.xsy.scm.admin.module.business.supplier.domain.form.SupplierAccountUpdateForm;
import com.xsy.scm.admin.module.business.supplier.domain.vo.SupplierAccountVO;
import com.xsy.scm.admin.module.business.supplier.manager.SupplierAccountManager;
import com.xsy.scm.base.common.domain.PageResult;
import com.xsy.scm.base.common.domain.ResponseDTO;
import com.xsy.scm.base.common.domain.ValidateList;
import com.xsy.scm.base.common.util.SmartBeanUtil;
import com.xsy.scm.base.common.util.SmartPageUtil;
import com.xsy.scm.base.common.util.SmartStringUtil;
import com.xsy.scm.base.module.support.securityprotect.service.SecurityPasswordService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

/**
 * 供应商账号 Service
 *
 * @author xsy-scm
 */
@Service
@Slf4j
public class SupplierAccountService {

    @Resource
    private SupplierAccountDao supplierAccountDao;

    @Resource
    private SupplierAccountManager supplierAccountManager;

    /**
     * 分页查询供应商账号
     */
    public ResponseDTO<PageResult<SupplierAccountVO>> query(SupplierAccountQueryForm queryForm) {
        queryForm.setDeletedFlag(Boolean.FALSE);
        Page<?> page = SmartPageUtil.convert2PageQuery(queryForm);
        List<SupplierAccountVO> list = supplierAccountDao.queryPage(page, queryForm);
        return ResponseDTO.ok(SmartPageUtil.convert2PageResult(page, list));
    }

    /**
     * 新增供应商账号（密码加密存储）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> add(SupplierAccountAddForm addForm) {
        SupplierAccountEntity existEntity = supplierAccountDao.getByAccount(addForm.getAccount());
        if (existEntity != null) {
            return ResponseDTO.userErrorParam("登录账号已存在");
        }
        SupplierAccountEntity entity = SmartBeanUtil.copy(addForm, SupplierAccountEntity.class);
        entity.setPassword(SecurityPasswordService.getEncryptPwd(addForm.getPassword()));
        entity.setLoginVersion(0);
        if (entity.getStatus() == null) {
            entity.setStatus(SupplierAccountStatusEnum.ENABLED.getValue());
        }
        entity.setDeletedFlag(Boolean.FALSE);
        supplierAccountManager.save(entity);
        return ResponseDTO.ok();
    }

    /**
     * 更新供应商账号
     *
     * <p>密码变更时重新加密，并递增 {@code loginVersion} 使历史登录态失效（蔬东坡 17.4）。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> update(SupplierAccountUpdateForm updateForm) {
        SupplierAccountEntity entity = SmartBeanUtil.copy(updateForm, SupplierAccountEntity.class);
        if (SmartStringUtil.isNotBlank(updateForm.getPassword())) {
            entity.setPassword(SecurityPasswordService.getEncryptPwd(updateForm.getPassword()));
            entity.setLoginVersion(currentLoginVersion(updateForm.getAccountId()) + 1);
        } else {
            // 未修改密码时不覆盖原密码与登录态版本
            entity.setPassword(null);
            entity.setLoginVersion(null);
        }
        supplierAccountManager.update(entity);
        return ResponseDTO.ok();
    }

    /**
     * 删除供应商账号（逻辑删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> delete(Long accountId) {
        supplierAccountDao.batchUpdateDeleted(Collections.singletonList(accountId), Boolean.TRUE);
        return ResponseDTO.ok();
    }

    /**
     * 批量删除供应商账号（逻辑删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> batchDelete(ValidateList<Long> idList) {
        supplierAccountDao.batchUpdateDeleted(idList, Boolean.TRUE);
        return ResponseDTO.ok();
    }

    /**
     * 取当前登录态版本号（不存在时为 0）
     */
    private Integer currentLoginVersion(Long accountId) {
        SupplierAccountEntity entity = supplierAccountDao.selectById(accountId);
        if (entity == null || entity.getLoginVersion() == null) {
            return 0;
        }
        return entity.getLoginVersion();
    }
}
