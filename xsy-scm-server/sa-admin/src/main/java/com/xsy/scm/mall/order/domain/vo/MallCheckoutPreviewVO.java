package com.xsy.scm.mall.order.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;
import com.xsy.scm.mall.address.domain.vo.MallAddressVO;

import java.math.BigDecimal;
import java.util.List;
import java.util.ArrayList;

import lombok.Data;

/**
 * 结算预览。金额一律由服务端按四位小数定点计算，客户端只展示。
 */
@Data
public class MallCheckoutPreviewVO {
    private Long warehouseId;
    private String warehouseName;
    private boolean warehouseRequired;
    private List<MallWarehouseOptionVO> warehouses = new ArrayList<>();
    private List<MallCheckoutItemVO> items = new ArrayList<>();
    /**
     * 当前选中的收货地址；未选择时为 null
     */
    private MallAddressVO address;
    /**
     * 可结算行的预估金额合计；非标品最终以实重结算
     */
    @JsonSerialize(using = ScmFixedScale4Serializer.class)
    private BigDecimal estimatedAmount = BigDecimal.ZERO;
    private Integer availableCount = 0;
    private Integer totalCount = 0;
    /**
     * 不可结算的 SKU，供前端提示
     */
    private List<Long> unavailableSkuIds = new ArrayList<>();
    /**
     * 提交时必须原样回传；服务端在提交时按当前价格重算比对
     */
    private String priceFingerprint;
    /**
     * 优惠试算结果；试算失败（如券不可用）时为 null，由 promotionError 说明
     */
    private MallCheckoutPromotionVO promotion;
    /**
     * 优惠试算失败原因；商品与金额照常返回，优惠不生效
     */
    private String promotionError;
    /**
     * 可选结算方式（余额 / 账期 / 货到付款）及其实时可用性；只读投影，真正校验在下单与确认时。
     */
    private List<MallSettlementOptionVO> settlementOptions = new ArrayList<>();
}
