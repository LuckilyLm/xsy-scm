package com.xsy.scm.admin.module.business.order.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.admin.module.business.order.domain.entity.SaleOrderItemEntity;
import com.xsy.scm.admin.module.business.order.domain.form.SaleOrderItemQueryForm;
import com.xsy.scm.admin.module.business.order.domain.vo.SaleOrderItemVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

/**
 * 销售订单明细 Dao
 *
 * @author xsy-scm
 */
@Mapper
public interface SaleOrderItemDao extends BaseMapper<SaleOrderItemEntity> {

    /**
     * 根据订单查询明细
     */
    List<SaleOrderItemEntity> queryByOrderId(@Param("orderId") Long orderId);

    /**
     * 分页查询订单明细
     */
    List<SaleOrderItemVO> queryPage(Page page, @Param("queryForm") SaleOrderItemQueryForm queryForm);

    /**
     * 批量更新删除状态
     */
    void batchUpdateDeleted(@Param("itemIdList") List<Long> itemIdList,
                            @Param("deletedFlag") Boolean deletedFlag);

    /**
     * 统计订单明细核算金额合计（不含税）
     */
    BigDecimal sumItemAmount(@Param("orderId") Long orderId);
}
