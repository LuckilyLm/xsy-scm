package com.xsy.scm.admin.module.business.stock.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.admin.module.business.stock.domain.entity.StockCheckEntity;
import com.xsy.scm.admin.module.business.stock.domain.form.StockCheckQueryForm;
import com.xsy.scm.admin.module.business.stock.domain.vo.StockCheckVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 库存盘点单 Dao
 *
 * @author xsy-scm
 */
@Mapper
public interface StockCheckDao extends BaseMapper<StockCheckEntity> {

    /**
     * 分页查询盘点单
     */
    List<StockCheckVO> queryPage(Page page, @Param("queryForm") StockCheckQueryForm queryForm);

    /**
     * 回填盘点单号
     */
    void updateCheckNo(@Param("checkId") Long checkId, @Param("checkNo") String checkNo);

    /**
     * 批量更新删除状态
     */
    void batchUpdateDeleted(@Param("checkIdList") List<Long> checkIdList,
                            @Param("deletedFlag") Boolean deletedFlag);
}
