package net.lab1024.sa.admin.module.scm.sorting.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.lab1024.sa.admin.module.scm.common.json.ScmFixedScale4Serializer;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 分拣任务明细：一条订单行在本任务里的一次分拣。
 *
 * <p>计划量是建单时冻结的快照，永不回写订单行的 {@code actual_quantity} 与结算金额；
 * 分拣量与结果成对出现（库里 {@code ck_sorting_task_item_processed_pairing} 强制），
 * 因此「未处理」与「已处理」是仅有的两态，不存在有量无结果的中间状态。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sorting_task_item")
public class SortingTaskItemEntity extends SortingRecord {
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long taskId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long salesOrderId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long salesOrderItemId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String orderNoSnapshot;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long customerId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String customerNameSnapshot;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long spuId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long skuId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String spuCodeSnapshot;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String productNameSnapshot;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String skuCodeSnapshot;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String specNameSnapshot;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String saleUnitSnapshot;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String productTypeSnapshot;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal plannedQuantitySnapshot;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal sortedQuantity;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String result;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String reason;
    private String sortedBy;
    private OffsetDateTime sortedAt;
    /**
     * {@code ACTIVE} 占用该订单行；任务取消时同一事务内置 {@code RELEASED}，行保留作历史。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String occupationStatus;
}
