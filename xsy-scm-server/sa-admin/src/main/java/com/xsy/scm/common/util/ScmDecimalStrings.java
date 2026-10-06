package com.xsy.scm.common.util;

import com.xsy.scm.common.exception.ScmBusinessException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Pattern;

import static com.xsy.scm.common.error.ScmCommonErrorCode.VALIDATION_ERROR;

/**
 * SCM 定点数（数量 / 金额）字符串的唯一解析入口。数量与金额只有一条规则：
 *
 * <ul>
 * <li>传输形态固定为 JSON 字符串，整数部分最多 14 位、小数部分最多 4 位；</li>
 * <li>接受 {@code "12.34"} / {@code "12.3400"}，拒绝 {@code "12.34000"}（超 4 位小数）；</li>
 * <li>拒绝 JSON 数字字面量 {@code 12.34}（浮点数在传输层就可能丢精度）；</li>
 * <li>拒绝负数与科学计数法（{@code 1e5}）；</li>
 * <li>{@code null} 与空白 → {@code null}，与 {@code "0"} 严格区分。</li>
 * </ul>
 *
 * <p>
 * 解析结果统一 {@code setScale(4, HALF_UP)}，与 {@code ScmFixedScale4Serializer} 的对外形态对称。
 *
 * <p>
 * 只服务「非 MVC 入口」，即参数已经进入 Service / Manager / 工具层的情况。MVC 入口的字段仍用 {@code @Pattern} 做第一道拦截（失败返回 30001）；本类失败抛
 * {@code VALIDATION_ERROR}(40000)。
 */
public final class ScmDecimalStrings {

    /**
     * 统一小数位。
     */
    public static final int SCALE = 4;

    /**
     * 整数部分最大位数（与 {@code NUMERIC(18,4)} 的容量一致）。
     */
    public static final int MAX_INTEGER_DIGITS = 14;

    /**
     * 规范形态：整数部分 1–14 位，可选小数部分 1–4 位。
     *
     * <p>
     * 与表单 {@code @Pattern} 使用同一字面量，保证 MVC 与非 MVC 入口规则一致。
     */
    public static final String PATTERN = "^\\d{1,14}(\\.\\d{1,4})?$";

    private static final Pattern CANONICAL = Pattern.compile(PATTERN);

    private ScmDecimalStrings() {
    }

    /**
     * 解析规范定点字符串。
     *
     * @param raw
     *            允许为 {@code null}；任何非 {@link String} 类型（例如 Jackson 反序列化出的数字）一律拒绝
     * @return {@code null} 当且仅当入参为 {@code null} 或空白；否则为 4 位小数的 {@link BigDecimal}
     * @throws ScmBusinessException
     *             形态不合法（40000）
     */
    public static BigDecimal parseScale4(Object raw) {
        if (raw == null) {
            return null;
        }
        if (!(raw instanceof String text)) {
            throw new ScmBusinessException(VALIDATION_ERROR);
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (!CANONICAL.matcher(trimmed).matches()) {
            throw new ScmBusinessException(VALIDATION_ERROR);
        }
        return new BigDecimal(trimmed).setScale(SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 解析必填定点字符串，空白视作缺失。
     *
     * @throws ScmBusinessException
     *             入参为 {@code null} / 空白 / 形态不合法（40000）
     */
    public static BigDecimal parseScale4Required(Object raw) {
        BigDecimal value = parseScale4(raw);
        if (value == null) {
            throw new ScmBusinessException(VALIDATION_ERROR);
        }
        return value;
    }

    /**
     * 入参是否为规范形态（{@code null} 与空白返回 {@code true}，因为二者表示"无值"）。
     */
    public static boolean isCanonical(String raw) {
        if (raw == null) {
            return true;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() || CANONICAL.matcher(trimmed).matches();
    }

    /**
     * 输出规范定点字符串，与 {@code ScmFixedScale4Serializer} 行为一致。
     *
     * @return {@code null} 入参返回 {@code null}；否则为 4 位小数字符串
     */
    public static String format(BigDecimal value) {
        return value == null ? null : value.setScale(SCALE, RoundingMode.HALF_UP).toPlainString();
    }
}
