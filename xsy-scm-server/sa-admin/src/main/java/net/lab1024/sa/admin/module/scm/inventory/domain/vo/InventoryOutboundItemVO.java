package net.lab1024.sa.admin.module.scm.inventory.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Data;
import net.lab1024.sa.admin.module.scm.common.json.ScmFixedScale4Serializer;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 出库单明细行（独立 VO，供明细接口与 mapper 复用）。
 *
 * <p>与 {@link InventoryOutboundVO.Item} 字段一致，但**不复用同一个类**：
 * 头内嵌明细是「详情的组成部分」，独立接口返回的是「一行的投影」，
 * 两者的演进理由不同（前者跟随头、后者跟随行）。用同名字段保持前端一致即可。
 */
@Data
public class InventoryOutboundItemVO {

    private Long id;

    private Long outboundId;

    private Long skuId;

    private String skuCode;

    private String skuName;

    private String productName;

    private Map<String, String> specValues;

    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal quantity;

    private String unitSnapshot;

    private String remark;
}
