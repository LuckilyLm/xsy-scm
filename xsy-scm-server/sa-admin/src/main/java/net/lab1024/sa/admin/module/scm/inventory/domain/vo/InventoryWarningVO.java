package net.lab1024.sa.admin.module.scm.inventory.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Data;
import net.lab1024.sa.admin.module.scm.common.json.ScmFixedScale4Serializer;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 库存预警列表的一行（= 一条阈值配置 + 它对应的余额）。
 *
 * <p><b>现有量 / 预留量 / 可用量三个都返回</b>：判定基准是**可用量**
 * （现有量 − 预留量），只给一个数字会让用户看不懂预警为什么触发 ——
 * 「明明有 20 kg 在库，为什么说低于下限 10 kg？」的答案是那 20 kg 里有 18 kg 已预留。
 *
 * <p><b>{@code status} 是派生值</b>（{@code NORMAL / LOW / HIGH}），由
 * {@code ScmInventoryWarningStatusEnum#evaluate} 在服务层按可用量与阈值算出，不落库。
 *
 * <p>配置了阈值但**没有余额行**时，三个数量字段都返回 0（SQL 用 {@code COALESCE}）——
 * 那正是「设了下限却一件没有」，应当预警；这也是本能力唯一能表达
 * 「还没进过货就要补货」的方式。
 */
@Data
public class InventoryWarningVO {

    /** 阈值配置 id（跳转到配置页用）。 */
    private Long thresholdId;

    private Long warehouseId;

    private String warehouseCode;

    private String warehouseName;

    private Long skuId;

    private String skuCode;

    private String skuName;

    private String productName;

    private Map<String, String> specValues;

    /** Q13 记账单位；没有余额行时为空。 */
    private String unit;

    /** 现有量；没有余额行时为 0。 */
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal quantity;

    /** 已预留量；没有余额行时为 0。 */
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal reservedQuantity;

    /** 可用量 = 现有量 − 预留量。**判定基准**。 */
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal availableQuantity;

    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal warnMin;

    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal warnMax;

    /** {@code NORMAL} / {@code LOW} / {@code HIGH}（服务层按可用量算出）。 */
    private String status;

    /** 状态中文描述（服务层按枚举填充，前端不硬编码字典）。 */
    private String statusDesc;
}
