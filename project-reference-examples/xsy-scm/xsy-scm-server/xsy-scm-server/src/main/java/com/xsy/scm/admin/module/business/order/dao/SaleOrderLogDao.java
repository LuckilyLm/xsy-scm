package com.xsy.scm.admin.module.business.order.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.admin.module.business.order.domain.entity.SaleOrderLogEntity;
import com.xsy.scm.admin.module.business.order.domain.form.SaleOrderLogQueryForm;
import com.xsy.scm.admin.module.business.order.domain.vo.SaleOrderLogVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 订单操作日志 Dao
 *
 * <p>日志类数据不做逻辑删除，因此不提供 batchUpdateDeleted。</p>
 *
 * @author xsy-scm
 */
@Mapper
public interface SaleOrderLogDao extends BaseMapper<SaleOrderLogEntity> {

    /**
     * 根据订单查询操作日志（订单操作日志显示）
     */
    List<SaleOrderLogEntity> queryByOrderId(@Param("orderId") Long orderId);

    /**
     * 分页查询订单操作日志
     */
    List<SaleOrderLogVO> queryPage(Page page, @Param("queryForm") SaleOrderLogQueryForm queryForm);
}
