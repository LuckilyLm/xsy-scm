package net.lab1024.sa.admin.module.scm.product;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.lab1024.sa.admin.module.scm.common.ScmW6PgITBase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FA-3 商品图 key 搬运（V58）的落库取证。
 *
 * <p><b>为什么必须自己摆历史形态</b>：本环境的 {@code xsy_scm_b0} 与全部一次性 IT 库里，
 * 「活行且非 public 前缀」的 {@code product_image} 行数实测为 0 —— 搬运在真实库上是空操作，
 * 「改写正确」这件事不可能靠跑一遍现网数据来证明。因此这里按迁移文件里的标记取出<b>同一段 SQL</b>
 * （不复制一份等价语句），在模拟的搬运前状态上执行。
 *
 * <p>本仓库不建外键，且 V58 的三条语句都不 join {@code product_spu}，所以夹具不需要一个真实商品：
 * 造真商品只会让这条迁移用例额外依赖 W1 的商品聚合不变量。
 *
 * <p>软删行刻意留在原地：CHECK 无法做成部分约束，V58 因此把 {@code deleted = TRUE} 写进约束，
 * 历史行的原 key 是搬运前的事实，改写它等于改审计。
 */
@DisplayName("FA-3 商品图 key 搬运（PG IT）")
class ScmProductImageKeyMigrationPgIT extends ScmW6PgITBase {

    private static final String V58 = "V58__scm_product_image_public_file_key.sql";

    /** 搬运段标记；改标记名会让取段断言直接失败，而不是静默跑一段空 SQL。 */
    private static final String BEGIN = "-- FA3 BEGIN";

    private static final String END = "-- FA3 END";

    @Test
    @DisplayName("搬运：key 改到 public/image/、t_file 同步（含 folder_type）、关系行收回，重跑一次结果不变")
    void movesLegacyKeysIntoPublicImagePrefix() {
        // CHECK 描述的是搬运之后的形态；先摘掉才能摆出搬运前的库状态（用例事务回滚，不留痕）
        dropPublicPrefixCheck();
        Long spu = 900001L;
        // 两种历史形状：直接落在目录下的文件名，以及带子目录的尾段（曾经的公告图目录形态）
        String flat = "private/common/" + tail("flat");
        String nested = "private/common/8f3d/notice/" + tail("nested");
        seedFile(flat);
        seedFile(nested);
        seedImage(spu, flat, true);
        seedImage(spu, nested, false);
        seedRelation(flat, spu);
        seedRelation(nested, spu);
        // 已软删的历史行不是搬运对象
        String tombstone = "private/common/" + tail("tomb");
        seedFile(tombstone);
        seedImage(spu, tombstone, false, true);

        runRewrite();

        assertThat(activeKeysOf(spu)).containsExactlyInAnyOrder(
                "public/image/" + tail("flat"), "public/image/8f3d/notice/" + tail("nested"));
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM product_image WHERE deleted = FALSE AND file_key NOT LIKE 'public/%'",
                Integer.class)).as("活行里不再有任何非公开前缀的商品图").isZero();
        assertThat(jdbc.queryForObject("SELECT folder_type FROM t_file WHERE file_key = ?", Integer.class,
                "public/image/" + tail("flat")))
                .as("folder_type 必须与 key 前缀一起改，否则同一行里两种口径并存").isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM t_file WHERE file_key = ?", Integer.class,
                "public/image/8f3d/notice/" + tail("nested"))).isEqualTo(1);
        assertThat(activeRelationCount(flat)).as("搬到公开前缀后关系行是死键，必须收回").isZero();
        assertThat(activeRelationCount(nested)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM t_file_relation WHERE file_key = ?", Integer.class,
                flat)).as("软删除而不是物理删：授权变更要可追溯").isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT file_key FROM t_file WHERE file_key = ?", String.class, tombstone))
                .isEqualTo(tombstone);

        // 可重入：第二次执行不改写任何行、也不报错
        List<String> afterFirst = activeKeysOf(spu);
        runRewrite();
        assertThat(activeKeysOf(spu)).isEqualTo(afterFirst);
    }

    @Test
    @DisplayName("两条历史 key 折叠成同一个目标时中止，不留下互相覆盖的结果")
    void refusesWhenTwoLegacyKeysCollapseToTheSameTarget() {
        dropPublicPrefixCheck();
        String sharedTail = tail("collide");
        seedFile("private/common/" + sharedTail);
        seedFile("private/notice/" + sharedTail);
        seedImage(900002L, "private/common/" + sharedTail, true);
        seedImage(900003L, "private/notice/" + sharedTail, true);

        assertThatThrownBy(this::runRewrite)
                .rootCause()
                .as("折叠冲突必须在改写前中止：静默覆盖会让一个商品的图指向另一个商品的对象")
                .hasMessageContaining("折叠");
    }

    @Test
    @DisplayName("目标 key 已被其它文件占用时中止，不改写任何一方")
    void refusesWhenTargetKeyIsAlreadyTaken() {
        dropPublicPrefixCheck();
        String legacy = "private/common/" + tail("taken");
        String occupied = "public/image/" + tail("taken");
        seedFile(legacy);
        seedFile(occupied);
        seedImage(900004L, legacy, true);

        assertThatThrownBy(this::runRewrite)
                .rootCause()
                .hasMessageContaining("已被其它文件占用");
        // 探针之后不再有语句：被拒的执行会让本用例的 Spring 事务进入 25P02
    }

    @Test
    @DisplayName("活行挂私有 key 被 CHECK 拒绝，软删行保留原 key 仍然合法")
    void checkAllowsOnlyPublicKeysOnActiveRows() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_constraint WHERE conname = ?", Integer.class,
                "ck_product_image_public_file_key")).as("V58 已把不变量落进数据库").isEqualTo(1);
        String legacy = "private/common/" + tail("check");
        seedFile(legacy);
        // 软删分支放在前面：CHECK 无法做成部分约束，历史行的原 key 就是靠这个分支留下来的
        seedImage(900006L, legacy, true, true);
        assertThat(jdbc.queryForObject("SELECT deleted FROM product_image WHERE spu_id = ?", Boolean.class,
                900006L)).isTrue();
        // 越界探针放在最后（同上：25P02 之后没有可执行的语句）
        assertThatThrownBy(() -> seedImage(900005L, legacy, true))
                .hasMessageContaining("ck_product_image_public_file_key");
    }

    @Test
    @DisplayName("空 file_key 无法机械搬运：迁移直接拒绝，不猜目标名")
    void refusesToGuessTargetForBlankKey() {
        dropPublicPrefixCheck();
        seedImage(900007L, " ", true);
        assertThatThrownBy(this::runRewrite)
                .rootCause()
                .hasMessageContaining("为空");
    }

    // ==================== 夹具 ====================

    private void runRewrite() {
        jdbc.execute(migrationSection(V58, BEGIN, END));
    }

    private void dropPublicPrefixCheck() {
        jdbc.execute("ALTER TABLE product_image DROP CONSTRAINT ck_product_image_public_file_key");
    }

    /** 尾段带上用例前缀，避免与同库里其它数据相撞。 */
    private String tail(String label) {
        return prefix.toLowerCase() + "-" + label + ".jpg";
    }

    private void seedFile(String fileKey) {
        jdbc.update("INSERT INTO t_file (folder_type, file_name, file_size, file_key, file_type, create_time) "
                        + "VALUES (1, ?, 1, ?, 'jpg', CURRENT_TIMESTAMP)",
                fileKey.substring(fileKey.lastIndexOf('/') + 1), fileKey);
    }

    private void seedImage(Long spuId, String fileKey, boolean primary) {
        seedImage(spuId, fileKey, primary, false);
    }

    private void seedImage(Long spuId, String fileKey, boolean primary, boolean deleted) {
        jdbc.update("INSERT INTO product_image (spu_id, file_key, file_name, file_size, is_primary, sort_order, "
                        + "version, deleted, created_at, updated_at, created_by, updated_by) "
                        + "VALUES (?, ?, ?, 1, ?, 0, 0, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'it', 'it')",
                spuId, fileKey, fileKey.substring(fileKey.lastIndexOf('/') + 1), primary, deleted);
    }

    private void seedRelation(String fileKey, Long spuId) {
        jdbc.update("INSERT INTO t_file_relation (file_key, biz_type, biz_id) VALUES (?, 'PRODUCT', ?)",
                fileKey, spuId);
    }

    private List<String> activeKeysOf(Long spuId) {
        return jdbc.queryForList("SELECT file_key FROM product_image WHERE spu_id = ? AND deleted = FALSE "
                + "ORDER BY file_key", String.class, spuId);
    }

    private int activeRelationCount(String fileKey) {
        return jdbc.queryForObject("SELECT count(*) FROM t_file_relation WHERE file_key = ? "
                + "AND deleted_flag = FALSE", Integer.class, fileKey);
    }
}
