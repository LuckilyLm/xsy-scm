package net.lab1024.sa.admin.module.scm.purchase.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import net.lab1024.sa.admin.module.scm.purchase.domain.entity.PurchaseOperationLogEntity;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseOperationLogVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 采购操作日志（W5 Target Design §5.10 / §7.12，**只追加**）。
 *
 * <p>**Q14 的归属规则由 DB 的 `ck_purchase_operation_log_owner` 强制**，
 * 本 Dao 不做任何归属推断 —— 它只负责把调用方算好的
 * `purchase_order_id` / `purchase_receipt_id` 原样写入。
 * `DEMAND_GENERATE` 时两者都是 `null`；`DEMAND_ALLOCATE` 由调用方用
 * `purchaseOrderItemId` **反查** `purchase_order_id` 后写入。
 *
 * <p>**刻意没有 update / delete 方法**：日志不可篡改（与 W4 的
 * {@code OrderOperationLogDao} 一致）。日志**没有** `deleted` 列，
 * 所以不需要 `WHERE deleted = FALSE`。
 */
@Mapper
public interface PurchaseOperationLogDao extends BaseMapper<PurchaseOperationLogEntity> {

    /** 追加一条操作日志。 */
    int append(@Param("row") PurchaseOperationLogEntity row);

    /** 某采购单的全部日志，按时间倒序（`GET /scm/purchase/log/{orderId}`）。 */
    List<PurchaseOperationLogVO> listByOrderId(@Param("purchaseOrderId") Long purchaseOrderId);

    /** 某收货单的全部日志，按时间倒序。 */
    List<PurchaseOperationLogVO> listByReceiptId(@Param("purchaseReceiptId") Long purchaseReceiptId);

    /** 某需求生成批次的相关日志（按 `after_data.demandIds` 反查；验收与排查用）。 */
    List<PurchaseOperationLogVO> listDemandGenerate();

    /** 按操作类型统计条数（验收断言 12 种类型全部落库）。 */
    int countByType(@Param("operationType") String operationType);
}
