package com.xsy.scm.admin.module.business.stock.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xsy.scm.admin.module.business.stock.domain.entity.ProductConvertItemEntity;
import com.xsy.scm.admin.module.business.stock.domain.vo.ProductConvertItemVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 商品转换明细 Dao
 *
 * @author xsy-scm
 */
@Mapper
public interface ProductConvertItemDao extends BaseMapper<ProductConvertItemEntity> {

    /**
     * 查询转换单下的明细
     */
    List<ProductConvertItemVO> listByConvertId(@Param("convertId") Long convertId);

    /**
     * 逻辑删除转换单下的全部明细
     */
    void batchUpdateDeletedByConvertId(@Param("convertId") Long convertId);
}
