package com.xsy.scm.admin.module.business.stock.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.admin.module.business.stock.domain.entity.ProductConvertEntity;
import com.xsy.scm.admin.module.business.stock.domain.form.ProductConvertQueryForm;
import com.xsy.scm.admin.module.business.stock.domain.vo.ProductConvertVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 商品转换单 Dao
 *
 * @author xsy-scm
 */
@Mapper
public interface ProductConvertDao extends BaseMapper<ProductConvertEntity> {

    /**
     * 分页查询商品转换单
     */
    List<ProductConvertVO> queryPage(Page page, @Param("queryForm") ProductConvertQueryForm queryForm);

    /**
     * 批量更新删除状态
     */
    void batchUpdateDeleted(@Param("idList") List<Long> idList,
                            @Param("deletedFlag") Boolean deletedFlag);
}
