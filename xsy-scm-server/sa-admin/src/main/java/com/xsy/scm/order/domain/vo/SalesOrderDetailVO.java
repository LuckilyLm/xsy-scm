package com.xsy.scm.order.domain.vo;

import com.xsy.scm.promotion.domain.vo.OrderDiscountVO;
import com.xsy.scm.promotion.domain.vo.PromotionDiscountVO;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class SalesOrderDetailVO extends SalesOrderVO {
    private List<SalesOrderItemVO> items;
    private OrderAddressSnapshotVO address;

    /**
     * 已冻结的订单优惠；没有优惠时为 {@code null}。
     *
     * <p>
     * 订单金额列仍是结算口径（不含优惠），优惠作为**独立事实**返回：把两者合成一个「净额」字段
     * 会让「原价多少、减了多少」无法回答，退款反向也就没有可对账的依据。
     */
    private OrderDiscountVO discount;

    /**
     * 满赠赠品权益；没有赠品时为空列表。
     *
     * <p>
     * 与 {@link #discount} 分开：赠品是**非金额权益**，只有赠品没有金额优惠的订单不会有优惠行，
     * 混在一起会让这种订单看起来「什么都没有」。
     */
    private List<PromotionDiscountVO.GiftEntitlementVO> gifts;
}
