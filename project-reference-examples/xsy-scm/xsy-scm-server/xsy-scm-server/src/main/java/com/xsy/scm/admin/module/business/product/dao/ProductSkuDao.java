package com.xsy.scm.admin.module.business.product.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.admin.module.business.product.domain.entity.ProductSkuEntity;
import com.xsy.scm.admin.module.business.product.domain.form.ProductSkuQueryForm;
import com.xsy.scm.admin.module.business.product.domain.vo.ProductSkuVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 商品规格 SKU Dao
 *
 * @author xsy-scm
 */
@Mapper
public interface ProductSkuDao extends BaseMapper<ProductSkuEntity> {

    /**
     * 分页查询商品规格
     */
    List<ProductSkuVO> queryPage(Page page, @Param("queryForm") ProductSkuQueryForm queryForm);

    /**
     * 回填规格编码
     */
    void updateSkuNo(@Param("skuId") Long skuId, @Param("skuNo") String skuNo);

    /**
     * 批量更新删除状态
     */
    void batchUpdateDeleted(@Param("skuIdList") List<Long> skuIdList,
                            @Param("deletedFlag") Boolean deletedFlag);
}
