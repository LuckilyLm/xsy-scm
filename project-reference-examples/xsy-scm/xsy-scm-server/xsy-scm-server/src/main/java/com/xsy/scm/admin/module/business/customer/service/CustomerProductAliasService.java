package com.xsy.scm.admin.module.business.customer.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.admin.module.business.customer.dao.CustomerProductAliasDao;
import com.xsy.scm.admin.module.business.customer.domain.entity.CustomerProductAliasEntity;
import com.xsy.scm.admin.module.business.customer.domain.form.CustomerProductAliasAddForm;
import com.xsy.scm.admin.module.business.customer.domain.form.CustomerProductAliasQueryForm;
import com.xsy.scm.admin.module.business.customer.domain.form.CustomerProductAliasUpdateForm;
import com.xsy.scm.admin.module.business.customer.domain.vo.CustomerProductAliasVO;
import com.xsy.scm.admin.module.business.customer.manager.CustomerProductAliasManager;
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
 * 客户商品别名 Service
 *
 * <p>对标蔬东坡 16.3 / 17.4：仅影响展示，不影响取价与库存。</p>
 *
 * @author xsy-scm
 */
@Service
@Slf4j
public class CustomerProductAliasService {

    @Resource
    private CustomerProductAliasDao customerProductAliasDao;

    @Resource
    private CustomerProductAliasManager customerProductAliasManager;

    /**
     * 分页查询客户商品别名
     */
    public ResponseDTO<PageResult<CustomerProductAliasVO>> query(CustomerProductAliasQueryForm queryForm) {
        queryForm.setDeletedFlag(Boolean.FALSE);
        Page<?> page = SmartPageUtil.convert2PageQuery(queryForm);
        List<CustomerProductAliasVO> list = customerProductAliasDao.queryPage(page, queryForm);
        return ResponseDTO.ok(SmartPageUtil.convert2PageResult(page, list));
    }

    /**
     * 新增客户商品别名（同客户 + 商品 + 规格唯一）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> add(CustomerProductAliasAddForm addForm) {
        CustomerProductAliasEntity existEntity = customerProductAliasDao.getByCustomerProductSku(
                addForm.getCustomerId(), addForm.getProductId(), addForm.getSkuId());
        if (existEntity != null) {
            return ResponseDTO.userErrorParam("该客户商品别名已存在");
        }
        CustomerProductAliasEntity entity = SmartBeanUtil.copy(addForm, CustomerProductAliasEntity.class);
        entity.setDeletedFlag(Boolean.FALSE);
        customerProductAliasManager.save(entity);
        return ResponseDTO.ok();
    }

    /**
     * 更新客户商品别名
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> update(CustomerProductAliasUpdateForm updateForm) {
        CustomerProductAliasEntity existEntity = customerProductAliasDao.getByCustomerProductSku(
                updateForm.getCustomerId(), updateForm.getProductId(), updateForm.getSkuId());
        if (existEntity != null && !existEntity.getAliasId().equals(updateForm.getAliasId())) {
            return ResponseDTO.userErrorParam("该客户商品别名已存在");
        }
        CustomerProductAliasEntity entity = SmartBeanUtil.copy(updateForm, CustomerProductAliasEntity.class);
        customerProductAliasManager.update(entity);
        return ResponseDTO.ok();
    }

    /**
     * 删除客户商品别名（逻辑删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> delete(Long aliasId) {
        customerProductAliasDao.batchUpdateDeleted(Collections.singletonList(aliasId), Boolean.TRUE);
        return ResponseDTO.ok();
    }

    /**
     * 批量删除客户商品别名（逻辑删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> batchDelete(ValidateList<Long> idList) {
        customerProductAliasDao.batchUpdateDeleted(idList, Boolean.TRUE);
        return ResponseDTO.ok();
    }
}
