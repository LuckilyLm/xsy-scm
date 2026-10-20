package com.xsy.scm.pricing;

import static org.assertj.core.api.Assertions.assertThat;

import com.xsy.scm.common.constant.ScmSettleModeEnum;
import com.xsy.scm.pricing.constant.ScmPriceSourceEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 展示名的单一事实源：导出等后端输出与前端文案共用这套口径。
 *
 * <p>
 * 未知值必须原样返回而不是抛错——导出不能因为一条脏数据整份失败。
 */
class ScmPriceSourceLabelTest {

    @Test
    @DisplayName("每一种价格来源都有中文展示名")
    void everySourceHasLabel() {
        for (ScmPriceSourceEnum source : ScmPriceSourceEnum.values()) {
            assertThat(source.getLabel()).as(source.name()).isNotBlank();
        }
        assertThat(ScmPriceSourceEnum.AGREEMENT.getLabel()).isEqualTo("客户协议价");
        assertThat(ScmPriceSourceEnum.CUSTOMER_TYPE.getLabel()).isEqualTo("客户类型价");
        assertThat(ScmPriceSourceEnum.TIME_PRICE.getLabel()).isEqualTo("时价");
        assertThat(ScmPriceSourceEnum.MARKET.getLabel()).isEqualTo("市场价");
    }

    @Test
    @DisplayName("枚举名转展示名；未知值原样返回，空值透传")
    void labelOfTranslatesAndPassthrough() {
        assertThat(ScmPriceSourceEnum.labelOf("TIME_PRICE")).isEqualTo("时价");
        assertThat(ScmPriceSourceEnum.labelOf("NOT_A_SOURCE")).isEqualTo("NOT_A_SOURCE");
        assertThat(ScmPriceSourceEnum.labelOf(null)).isNull();
        assertThat(ScmSettleModeEnum.labelOf("GROUP")).isEqualTo("集团结算");
        assertThat(ScmSettleModeEnum.labelOf("INDEPENDENT")).isEqualTo("独立结算");
    }
}
