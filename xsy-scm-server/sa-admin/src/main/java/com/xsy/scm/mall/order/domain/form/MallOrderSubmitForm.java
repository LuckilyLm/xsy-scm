package com.xsy.scm.mall.order.domain.form;

import com.xsy.scm.common.validation.ScmEnumValue;
import com.xsy.scm.mall.order.constant.ScmSettlementMethodEnum;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

@Data
public class MallOrderSubmitForm {
    @Valid
    @NotEmpty(message = "请选择要结算的商品")
    private List<MallCheckoutItemForm> items;
    @NotNull(message = "请选择收货地址")
    private Long addressId;
    /** 提交时必须是该客户配送仓选项之一；由订单服务冻结。 */
    @Positive(message = "配送仓必须大于0")
    private Long warehouseId;
    /**
     * 结算预览返回的价格指纹。提交时服务端按当前价格重算，不一致即拒绝，避免按旧价静默下单。
     */
    @NotBlank(message = "缺少价格指纹，请重新结算")
    private String priceFingerprint;
    @Size(max = 500, message = "订单备注不能超过500个字符")
    private String remark;
    /** 期望配送日期 yyyy-MM-dd，可空；服务端按当日 00:00（Asia/Shanghai）落库 */
    @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "期望配送日期格式应为 yyyy-MM-dd")
    private String expectDeliveryDate;
    /**
     * 客户预选的券实例。提交时服务端重验归属、状态与门槛后随订单持久化； 正式占用（冻结）仍在订单确认命令内完成，预览与预选都不占用券。
     */
    @Positive(message = "券实例 ID 必须大于0")
    private Long couponInstanceId;
    /**
     * 新下单必须主动选定结算方式。此字段保持可空以便旧请求用同一幂等键重放成功订单；新请求会在幂等预检后拒绝空值。
     */
    @ScmEnumValue(enumClass = ScmSettlementMethodEnum.class, message = "结算方式无效")
    private String settlementMethod;
}
