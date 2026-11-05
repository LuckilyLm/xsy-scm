package com.xsy.scm.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 单据编号规则（纯函数，不连库）。
 */
@DisplayName("SCM 单据编号规则")
class ScmDocumentNumbersTest {

    @Test
    @DisplayName("单号 = XSY + 雪花 ID")
    void documentNumberUsesXsyPrefixAndSnowflakeId() {
        assertThat(ScmDocumentNumbers.next()).matches("^XSY\\d{18,19}$");
    }

    @Test
    @DisplayName("连续取号不重复")
    void consecutiveNumbersAreUnique() {
        assertThat(ScmDocumentNumbers.next()).isNotEqualTo(ScmDocumentNumbers.next());
    }

    @Test
    @DisplayName("主数据编码 = 前缀 + 至少 6 位补零，不带日期段")
    void masterCodePadsWithoutDateSegment() {
        assertThat(ScmDocumentNumbers.masterCode("CUS", 1L)).isEqualTo("CUS000001");
        assertThat(ScmDocumentNumbers.masterCode("SUP", 123456L)).isEqualTo("SUP123456");
        assertThat(ScmDocumentNumbers.masterCode("WH", 1_000_000L)).isEqualTo("WH1000000");
    }
}
