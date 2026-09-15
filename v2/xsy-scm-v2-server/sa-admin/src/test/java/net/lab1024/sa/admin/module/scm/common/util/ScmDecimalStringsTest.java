package net.lab1024.sa.admin.module.scm.common.util;

import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SCM 定点数唯一解析规则的契约测试。
 *
 * <p>这些用例同时是「legacy 双重解析规则」缺陷（D13/D14）的回归门禁：任何放宽到接受数字、
 * 负数、超 4 位小数或科学计数法的改动都必须先让本测试失败。
 */
class ScmDecimalStringsTest {

    private static int codeOf(Throwable t) {
        return ((ScmBusinessException) t).getErrorCode().getCode();
    }

    @Test
    @DisplayName("接受 4 位以内小数的规范字符串")
    void acceptsCanonicalStrings() {
        assertThat(ScmDecimalStrings.parseScale4("12.3400")).isEqualByComparingTo(new BigDecimal("12.3400"));
        assertThat(ScmDecimalStrings.parseScale4("12.34")).isEqualByComparingTo(new BigDecimal("12.3400"));
        assertThat(ScmDecimalStrings.parseScale4("0")).isEqualByComparingTo(new BigDecimal("0.0000"));
        assertThat(ScmDecimalStrings.parseScale4("  8  ")).isEqualByComparingTo(new BigDecimal("8.0000"));
    }

    @Test
    @DisplayName("超过 4 位小数被拒绝，不做静默截断")
    void rejectsMoreThanFourDecimals() {
        assertThatThrownBy(() -> ScmDecimalStrings.parseScale4("12.34000"))
                .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40000));
    }

    @Test
    @DisplayName("JSON 数字字面量被拒绝：数量与金额只以字符串传输")
    void rejectsNumericLiterals() {
        assertThatThrownBy(() -> ScmDecimalStrings.parseScale4(new BigDecimal("12.34")))
                .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40000));
        assertThatThrownBy(() -> ScmDecimalStrings.parseScale4(12.34d))
                .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40000));
        assertThatThrownBy(() -> ScmDecimalStrings.parseScale4(12))
                .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40000));
    }

    @Test
    @DisplayName("null 与空白都表示「无值」，与 0 严格区分")
    void nullAndBlankMeanAbsent() {
        assertThat(ScmDecimalStrings.parseScale4(null)).isNull();
        assertThat(ScmDecimalStrings.parseScale4("")).isNull();
        assertThat(ScmDecimalStrings.parseScale4("   ")).isNull();
        assertThat(ScmDecimalStrings.parseScale4("0")).isNotNull().isEqualByComparingTo("0.0000");
    }

    @Test
    @DisplayName("整数部分超过 14 位被拒绝")
    void rejectsTooManyIntegerDigits() {
        assertThat(ScmDecimalStrings.parseScale4("12345678901234")).isNotNull();
        assertThatThrownBy(() -> ScmDecimalStrings.parseScale4("123456789012345"))
                .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40000));
    }

    @Test
    @DisplayName("负数与科学计数法被拒绝")
    void rejectsNegativeAndScientificNotation() {
        assertThatThrownBy(() -> ScmDecimalStrings.parseScale4("-1"))
                .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40000));
        assertThatThrownBy(() -> ScmDecimalStrings.parseScale4("-0.0001"))
                .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40000));
        assertThatThrownBy(() -> ScmDecimalStrings.parseScale4("1e5"))
                .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40000));
        assertThatThrownBy(() -> ScmDecimalStrings.parseScale4("1E5"))
                .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40000));
    }

    @Test
    @DisplayName("必填解析：空白与 null 都失败")
    void requiredRejectsAbsent() {
        assertThat(ScmDecimalStrings.parseScale4Required("1.5")).isEqualByComparingTo(new BigDecimal("1.5000"));
        assertThatThrownBy(() -> ScmDecimalStrings.parseScale4Required(null)).isInstanceOf(ScmBusinessException.class);
        assertThatThrownBy(() -> ScmDecimalStrings.parseScale4Required("  ")).isInstanceOf(ScmBusinessException.class);
    }

    @Test
    @DisplayName("输出形态与 ScmFixedScale4Serializer 对称")
    void formatMatchesSerializerContract() {
        assertThat(ScmDecimalStrings.format(null)).isNull();
        assertThat(ScmDecimalStrings.format(BigDecimal.ZERO)).isEqualTo("0.0000");
        assertThat(ScmDecimalStrings.format(new BigDecimal("12.34565"))).isEqualTo("12.3457");
        assertThat(ScmDecimalStrings.format(new BigDecimal("8"))).isEqualTo("8.0000");
    }

    @Test
    @DisplayName("isCanonical 与 @Pattern 使用同一字面量")
    void isCanonicalMatchesPatternConstant() {
        assertThat(ScmDecimalStrings.PATTERN).isEqualTo("^\\d{1,14}(\\.\\d{1,4})?$");
        assertThat(ScmDecimalStrings.isCanonical(null)).isTrue();
        assertThat(ScmDecimalStrings.isCanonical("")).isTrue();
        assertThat(ScmDecimalStrings.isCanonical("1.2345")).isTrue();
        assertThat(ScmDecimalStrings.isCanonical("1.23456")).isFalse();
        assertThat(ScmDecimalStrings.isCanonical("-1")).isFalse();
    }
}
