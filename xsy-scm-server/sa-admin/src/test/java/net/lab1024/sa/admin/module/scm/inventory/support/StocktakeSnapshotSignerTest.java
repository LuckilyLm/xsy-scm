package net.lab1024.sa.admin.module.scm.inventory.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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
}
