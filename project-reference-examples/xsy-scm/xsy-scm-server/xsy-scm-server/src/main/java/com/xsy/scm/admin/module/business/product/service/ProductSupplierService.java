package com.xsy.scm.admin.module.business.product.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.admin.module.business.product.dao.ProductSupplierDao;
import com.xsy.scm.admin.module.business.product.domain.entity.ProductSupplierEntity;
import com.xsy.scm.admin.module.business.product.domain.form.ProductSupplierAddForm;
import com.xsy.scm.admin.module.business.product.domain.form.ProductSupplierQueryForm;
import com.xsy.scm.admin.module.business.product.domain.form.ProductSupplierUpdateForm;
import com.xsy.scm.admin.module.business.product.domain.vo.ProductSupplierVO;
import com.xsy.scm.admin.module.business.product.manager.ProductSupplierManager;
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
 * 商品-供应商关系 Service
 *
 * @author xsy-scm
 */
@Service
@Slf4j
public class ProductSupplierService {

    @Resource
    private ProductSupplierDao productSupplierDao;

    @Resource
    private ProductSupplierManager productSupplierManager;

    /**
     * 分页查询商品-供应商关系（按商品维度）
     */
    public ResponseDTO<PageResult<ProductSupplierVO>> query(ProductSupplierQueryForm queryForm) {
        queryForm.setDeletedFlag(Boolean.FALSE);
        Page<?> page = SmartPageUtil.convert2PageQuery(queryForm);
        List<ProductSupplierVO> list = productSupplierDao.queryPage(page, queryForm);
        return ResponseDTO.ok(SmartPageUtil.convert2PageResult(page, list));
    }

    /**
     * 新增商品-供应商关系
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> add(ProductSupplierAddForm addForm) {
        ProductSupplierEntity entity = SmartBeanUtil.copy(addForm, ProductSupplierEntity.class);
        entity.setDeletedFlag(Boolean.FALSE);
        productSupplierManager.save(entity);
        return ResponseDTO.ok();
    }

    /**
     * 更新商品-供应商关系
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> update(ProductSupplierUpdateForm updateForm) {
        ProductSupplierEntity entity = SmartBeanUtil.copy(updateForm, ProductSupplierEntity.class);
        productSupplierManager.update(entity);
        return ResponseDTO.ok();
    }

    /**
     * 删除商品-供应商关系（逻辑删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> delete(Long id) {
        productSupplierDao.batchUpdateDeleted(Collections.singletonList(id), Boolean.TRUE);
        return ResponseDTO.ok();
    }

    /**
     * 批量删除商品-供应商关系（逻辑删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> batchDelete(ValidateList<Long> idList) {
        productSupplierDao.batchUpdateDeleted(idList, Boolean.TRUE);
        return ResponseDTO.ok();
    }
}
