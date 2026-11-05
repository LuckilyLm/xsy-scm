package com.xsy.scm.mall;

import static org.assertj.core.api.Assertions.assertThat;

import com.xsy.scm.mall.home.service.MallThemeColors;
import org.junit.jupiter.api.Test;

class MallThemeColorsTest {
    @Test
    void acceptsOnlySixDigitHexAndNormalizesCase() {
        assertThat(MallThemeColors.isValid("#16A34A")).isTrue();
        assertThat(MallThemeColors.isValid("#16a34a")).isTrue();
        assertThat(MallThemeColors.isValid("#abc")).isFalse();
        assertThat(MallThemeColors.isValid("16A34A")).isFalse();
        assertThat(MallThemeColors.isValid(null)).isFalse();
        assertThat(MallThemeColors.normalize(" #16a34a ")).isEqualTo("#16A34A");
    }

    @Test
    void presetColorsAreStableAndUnknownCodesFallBackToFresh() {
        assertThat(MallThemeColors.baseColor("FRESH")).isEqualTo("#16A34A");
        assertThat(MallThemeColors.baseColor("WARM")).isEqualTo("#F97316");
        assertThat(MallThemeColors.baseColor("OCEAN")).isEqualTo("#0284C7");
        assertThat(MallThemeColors.baseColor("UNKNOWN")).isEqualTo("#16A34A");
        assertThat(MallThemeColors.baseColor(null)).isEqualTo("#16A34A");
    }

    @Test
    void derivedPaletteMatchesTheSharedFrontendFormula() {
        assertThat(MallThemeColors.dark("#0284C7")).isEqualTo("#026CA3");
        assertThat(MallThemeColors.ink("#0284C7")).isEqualTo("#015A87");
        assertThat(MallThemeColors.light("#0284C7")).isEqualTo("#E6F3F9");
        assertThat(MallThemeColors.disabled("#0284C7")).isEqualTo("#8DC8E6");
    }

    @Test
    void rejectsColorsWhereWhiteTextWouldDisappear() {
        assertThat(MallThemeColors.readableOnWhite("#16A34A")).isTrue();
        assertThat(MallThemeColors.readableOnWhite("#0284C7")).isTrue();
        // 亮黄配白字几乎看不清，保存时应直接拒绝，而不是等到客户端才发现
        assertThat(MallThemeColors.readableOnWhite("#FFE600")).isFalse();
        assertThat(MallThemeColors.whiteContrast("#FFFFFF")).isEqualTo(1.0);
    }
}
