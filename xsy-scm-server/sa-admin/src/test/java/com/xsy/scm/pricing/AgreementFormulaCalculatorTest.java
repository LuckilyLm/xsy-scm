package com.xsy.scm.pricing;

import com.xsy.scm.pricing.constant.AgreementFormulaType;
import com.xsy.scm.pricing.manager.AgreementFormulaCalculator;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgreementFormulaCalculatorTest {

    @Test
    void supportsFourExplicitFormulaTypes() {
        assertPrice(AgreementFormulaType.FIXED, "7.1234", null, null, null, "7.1234");
        assertPrice(AgreementFormulaType.MARKET_PLUS_AMOUNT, null, "2.5000", "10.0000", null, "12.5000");
        assertPrice(AgreementFormulaType.COST_MARKUP_RATE, null, "0.2000", null, "8.0000", "9.6000");
        assertPrice(AgreementFormulaType.MARKET_MARKUP_RATE, null, "0.2000", "10.0000", null, "12.0000");
    }

    @Test
    void roundsAtFourDecimalPlacesWithHalfUp() {
        assertPrice(AgreementFormulaType.MARKET_MARKUP_RATE, null, "0.3333", "1.0000", null, "1.3333");
        assertPrice(AgreementFormulaType.COST_MARKUP_RATE, null, "0.1000", null, "1.2345", "1.3580");
        assertPrice(AgreementFormulaType.FIXED, "1.23445", null, null, null, "1.2345");
    }

    @Test
    void missingInputsMustNotFallbackToMarketPriceOrZero() {
        assertThat(calc(null, null, null, "10.0000", null)).isNull();
        assertThat(calc(AgreementFormulaType.FIXED, null, null, "10.0000", null)).isNull();
        assertThat(calc(AgreementFormulaType.MARKET_PLUS_AMOUNT, null, "2", null, null)).isNull();
        assertThat(calc(AgreementFormulaType.COST_MARKUP_RATE, null, "0.20", "10", null)).isNull();
        assertThat(calc(AgreementFormulaType.MARKET_MARKUP_RATE, null, "0.20", null, "5")).isNull();
        assertThat(calc(AgreementFormulaType.MARKET_MARKUP_RATE, null, null, "5", null)).isNull();
    }

    @Test
    void rejectsNegativeParametersAndOverflowWithoutCrashing() {
        assertThat(calc(AgreementFormulaType.MARKET_MARKUP_RATE, null, "-0.5000", "20", null)).isNull();
        assertThat(calc(AgreementFormulaType.MARKET_PLUS_AMOUNT, null, "-2", "10", null)).isNull();
        assertThat(calc(AgreementFormulaType.COST_MARKUP_RATE, null, "-1", null, "5")).isNull();
        assertThat(calc(AgreementFormulaType.FIXED, "-0.0001", null, null, null)).isNull();
        assertThat(calc(AgreementFormulaType.FIXED, "100000000000000.0000", null, null, null)).isNull();
        assertThat(calc(AgreementFormulaType.MARKET_PLUS_AMOUNT, null, "99999999999999.9999",
                "99999999999999.9999", null)).isNull();
    }

    @Test
    void zeroIsAnExplicitPriceNotAMissingValue() {
        assertPrice(AgreementFormulaType.FIXED, "0.0000", null, null, null, "0.0000");
        assertPrice(AgreementFormulaType.MARKET_MARKUP_RATE, null, "0.1000", "0.0000", null, "0.0000");
    }

    private static void assertPrice(AgreementFormulaType formula, String fixed, String parameter,
            String market, String cost, String expected) {
        assertThat(calc(formula, fixed, parameter, market, cost)).isEqualByComparingTo(expected);
    }

    private static BigDecimal calc(AgreementFormulaType formula, String fixed, String parameter,
            String market, String cost) {
        return AgreementFormulaCalculator.calculate(formula, decimal(fixed), decimal(parameter),
                decimal(market), decimal(cost));
    }

    private static BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }
}
