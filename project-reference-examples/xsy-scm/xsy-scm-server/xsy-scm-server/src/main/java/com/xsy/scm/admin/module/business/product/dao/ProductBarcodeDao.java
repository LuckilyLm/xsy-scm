package com.xsy.scm.admin.module.business.product.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.admin.module.business.product.domain.entity.ProductBarcodeEntity;
import com.xsy.scm.admin.module.business.product.domain.form.ProductBarcodeQueryForm;
import com.xsy.scm.admin.module.business.product.domain.vo.ProductBarcodeVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 商品条码 Dao
 *
 * @author xsy-scm
 */
@Mapper
public interface ProductBarcodeDao extends BaseMapper<ProductBarcodeEntity> {

    /**
     * 分页查询商品条码
     */
    List<ProductBarcodeVO> queryPage(Page page, @Param("queryForm") ProductBarcodeQueryForm queryForm);

    /**
     * 按条码查询（扫码入口 + 唯一性校验）
     */
    ProductBarcodeEntity getByBarcode(@Param("barcode") String barcode);

    /**
     * 批量更新删除状态
     */
    void batchUpdateDeleted(@Param("idList") List<Long> idList,
                            @Param("deletedFlag") Boolean deletedFlag);
}
