package net.lab1024.sa.admin.module.scm.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 单据编号拼接规则（纯函数，不连库）。
 *
 * <p>原先这套断言在 4 个 {@code Inventory*NumberGeneratorTest} 里各写一遍，而它们调用的是
 * 各自类中<b>逐字节相同</b>的 {@code format} 实现 —— 同一个口径被重复断言 12 次，
 * 按 §31「Avoid Over-Testing」属于该合并的一类。拼接现已收敛到本类，规则在这里测一次；
 * 各单据自己的前缀是否互不撞车仍留在各自的测试里，那才是每个文档独有的事实。
 */
@DisplayName("SCM 单据编号拼接规则")
class ScmDocumentNumbersTest {

    @Test
    @DisplayName("单号 = 前缀 + yyyyMMdd + 6 位补零")
    void padsToSixDigits() {
        String no = ScmDocumentNumbers.format("OUT", 1L);
        assertThat(no).startsWith("OUT");
        assertThat(no).hasSize("OUT".length() + 8 + 6);
        assertThat(no).endsWith("000001");
    }

    @Test
    @DisplayName("超过 999999 自然扩位，不截断也不报错")
    void expandsBeyondSixDigits() {
        String no = ScmDocumentNumbers.format("OUT", 1_000_000L);
        assertThat(no).endsWith("1000000");
        assertThat(no).hasSize("OUT".length() + 8 + 7);
    }

    @Test
    @DisplayName("前缀由调用方决定，规则本身不绑定单据类型")
    void honoursPrefix() {
        assertThat(ScmDocumentNumbers.format("XX", 42L)).contains("000042");
        assertThat(ScmDocumentNumbers.format("STK", 42L)).startsWith("STK").endsWith("000042");
    }
}
