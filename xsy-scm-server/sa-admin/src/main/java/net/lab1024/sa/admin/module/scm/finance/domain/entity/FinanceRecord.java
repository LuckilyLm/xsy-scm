package net.lab1024.sa.admin.module.scm.finance.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 财务七张事实表的公共审计列，字段口径与 {@code SortingRecord} / {@code DeliveryRecord} 一致。
 *
 * <p><b>刻意不带 {@code @TableLogic}</b>：七张表都有 DB 约束 {@code ck_finance_*_append_only
 * CHECK (deleted = FALSE)}（V65），软删历史财务事实在数据库层直接失败。{@code @TableLogic}
 * 表达的是「可被软删的实体」，与财务事实的语义正好相反 —— 挂上它，MyBatis-Plus 的
 * {@code deleteById} 会生成 {@code UPDATE ... SET deleted = TRUE} 并被 CHECK 打回，
 * 报错信息指向一个与业务无关的约束。这里沿用 {@code InventoryMovementEntity} 的同一取舍：
 * 读取一律在 SQL 里显式写 {@code deleted = FALSE}（与部分唯一索引的谓词同口径），不靠框架注入。
 *
 * <p>{@code deleted} 列本身保留，只为让部分唯一索引的 {@code WHERE deleted = FALSE} 谓词
 * 与本仓库既有形态逐字一致。纠错一律走 {@code entry_type = 'REVERSE'/'RED'} 的反向事实，
 * 见 docs/decisions.md「P3 Finance R1 裁决」全局不变量 1/2 与第三批 D-3。
 *
 * <p>{@code finance_operation_log} 不继承本类：它连 {@code deleted} 与 {@code version} 都没有，
 * append-only 是结构性的（没有任何删除或修改入口）。
 */
@Data
public abstract class FinanceRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    @Version
    private Integer version = 0;
    private Boolean deleted = false;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
}
