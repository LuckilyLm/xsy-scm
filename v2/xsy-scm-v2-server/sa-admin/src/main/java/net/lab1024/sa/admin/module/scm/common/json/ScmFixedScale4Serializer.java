package net.lab1024.sa.admin.module.scm.common.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * SCM 数量与金额的定点数序列化器（4 位小数，字符串形式）。
 *
 * <p><b>为什么不能使用 SmartAdmin 的 {@code BigDecimalNullZeroSerializer}：</b>
 * 后者把 {@code null} 写成数字 {@code 0}。SCM 业务中 {@code null} 承载真实语义——
 * 例如 {@code PriceStatus.UNPRICED}（请求时点没有任何有效价格来源）时
 * {@code unitPrice} 必须为空，而不是"价格为 0 元"。把两者混为一谈会导致：
 * <ul>
 *   <li>缺价商品在商城中显示为 0 元并可下单；</li>
 *   <li>订单金额被错误计算为 0；</li>
 *   <li>前端无法区分"未定价"与"定价为零"。</li>
 * </ul>
 *
 * <p><b>使用方式（重要）：</b>
 * Jackson 的 {@code @JsonSerialize(using = X)} <b>不会</b>作用于 null 值，只有
 * {@code nullsUsing = X} 才会。因此 SCM 字段必须同时声明两者，否则 null 的最终表现
 * 取决于全局 NullValueSerializer，容易在配置变动后失守：
 * <pre>
 *   &#64;JsonSerialize(using = ScmFixedScale4Serializer.class,
 *                   nullsUsing = ScmFixedScale4Serializer.class)
 *   private BigDecimal unitPrice;
 * </pre>
 *
 * <p><b>本序列化器的契约：</b>
 * <pre>
 *   null            -> JSON null          （保留"无值/未定价"语义）
 *   BigDecimal 0    -> "0.0000"           （字符串，4 位定点）
 *   12.345678       -> "12.3457"          （HALF_UP）
 * </pre>
 *
 * <p>使用字符串而非 JSON 数字，是因为前端按字符串做定点运算与格式化，
 * JSON 数字会丢精度。该约定与 legacy SCM 的 {@code FixedScale4Serializer} 一致。
 *
 * @see net.lab1024.sa.base.common.json.serializer.BigDecimalNullZeroSerializer
 */
public class ScmFixedScale4Serializer extends JsonSerializer<BigDecimal> {

    /**
     * 金额与数量统一保留的小数位数。
     */
    public static final int SCALE = 4;

    @Override
    public void serialize(BigDecimal value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (value == null) {
            // 关键：写 null，绝不能写成 0
            gen.writeNull();
            return;
        }
        gen.writeString(value.setScale(SCALE, RoundingMode.HALF_UP).toPlainString());
    }
}
