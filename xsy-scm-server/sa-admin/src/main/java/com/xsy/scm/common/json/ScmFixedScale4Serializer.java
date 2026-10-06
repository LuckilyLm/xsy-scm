package com.xsy.scm.common.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * SCM 金额与数量的 4 位定点序列化器。
 *
 * <p>
 * null 保持 JSON null（与「价格为零」严格区分），0 输出 "0.0000"；数值使用 HALF_UP 保留 4 位并输出字符串，避免 JSON Number 精度损失。
 *
 * <p>
 * 字段同时需要配置 using 与 nullsUsing，确保 null 不受全局序列化器影响。
 *
 * @see com.xsy.scm.common.util.ScmDecimalStrings
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
