package net.lab1024.sa.admin.module.scm.purchase.manager;

import net.lab1024.sa.admin.module.scm.common.constant.ScmOperator;
import net.lab1024.sa.admin.module.scm.purchase.domain.entity.PurchaseDemandAllocationEntity;
import net.lab1024.sa.admin.module.scm.purchase.domain.entity.PurchaseOrderEntity;
import net.lab1024.sa.admin.module.scm.purchase.domain.entity.PurchaseOrderItemEntity;

import java.time.OffsetDateTime;

/**
 * 采购单、采购行和分配行的审计字段赋值。
 * 新建行与分配记录复位 version/deleted；单据头由 PurchaseSnapshotFactory 初始化。
 */
public final class PurchaseEntityStamper {

    private PurchaseEntityStamper() {
    }

    /** 单据头：只打审计字段，不复位 version / deleted（由构造方设置）。 */
    public static void stamp(PurchaseOrderEntity row, boolean creating) {
        OffsetDateTime now = OffsetDateTime.now();
        row.setUpdatedAt(now);
        row.setUpdatedBy(ScmOperator.current());
        if (creating) {
            row.setCreatedAt(now);
            row.setCreatedBy(row.getUpdatedBy());
        }
    }

    /** 单据行：审计字段 + 新建时复位 version / deleted。 */
    public static void stamp(PurchaseOrderItemEntity row, boolean creating) {
        OffsetDateTime now = OffsetDateTime.now();
        row.setUpdatedAt(now);
        row.setUpdatedBy(ScmOperator.current());
        if (creating) {
            row.setVersion(0);
            row.setDeleted(false);
            row.setCreatedAt(now);
            row.setCreatedBy(row.getUpdatedBy());
        }
    }

    /** 分配行：审计字段 + 新建时复位 version / deleted。 */
    public static void stamp(PurchaseDemandAllocationEntity row, boolean creating) {
        OffsetDateTime now = OffsetDateTime.now();
        row.setUpdatedAt(now);
        row.setUpdatedBy(ScmOperator.current());
        if (creating) {
            row.setVersion(0);
            row.setDeleted(false);
            row.setCreatedAt(now);
            row.setCreatedBy(row.getUpdatedBy());
        }
    }
}
