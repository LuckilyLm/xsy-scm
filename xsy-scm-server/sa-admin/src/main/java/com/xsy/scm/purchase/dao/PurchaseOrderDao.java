package com.xsy.scm.purchase.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.common.scope.ScmValueScope;
import com.xsy.scm.purchase.domain.entity.PurchaseOrderEntity;
import com.xsy.scm.purchase.domain.form.PurchaseOrderQueryForm;
import com.xsy.scm.purchase.domain.vo.PurchaseOrderVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 采购单。
 *
 * <p>{@link #nextOrderNo()} 取全局序列，**不按日 reset**：
 * 单号形如 {@code PO + yyyyMMdd + 至少 6 位}，超过 999999 自然扩位，
 * 由 {@code PurchaseNumberGenerator} 负责拼接与补零。
 */
@Mapper
public interface PurchaseOrderDao extends BaseMapper<PurchaseOrderEntity> {

    /**
     * 分页查询（联 supplier / warehouse / 收货进度）。
     *
     * <p>{@code scope} 是采购员维度的授权范围，由 Service 显式下传：
     * {@code null} 在 Mapper 里按失败关闭处理（0 行），不表示「全部」；
     * {@code detail} / {@code lock} 刻意不带范围，读取范围只在查询端点判定，命令侧由写权限把关。
     */
    List<PurchaseOrderVO> query(Page<?> page, @Param("query") PurchaseOrderQueryForm query,
                                @Param("scope") ScmValueScope scope);

    /**
     * 详情（单头，联名称与进度）。
     */
    PurchaseOrderVO detail(@Param("id") Long id);

    /**
     * 单条 `FOR UPDATE`（锁序：purchase_demand → purchase_order → purchase_order_item）。
     */
    PurchaseOrderEntity lock(@Param("id") Long id);

    /**
     * 全局单调递增的采购单号序列（不按日 reset）。
     */
    Long nextOrderNo();

    /**
     * 软删（仅 DRAFT，由 Service 断言）。
     */
    int softDelete(@Param("id") Long id,
                   @Param("version") Integer version,
                   @Param("operator") String operator);
}
