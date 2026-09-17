package com.xsy.scm.admin.module.business.stock.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.admin.module.business.stock.domain.entity.StockCheckItemEntity;
import com.xsy.scm.admin.module.business.stock.domain.form.StockCheckItemQueryForm;
import com.xsy.scm.admin.module.business.stock.domain.vo.StockCheckItemVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 库存盘点明细 Dao
 *
 * @author xsy-scm
 */
@Mapper
public interface StockCheckItemDao extends BaseMapper<StockCheckItemEntity> {

    /**
     * 根据盘点单查询明细
     */
    List<StockCheckItemEntity> queryByCheckId(@Param("checkId") Long checkId);

    /**
     * 分页查询盘点明细
     */
    List<StockCheckItemVO> queryPage(Page page, @Param("queryForm") StockCheckItemQueryForm queryForm);

    /**
     * 批量更新删除状态
     */
    void batchUpdateDeleted(@Param("itemIdList") List<Long> itemIdList,
                            @Param("deletedFlag") Boolean deletedFlag);
}
