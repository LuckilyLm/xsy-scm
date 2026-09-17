package com.xsy.scm.admin.module.business.product.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.admin.module.business.product.dao.ProductBarcodeDao;
import com.xsy.scm.admin.module.business.product.domain.entity.ProductBarcodeEntity;
import com.xsy.scm.admin.module.business.product.domain.form.ProductBarcodeAddForm;
import com.xsy.scm.admin.module.business.product.domain.form.ProductBarcodeQueryForm;
import com.xsy.scm.admin.module.business.product.domain.form.ProductBarcodeUpdateForm;
import com.xsy.scm.admin.module.business.product.domain.vo.ProductBarcodeVO;
import com.xsy.scm.admin.module.business.product.manager.ProductBarcodeManager;
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
 * 商品条码 Service（扫码作业基础数据）
 *
 * <p>对标蔬东坡 17.4：条码录入、盘点 / 收货 / 分拣扫码。</p>
 *
 * @author xsy-scm
 */
@Service
@Slf4j
public class ProductBarcodeService {

    @Resource
    private ProductBarcodeDao productBarcodeDao;

    @Resource
    private ProductBarcodeManager productBarcodeManager;

    /**
     * 分页查询商品条码
     */
    public ResponseDTO<PageResult<ProductBarcodeVO>> query(ProductBarcodeQueryForm queryForm) {
        queryForm.setDeletedFlag(Boolean.FALSE);
        Page<?> page = SmartPageUtil.convert2PageQuery(queryForm);
        List<ProductBarcodeVO> list = productBarcodeDao.queryPage(page, queryForm);
        return ResponseDTO.ok(SmartPageUtil.convert2PageResult(page, list));
    }

    /**
     * 扫码：按条码查询商品（扫码作业入口）
     */
    public ResponseDTO<ProductBarcodeVO> getByBarcode(String barcode) {
        ProductBarcodeEntity entity = productBarcodeDao.getByBarcode(barcode);
        if (entity == null) {
            return ResponseDTO.userErrorParam("条码不存在");
        }
        return ResponseDTO.ok(SmartBeanUtil.copy(entity, ProductBarcodeVO.class));
    }

    /**
     * 新增商品条码（条码全局唯一）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> add(ProductBarcodeAddForm addForm) {
        ProductBarcodeEntity existEntity = productBarcodeDao.getByBarcode(addForm.getBarcode());
        if (existEntity != null) {
            return ResponseDTO.userErrorParam("条形码已存在");
        }
        ProductBarcodeEntity entity = SmartBeanUtil.copy(addForm, ProductBarcodeEntity.class);
        entity.setDeletedFlag(Boolean.FALSE);
        productBarcodeManager.save(entity);
        return ResponseDTO.ok();
    }

    /**
     * 更新商品条码
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> update(ProductBarcodeUpdateForm updateForm) {
        ProductBarcodeEntity existEntity = productBarcodeDao.getByBarcode(updateForm.getBarcode());
        if (existEntity != null && !existEntity.getBarcodeId().equals(updateForm.getBarcodeId())) {
            return ResponseDTO.userErrorParam("条形码已存在");
        }
        ProductBarcodeEntity entity = SmartBeanUtil.copy(updateForm, ProductBarcodeEntity.class);
        productBarcodeManager.update(entity);
        return ResponseDTO.ok();
    }

    /**
     * 删除商品条码（逻辑删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> delete(Long barcodeId) {
        productBarcodeDao.batchUpdateDeleted(Collections.singletonList(barcodeId), Boolean.TRUE);
        return ResponseDTO.ok();
    }

    /**
     * 批量删除商品条码（逻辑删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> batchDelete(ValidateList<Long> idList) {
        productBarcodeDao.batchUpdateDeleted(idList, Boolean.TRUE);
        return ResponseDTO.ok();
    }
}
