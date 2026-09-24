package net.lab1024.sa.admin.module.scm.inventory.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 盘点快照签名密钥的环境门禁（Wave 6 审计修复）。
 *
 * <p>密钥保护的正是「快照未被篡改」这一件事：生产沿用仓库里的公开默认值等于没有签名，
 * 因此必须在<b>启动</b>时失败，而不是等到有人伪造凭证才被发现。
 * 这里只构造对象，不加载 Spring 上下文 —— 判定完全在构造函数内。
 */
@DisplayName("盘点快照密钥：非生产可用默认值，pre/prod 缺失或沿用默认值即启动失败")
class StocktakeSnapshotSignerTest {

    /** 生产应当显式配置的私有密钥（任意非默认高熵值）。 */
    private static final String PRIVATE_KEY = "3f2b7c9d40a15e8b9c6d3f2a1b0c4d5e";

    private StocktakeSnapshotSigner signer(String secret, String profiles) {
        return new StocktakeSnapshotSigner(new ObjectMapper(), secret, profiles);
    }

    @ParameterizedTest(name = "{0} profile 下缺密钥必须启动失败")
    @ValueSource(strings = {"pre", "prod", "production", "prod,druid"})
    void rejectsBlankSecretInProduction(String profiles) {
        assertThatThrownBy(() -> signer(null, profiles)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> signer("   ", profiles)).isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest(name = "{0} profile 下沿用仓库默认密钥必须启动失败")
    @ValueSource(strings = {"pre", "prod"})
    void rejectsRepositoryDefaultSecretInProduction(String profiles) {
        assertThatThrownBy(() -> signer(StocktakeSnapshotSigner.DEV_SECRET, profiles))
                .isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest(name = "{0} profile 下允许使用开发默认密钥")
    @ValueSource(strings = {"dev", "test", "dev,test"})
    void allowsDevSecretOutsideProduction(String profiles) {
        assertThatCode(() -> signer(StocktakeSnapshotSigner.DEV_SECRET, profiles)).doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "{0} profile 下显式私有密钥正常启动")
    @ValueSource(strings = {"pre", "prod"})
    void acceptsExplicitSecretInProduction(String profiles) {
        assertThatCode(() -> signer(PRIVATE_KEY, profiles)).doesNotThrowAnyException();
    }

    /**
     * 凭证要能被写进 Excel 单元格：POI 的硬上限是 32767 字符。
     *
     * <p>载荷为仓库里每条活跃余额带上 skuCode / 单位 / 账面量，且导出会把凭证写进<b>每一行</b>。
     * 实测 240 个 SKU 就把未压缩的 JSON 顶过上限，大仓库因此根本导不出盘点模板
     * （{@code ScmStocktakeImportPgIT} 整类 IllegalArgumentException）。
     * 这里钉住「充分压缩 + 原样往返」，删掉签名链上的 DEFLATE 会立刻变红。
     */
    @ParameterizedTest(name = "{0} 条余额的凭证仍在单元格上限内")
    @ValueSource(ints = {240, 2000})
    void credentialFitsExcelCellLimit(int size) {
        var entries = new java.util.ArrayList<StocktakeSnapshotSigner.Entry>();
        for (int i = 0; i < size; i++) {
            entries.add(new StocktakeSnapshotSigner.Entry("SKU-" + i, (long) i, (long) i + 10_000,
                    "kg", i, new java.math.BigDecimal("12.3456")));
        }
        var payload = new StocktakeSnapshotSigner.Payload("1.0", 7L, "1:1", 1_000L, 9_000L, entries);
        var signer = signer(PRIVATE_KEY, "prod");

        String token = signer.sign(payload);
        assertThat(token).as("凭证长度必须留在 POI 单元格上限内").hasSizeLessThan(32_767);
        assertThat(signer.verify(token, 2_000L).entries())
                .hasSize(size)
                .last().isEqualTo(entries.get(size - 1));
    }

    @Test
    @DisplayName("篡改压缩后的载荷同样过不了签名：DEFLATE 不是完整性手段，MAC 才是")
    void tamperedCompressedBodyStillFailsVerification() {
        var entries = new java.util.ArrayList<StocktakeSnapshotSigner.Entry>();
        for (int i = 0; i < 50; i++) {
            entries.add(new StocktakeSnapshotSigner.Entry("SKU-" + i, (long) i, (long) i,
                    "kg", i, new java.math.BigDecimal("1.0000")));
        }
        var signer = signer(PRIVATE_KEY, "prod");
        String token = signer.sign(new StocktakeSnapshotSigner.Payload("1.0", 7L, "1:1", 1_000L, 9_000L, entries));
        byte[] packed = java.util.Base64.getUrlDecoder().decode(token.substring(0, token.lastIndexOf('.')));
        packed[packed.length / 2] ^= 0x55;
        String forged = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(packed)
                + token.substring(token.lastIndexOf('.'));
        assertThatThrownBy(() -> signer.verify(forged, 2_000L))
                .isInstanceOf(StocktakeSnapshotSigner.SnapshotCredentialException.class);
    }
}
