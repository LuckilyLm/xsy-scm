package com.xsy.scm.admin.module.business.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.admin.module.business.product.dao.ProductSkuDao;
import com.xsy.scm.admin.module.business.product.domain.entity.ProductSkuEntity;
import com.xsy.scm.admin.module.business.product.domain.form.ProductSkuAddForm;
import com.xsy.scm.admin.module.business.product.domain.form.ProductSkuQueryForm;
import com.xsy.scm.admin.module.business.product.domain.form.ProductSkuUpdateForm;
import com.xsy.scm.admin.module.business.product.domain.vo.ProductSkuVO;
import com.xsy.scm.admin.module.business.product.manager.ProductSkuManager;
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
 * 商品规格 SKU Service
 *
 * @author xsy-scm
 */
@Service
@Slf4j
public class ProductSkuService {

    @Resource
    private ProductSkuDao skuDao;

    @Resource
    private ProductSkuManager skuManager;

    /**
     * 分页查询商品规格（按商品维度）
     */
    public ResponseDTO<PageResult<ProductSkuVO>> query(ProductSkuQueryForm queryForm) {
        queryForm.setDeletedFlag(Boolean.FALSE);
        Page<?> page = SmartPageUtil.convert2PageQuery(queryForm);
        List<ProductSkuVO> list = skuDao.queryPage(page, queryForm);
        return ResponseDTO.ok(SmartPageUtil.convert2PageResult(page, list));
    }

    /**
     * 新增商品规格，规格编码取 SKU + 自增主键
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> add(ProductSkuAddForm addForm) {
        ProductSkuEntity entity = SmartBeanUtil.copy(addForm, ProductSkuEntity.class);
        entity.setDeletedFlag(Boolean.FALSE);
        skuManager.save(entity);
        skuDao.updateSkuNo(entity.getSkuId(), "SKU" + entity.getSkuId());
        return ResponseDTO.ok();
    }

    /**
     * 更新商品规格
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> update(ProductSkuUpdateForm updateForm) {
        ProductSkuEntity entity = SmartBeanUtil.copy(updateForm, ProductSkuEntity.class);
        skuManager.update(entity);
        return ResponseDTO.ok();
    }

    /**
     * 删除商品规格（逻辑删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> delete(Long skuId) {
        skuDao.batchUpdateDeleted(Collections.singletonList(skuId), Boolean.TRUE);
        return ResponseDTO.ok();
    }

    /**
     * 批量删除商品规格（逻辑删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> batchDelete(ValidateList<Long> idList) {
        skuDao.batchUpdateDeleted(idList, Boolean.TRUE);
        return ResponseDTO.ok();
    }

    /**
     * 查询所有未删除商品规格（用于下拉选择）
     */
    public ResponseDTO<List<ProductSkuVO>> queryAll() {
        LambdaQueryWrapper<ProductSkuEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProductSkuEntity::getDeletedFlag, Boolean.FALSE);
        wrapper.orderByAsc(ProductSkuEntity::getSkuId);
        List<ProductSkuEntity> list = skuDao.selectList(wrapper);
        return ResponseDTO.ok(SmartBeanUtil.copyList(list, ProductSkuVO.class));
    }
}
