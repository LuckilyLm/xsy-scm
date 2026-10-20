package com.xsy.scm.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.xsy.scm.common.ScmW5PgITBase;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.pricing.domain.form.TimePriceForm;
import com.xsy.scm.pricing.domain.form.TimePriceQueryForm;
import com.xsy.scm.pricing.domain.vo.TimePriceVO;
import com.xsy.scm.pricing.service.TimePriceService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 时价的写入约束：区间半开、同规格不重叠。
 *
 * <p>
 * 重叠会让「现在按哪条算」没有唯一答案，所以这里重点验证重叠被拒，而不是价格数值本身。
 */
class TimePricePgIT extends ScmW5PgITBase {

    @Autowired
    private TimePriceService timePriceService;

    @Test
    @DisplayName("#1 新增时价后可查询，生效中状态按当前时刻判定")
    void createAndQuery() {
        Long skuId = newOnShelfSku("TP1");
        OffsetDateTime now = OffsetDateTime.now();
        TimePriceVO created = timePriceService.create(
                form(skuId, "9.9000", now.minusHours(1), now.plusHours(1), "临调"), "W9 IT");

        assertThat(created.getId()).isNotNull();
        assertThat(created.getUnitPrice()).isEqualByComparingTo("9.9000");

        TimePriceQueryForm query = new TimePriceQueryForm();
        query.setPageNum(1L);
        query.setPageSize(20L);
        query.setSkuId(skuId);
        var page = timePriceService.query(query);
        assertThat(page.getList()).hasSize(1);
        assertThat(page.getList().getFirst().getEffectiveNow()).isTrue();

        // 时价必须留审计：谁在什么时候把哪个规格的临时价改成了多少
        Integer logs = jdbc.queryForObject(
                "SELECT count(*) FROM xsy_pricing_time_price_operation_log WHERE time_price_id = ? "
                        + "AND operation_type = 'CREATE'", Integer.class, created.getId());
        assertThat(logs).as("新增时价要写一条 CREATE 审计").isEqualTo(1);
    }

    @Test
    @DisplayName("#2 同规格区间重叠被拒（40052），区间倒置被拒（40051）")
    void rejectsOverlapAndInvalidWindow() {
        Long skuId = newOnShelfSku("TP2");
        OffsetDateTime now = OffsetDateTime.now();
        timePriceService.create(form(skuId, "5.0000", now.minusHours(2), now.plusHours(2), null), "W9 IT");

        assertThatThrownBy(() -> timePriceService.create(
                form(skuId, "6.0000", now.plusHours(1), now.plusHours(3), null), "W9 IT"))
                .isInstanceOf(ScmBusinessException.class)
                .satisfies(error -> assertThat(((ScmBusinessException) error).getErrorCode().getCode())
                        .isEqualTo(40052));

        assertThatThrownBy(() -> timePriceService.create(
                form(skuId, "6.0000", now.plusHours(5), now.plusHours(4), null), "W9 IT"))
                .isInstanceOf(ScmBusinessException.class)
                .satisfies(error -> assertThat(((ScmBusinessException) error).getErrorCode().getCode())
                        .isEqualTo(40051));

        // 首尾相接不算重叠：半开区间 [start, end)
        TimePriceVO adjacent = timePriceService.create(
                form(skuId, "7.0000", now.plusHours(2), now.plusHours(4), null), "W9 IT");
        assertThat(adjacent.getId()).isNotNull();
    }

    private TimePriceForm form(Long skuId, String price, OffsetDateTime startAt, OffsetDateTime endAt,
            String remark) {
        TimePriceForm form = new TimePriceForm();
        form.setSkuId(skuId);
        form.setUnitPrice(new BigDecimal(price));
        form.setStartAt(startAt);
        form.setEndAt(endAt);
        form.setRemark(remark);
        return form;
    }
}
