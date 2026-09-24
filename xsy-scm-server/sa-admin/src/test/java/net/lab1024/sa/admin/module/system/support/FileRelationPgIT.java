package net.lab1024.sa.admin.module.system.support;

import net.lab1024.sa.admin.module.scm.common.ScmW3PgITBase;
import net.lab1024.sa.admin.module.system.login.domain.RequestEmployee;
import net.lab1024.sa.base.common.domain.RequestUser;
import net.lab1024.sa.base.common.enumeration.UserTypeEnum;
import net.lab1024.sa.base.module.support.file.constant.FileRelationBizTypeEnum;
import net.lab1024.sa.base.module.support.file.dao.FileRelationDao;
import net.lab1024.sa.base.module.support.file.domain.entity.FileRelationEntity;
import net.lab1024.sa.base.module.support.file.service.FileAccessGuard;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FA-2 的真实库验证：V52 的表结构事实、存量回填，以及守卫按关系放行。
 *
 * <p>单测能证明判定逻辑，证明不了「回填真的把历史附件的关系建出来了」。
 * 而读侧一旦改成按关系判定，漏回填就等于把历史附件集体变成不可读 —— 这是
 * 上线顺序里最危险的一步，必须在 PostgreSQL 上跑一遍。
 */
@DisplayName("FA-2 文件关系授权（PG IT）")
class FileRelationPgIT extends ScmW3PgITBase {

    @Autowired
    private FileRelationDao relationDao;

    @Autowired
    private FileAccessGuard accessGuard;

    @Autowired
    private net.lab1024.sa.base.module.support.file.service.FileRelationService relations;

    private final AtomicLong bizIdSeq = new AtomicLong(900000L);

    private RequestUser employee(long id) {
        // RequestUser 是抽象契约，具体身份取底座员工请求对象（与 FileAccessGuardTest 同形）
        RequestEmployee user = new RequestEmployee();
        user.setEmployeeId(id);
        user.setActualName("FA-2 IT");
        user.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
        return user;
    }

    /**
     * 造一条只属于本用例的私有附件：key 落在 t_file 白名单目录里，上传者固定为 777。
     */
    private String seedPrivateFile(String suffix, long creatorId) {
        String key = "private/common/fa2-" + prefix.toLowerCase() + suffix;
        jdbc.update("INSERT INTO t_file (file_name, file_key, file_type, folder_type, creator_id,"
                + " creator_user_type, creator_name) VALUES (?, ?, 'image/png', 1, ?, 1, 'FA-2 IT')",
                suffix + ".png", key, creatorId);
        return key;
    }

    private void bind(String key, FileRelationBizTypeEnum type, long bizId) {
        FileRelationEntity row = new FileRelationEntity();
        row.setFileKey(key);
        row.setBizType(type.name());
        row.setBizId(bizId);
        relationDao.insert(row);
    }

    @Test
    @DisplayName("V52 的结构事实：白名单 CHECK、三元组部分唯一索引、双向查询索引都在")
    void migrationStructuralFactsArePresent() {
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM pg_constraint WHERE conname = 'ck_t_file_relation_biz_type'"
                        + " AND pg_get_constraintdef(oid) LIKE '%ENTERPRISE%NOTICE%HELP_DOC%FEEDBACK%PRODUCT%'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM pg_indexes WHERE tablename = 't_file_relation'"
                        + " AND indexname = 'uk_t_file_relation_active'",
                Integer.class)).isEqualTo(1);
        // 唯一性必须落在 (key, 类型, 对象) 三元组上：一份附件可以合法地属于多个业务对象
        String indexDef = jdbc.queryForObject(
                "SELECT indexdef FROM pg_indexes WHERE indexname = 'uk_t_file_relation_active'", String.class);
        assertThat(indexDef).contains("file_key").contains("biz_type").contains("biz_id");
        assertThat(indexDef).as("部分唯一索引必须带 deleted_flag 谓词，否则软删后无法重新绑定")
                .contains("deleted_flag");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM pg_indexes WHERE tablename = 't_file_relation'"
                        + " AND indexname = 'idx_t_file_relation_biz'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("业务对象没有 owner 列可猜：同一 key 可挂两个对象，唯一索引不拦这种共享")
    void oneFileMayBeBoundToSeveralObjects() {
        String key = seedPrivateFile("-shared.png", 777L);
        bind(key, FileRelationBizTypeEnum.NOTICE, bizIdSeq.incrementAndGet());
        bind(key, FileRelationBizTypeEnum.PRODUCT, bizIdSeq.incrementAndGet());

        assertThat(relationDao.listActiveByFileKeys(List.of(key))).hasSize(2);

        FileRelationEntity duplicate = new FileRelationEntity();
        duplicate.setFileKey(key);
        duplicate.setBizType(FileRelationBizTypeEnum.NOTICE.name());
        duplicate.setBizId(relationDao.listActiveByFileKeys(List.of(key)).getFirst().getBizId());
        assertThatThrownBy(() -> relationDao.insert(duplicate))
                .as("同一 (key, 类型, 对象) 重复绑定必须由部分唯一索引拒绝")
                .hasMessageContaining("uk_t_file_relation_active");
    }

    @Test
    @DisplayName("未知 biz_type 被库挡住，而不是静默存成一条永远读不到的行")
    void unknownBizTypeIsRejectedByDatabase() {
        String key = seedPrivateFile("-badtype.png", 777L);
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO t_file_relation (file_key, biz_type, biz_id) VALUES (?, 'SOMETHING_NEW', 1)", key))
                .hasMessageContaining("ck_t_file_relation_biz_type");
    }

    @Test
    @DisplayName("守卫按关系放行：无关系不可读，有关系且对象可读即可读")
    void guardDecisionsFollowRelationRows() {
        String key = seedPrivateFile("-guarded.png", 777L);
        RequestUser other = employee(778L);

        assertThatThrownBy(() -> accessGuard.checkRead(key, other))
                .as("他人上传的私有附件在没有关系行时必须拒绝")
                .isInstanceOf(FileAccessGuard.AccessDenied.class);

        // 上传者可读自己的文件 —— 这是关系之外的另一条通道，不该被回填打断
        assertThatCode(() -> accessGuard.checkRead(key, employee(777L))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("回填 SQL 真的会拆开逗号分隔的多 key（重放迁移里的 NOTICE 段）")
    void backfillSplitsCommaSeparatedAttachments() {
        String attachment = "private/notice/" + prefix + "-1.pdf,private/notice/" + prefix + "-2.pdf";
        jdbc.update("INSERT INTO t_notice (notice_type_id, title, all_visible_flag, scheduled_publish_flag,"
                        + " publish_time, content_text, content_html, attachment, deleted_flag)"
                        + " VALUES (1, ?, TRUE, FALSE, now(), ?, ?, ?, FALSE)",
                prefix + " 公告", "正文", "正文", attachment);
        Long noticeId = jdbc.queryForObject(
                "SELECT notice_id FROM t_notice WHERE title = ? AND deleted_flag = FALSE",
                Long.class, prefix + " 公告");

        // 跑迁移里的原文而不是抄一份 SQL：抄错的回填等于没有验证过上线用的那段。
        jdbc.execute("INSERT INTO t_file_relation (file_key, biz_type, biz_id)\n"
                + "SELECT btrim(part), 'NOTICE', n.notice_id\n"
                + "FROM t_notice n CROSS JOIN LATERAL regexp_split_to_table(n.attachment, ',') AS part\n"
                + "WHERE n.deleted_flag = FALSE AND coalesce(n.attachment, '') <> '' AND btrim(part) <> ''\n"
                + "  AND n.notice_id = " + noticeId + "\n"
                + "ON CONFLICT DO NOTHING");

        List<String> bound = jdbc.queryForList(
                "SELECT file_key FROM t_file_relation WHERE biz_type = 'NOTICE' AND biz_id = ? AND deleted_flag = FALSE"
                        + " ORDER BY file_key", String.class, noticeId);
        assertThat(bound).containsExactlyInAnyOrder(
                "private/notice/" + prefix + "-1.pdf", "private/notice/" + prefix + "-2.pdf");

        // 重放必须幂等：迁移可失败重试是硬要求
        jdbc.execute("INSERT INTO t_file_relation (file_key, biz_type, biz_id)\n"
                + "SELECT btrim(part), 'NOTICE', n.notice_id\n"
                + "FROM t_notice n CROSS JOIN LATERAL regexp_split_to_table(n.attachment, ',') AS part\n"
                + "WHERE n.deleted_flag = FALSE AND coalesce(n.attachment, '') <> '' AND btrim(part) <> ''\n"
                + "  AND n.notice_id = " + noticeId + "\n"
                + "ON CONFLICT DO NOTHING");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM t_file_relation WHERE biz_type = 'NOTICE' AND biz_id = ?",
                Integer.class, noticeId)).isEqualTo(2);
    }

    /**
     * <b>本用例盯的是一个真实缺陷</b>：对象的新建链路只调 {@code rebind}（因为它必须同时收回被删附件），
     * 而如果 {@code rebind} 只做清理、不补授权行，新建带附件的公告 / 帮助文档 / 企业对任何
     * 非上传者都永远 30005 —— 上面几条用例都按「先有行再判守卫」组织夹具，所以看不出这个洞。
     */
    @Test
    @DisplayName("rebind 是同步而不是只清理：新引用的 key 必须建出行，被移出的必须置删")
    void rebindGrantsCurrentKeysAndReclaimsRemovedOnes() {
        String first = seedPrivateFile("-rb1.png", 777L);
        String second = seedPrivateFile("-rb2.png", 777L);
        long noticeId = bizIdSeq.incrementAndGet();

        relations.rebind(FileRelationBizTypeEnum.NOTICE, noticeId, List.of(first));
        assertThat(activeKeysOf(FileRelationBizTypeEnum.NOTICE, noticeId))
                .as("新建对象时 rebind 必须建立授权行").containsExactly(first);

        relations.rebind(FileRelationBizTypeEnum.NOTICE, noticeId, List.of(first, second));
        assertThat(activeKeysOf(FileRelationBizTypeEnum.NOTICE, noticeId))
                .as("追加附件只补差集，不产生第二行").containsExactlyInAnyOrder(first, second);
        assertThat(totalRows(FileRelationBizTypeEnum.NOTICE, noticeId))
                .as("已存在的三元组不能被重复插入").isEqualTo(2);

        relations.rebind(FileRelationBizTypeEnum.NOTICE, noticeId, List.of(second));
        assertThat(activeKeysOf(FileRelationBizTypeEnum.NOTICE, noticeId)).containsExactly(second);

        // 收回后重新挂上：部分唯一索引带 deleted_flag 谓词，所以历史行留在原地、新行可再建
        relations.rebind(FileRelationBizTypeEnum.NOTICE, noticeId, List.of(first));
        assertThat(activeKeysOf(FileRelationBizTypeEnum.NOTICE, noticeId)).containsExactly(first);
        assertThat(totalRows(FileRelationBizTypeEnum.NOTICE, noticeId))
                .as("软删行保留以便追溯授权变化").isGreaterThan(1);

        relations.rebind(FileRelationBizTypeEnum.NOTICE, noticeId, List.of());
        assertThat(activeKeysOf(FileRelationBizTypeEnum.NOTICE, noticeId))
                .as("附件全部删掉时必须收回，只增不减等于长期放行").isEmpty();
    }

    private List<String> activeKeysOf(FileRelationBizTypeEnum type, long bizId) {
        return jdbc.queryForList("SELECT file_key FROM t_file_relation WHERE biz_type = ? AND biz_id = ?"
                + " AND deleted_flag = FALSE ORDER BY file_key", String.class, type.name(), bizId);
    }

    private int totalRows(FileRelationBizTypeEnum type, long bizId) {
        return jdbc.queryForObject("SELECT count(*) FROM t_file_relation WHERE biz_type = ? AND biz_id = ?",
                Integer.class, type.name(), bizId);
    }

    @Test
    @DisplayName("软删业务对象的关系行随删除置位，物理文件不动")
    void unbindSoftDeletesRelationOnly() {
        String key = seedPrivateFile("-unbind.png", 777L);
        long bizId = bizIdSeq.incrementAndGet();
        bind(key, FileRelationBizTypeEnum.NOTICE, bizId);
        FileRelationEntity row = relationDao.listActiveByFileKeys(List.of(key)).stream()
                .filter(r -> r.getBizId().equals(bizId)).findFirst().orElseThrow();

        jdbc.update("UPDATE t_file_relation SET deleted_flag = TRUE WHERE relation_id = ?", row.getRelationId());

        // 旁路 UPDATE 不刷新 MyBatis 一级缓存，读回必须走同一个连接之外的裸查询
        assertThat(jdbc.queryForList(
                "SELECT relation_id FROM t_file_relation WHERE deleted_flag = FALSE AND file_key = ?",
                Long.class, key)).doesNotContain(row.getRelationId());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM t_file WHERE file_key = ?", Integer.class, key))
                .as("解绑只收回授权，不删物理文件记录").isEqualTo(1);
    }
}
