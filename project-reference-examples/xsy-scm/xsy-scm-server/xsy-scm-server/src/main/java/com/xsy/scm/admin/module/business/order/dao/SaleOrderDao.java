package com.xsy.scm.admin.module.business.order.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.admin.module.business.order.domain.entity.SaleOrderEntity;
import com.xsy.scm.admin.module.business.order.domain.form.SaleOrderQueryForm;
import com.xsy.scm.admin.module.business.order.domain.vo.SaleOrderVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 销售订单 Dao
 *
 * @author xsy-scm
 */
@Mapper
public interface SaleOrderDao extends BaseMapper<SaleOrderEntity> {

    /**
     * 分页查询销售订单
     */
    List<SaleOrderVO> queryPage(Page page, @Param("queryForm") SaleOrderQueryForm queryForm);

    /**
     * 批量更新删除状态
     *
     * <p>订单业务上应走「取消 / 作废」状态流转，本方法仅供草稿类数据的后台清理使用。</p>
     */
    void batchUpdateDeleted(@Param("orderIdList") List<Long> orderIdList,
                            @Param("deletedFlag") Boolean deletedFlag);
}
