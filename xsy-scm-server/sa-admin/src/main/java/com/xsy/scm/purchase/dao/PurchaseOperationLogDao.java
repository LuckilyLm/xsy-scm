package com.xsy.scm.purchase.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xsy.scm.purchase.domain.entity.PurchaseOperationLogEntity;
import com.xsy.scm.purchase.domain.vo.PurchaseOperationLogVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 采购操作日志，只追加不修改。
 *
 * <p>
 * 日志归属规则由 DB 的 {@code ck_purchase_operation_log_owner} 强制，本 Dao 不做任何归属推断 —— 它只负责把调用方算好的 {@code purchase_order_id} /
 * {@code purchase_receipt_id} 原样写入。 {@code DEMAND_GENERATE} 时两者都是 {@code null}；{@code DEMAND_ALLOCATE} 由调用方用
 * {@code purchaseOrderItemId} <b>反查</b> {@code purchase_order_id} 后写入。
 *
 * <p>
 * 刻意没有 update / delete 方法：日志不可篡改，与 {@code OrderOperationLogDao} 一致。 日志没有 {@code deleted} 列，所以查询不需要补
 * {@code WHERE deleted = FALSE}。
 */
@Mapper
public interface PurchaseOperationLogDao extends BaseMapper<PurchaseOperationLogEntity> {

    /**
     * 追加一条操作日志。
     */
    int append(@Param("row") PurchaseOperationLogEntity row);

    /**
     * 某采购单的全部日志，按时间倒序（{@code GET /scm/purchase/log/{orderId}}）。
     */
    List<PurchaseOperationLogVO> listByOrderId(@Param("purchaseOrderId") Long purchaseOrderId);

    /**
     * 某收货单的全部日志，按时间倒序。
     */
    List<PurchaseOperationLogVO> listByReceiptId(@Param("purchaseReceiptId") Long purchaseReceiptId);

    /**
     * 某需求生成批次的相关日志（按 {@code after_data.demandIds} 反查；验收与排查用）。
     */
    List<PurchaseOperationLogVO> listDemandGenerate();

    /**
     * 按操作类型统计条数（验收断言 12 种类型全部落库）。
     */
    int countByType(@Param("operationType") String operationType);
}
