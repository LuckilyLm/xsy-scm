package com.xsy.scm.admin.module.business.order.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.admin.module.business.order.domain.entity.SaleRefundEntity;
import com.xsy.scm.admin.module.business.order.domain.form.SaleRefundQueryForm;
import com.xsy.scm.admin.module.business.order.domain.vo.SaleRefundVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 销售退款单 Dao
 *
 * @author xsy-scm
 */
@Mapper
public interface SaleRefundDao extends BaseMapper<SaleRefundEntity> {

    /**
     * 根据订单查询退款单
     */
    List<SaleRefundEntity> queryByOrderId(@Param("orderId") Long orderId);

    /**
     * 分页查询退款单
     */
    List<SaleRefundVO> queryPage(Page page, @Param("queryForm") SaleRefundQueryForm queryForm);

    /**
     * 回填退款单号
     */
    void updateRefundNo(@Param("refundId") Long refundId, @Param("refundNo") String refundNo);

    /**
     * 批量更新删除状态（业务上应走状态冲销，本方法仅供异常数据清理）
     */
    void batchUpdateDeleted(@Param("refundIdList") List<Long> refundIdList,
                            @Param("deletedFlag") Boolean deletedFlag);
}
