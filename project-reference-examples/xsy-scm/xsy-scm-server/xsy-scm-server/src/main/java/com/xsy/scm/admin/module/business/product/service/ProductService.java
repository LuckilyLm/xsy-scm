package com.xsy.scm.admin.module.business.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.admin.module.business.product.dao.ProductDao;
import com.xsy.scm.admin.module.business.product.domain.entity.ProductEntity;
import com.xsy.scm.admin.module.business.product.domain.form.ProductAddForm;
import com.xsy.scm.admin.module.business.product.domain.form.ProductQueryForm;
import com.xsy.scm.admin.module.business.product.domain.form.ProductUpdateForm;
import com.xsy.scm.admin.module.business.product.domain.vo.ProductVO;
import com.xsy.scm.admin.module.business.product.manager.ProductManager;
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

import java.util.Collections;
import java.util.List;

/**
 * 商品 Service
 *
 * @author xsy-scm
 */
@Service
@Slf4j
public class ProductService {

    @Resource
    private ProductDao productDao;

    @Resource
    private ProductManager productManager;

    @Resource
    private SerialNumberService serialNumberService;

    /**
     * 分页查询商品
     */
    public ResponseDTO<PageResult<ProductVO>> query(ProductQueryForm queryForm) {
        queryForm.setDeletedFlag(Boolean.FALSE);
        Page<?> page = SmartPageUtil.convert2PageQuery(queryForm);
        List<ProductVO> list = productDao.queryPage(page, queryForm);
        PageResult<ProductVO> pageResult = SmartPageUtil.convert2PageResult(page, list);
        return ResponseDTO.ok(pageResult);
    }

    /**
     * 新增商品
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> add(ProductAddForm addForm) {
        ProductEntity productEntity = SmartBeanUtil.copy(addForm, ProductEntity.class);
        productEntity.setProductNo(serialNumberService.generate(SerialNumberIdEnum.PRODUCT));
        productEntity.setDeletedFlag(Boolean.FALSE);
        productManager.save(productEntity);
        return ResponseDTO.ok();
    }

    /**
     * 更新商品
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> update(ProductUpdateForm updateForm) {
        ProductEntity productEntity = SmartBeanUtil.copy(updateForm, ProductEntity.class);
        productManager.update(productEntity);
        return ResponseDTO.ok();
    }

    /**
     * 删除商品（逻辑删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> delete(Long productId) {
        productDao.batchUpdateDeleted(Collections.singletonList(productId), Boolean.TRUE);
        return ResponseDTO.ok();
    }

    /**
     * 批量删除商品（逻辑删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> batchDelete(ValidateList<Long> idList) {
        productDao.batchUpdateDeleted(idList, Boolean.TRUE);
        return ResponseDTO.ok();
    }

    /**
     * 查询所有未删除商品（用于下拉选择）
     */
    public ResponseDTO<List<ProductVO>> queryAll() {
        LambdaQueryWrapper<ProductEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProductEntity::getDeletedFlag, Boolean.FALSE);
        wrapper.orderByAsc(ProductEntity::getProductId);
        List<ProductEntity> list = productDao.selectList(wrapper);
        return ResponseDTO.ok(SmartBeanUtil.copyList(list, ProductVO.class));
    }
}
