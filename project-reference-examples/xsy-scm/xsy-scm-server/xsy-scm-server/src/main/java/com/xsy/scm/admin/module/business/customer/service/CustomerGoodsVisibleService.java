package com.xsy.scm.admin.module.business.customer.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.admin.module.business.customer.dao.CustomerGoodsVisibleDao;
import com.xsy.scm.admin.module.business.customer.domain.entity.CustomerGoodsVisibleEntity;
import com.xsy.scm.admin.module.business.customer.domain.form.CustomerGoodsVisibleAddForm;
import com.xsy.scm.admin.module.business.customer.domain.form.CustomerGoodsVisibleQueryForm;
import com.xsy.scm.admin.module.business.customer.domain.form.CustomerGoodsVisibleUpdateForm;
import com.xsy.scm.admin.module.business.customer.domain.vo.CustomerGoodsVisibleVO;
import com.xsy.scm.admin.module.business.customer.manager.CustomerGoodsVisibleManager;
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
 * 客户商品可见性 Service
 *
 * @author xsy-scm
 */
@Service
@Slf4j
public class CustomerGoodsVisibleService {

    @Resource
    private CustomerGoodsVisibleDao goodsVisibleDao;

    @Resource
    private CustomerGoodsVisibleManager goodsVisibleManager;

    /**
     * 分页查询客户商品可见性（按客户维度）
     */
    public ResponseDTO<PageResult<CustomerGoodsVisibleVO>> query(CustomerGoodsVisibleQueryForm queryForm) {
        queryForm.setDeletedFlag(Boolean.FALSE);
        Page<?> page = SmartPageUtil.convert2PageQuery(queryForm);
        List<CustomerGoodsVisibleVO> list = goodsVisibleDao.queryPage(page, queryForm);
        return ResponseDTO.ok(SmartPageUtil.convert2PageResult(page, list));
    }

    /**
     * 新增客户商品可见性
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> add(CustomerGoodsVisibleAddForm addForm) {
        CustomerGoodsVisibleEntity entity = SmartBeanUtil.copy(addForm, CustomerGoodsVisibleEntity.class);
        entity.setDeletedFlag(Boolean.FALSE);
        goodsVisibleManager.save(entity);
        return ResponseDTO.ok();
    }

    /**
     * 更新客户商品可见性
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> update(CustomerGoodsVisibleUpdateForm updateForm) {
        CustomerGoodsVisibleEntity entity = SmartBeanUtil.copy(updateForm, CustomerGoodsVisibleEntity.class);
        goodsVisibleManager.update(entity);
        return ResponseDTO.ok();
    }

    /**
     * 删除客户商品可见性（逻辑删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> delete(Long id) {
        goodsVisibleDao.batchUpdateDeleted(Collections.singletonList(id), Boolean.TRUE);
        return ResponseDTO.ok();
    }

    /**
     * 批量删除客户商品可见性（逻辑删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> batchDelete(ValidateList<Long> idList) {
        goodsVisibleDao.batchUpdateDeleted(idList, Boolean.TRUE);
        return ResponseDTO.ok();
    }
}
