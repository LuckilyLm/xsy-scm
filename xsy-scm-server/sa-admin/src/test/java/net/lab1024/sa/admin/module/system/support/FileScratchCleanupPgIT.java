package net.lab1024.sa.admin.module.system.support;

import net.lab1024.sa.admin.module.scm.common.ScmW3PgITBase;
import net.lab1024.sa.base.module.support.file.constant.FileFolderTypeEnum;
import net.lab1024.sa.base.module.support.file.dao.FileDao;
import net.lab1024.sa.base.module.support.file.domain.vo.FileVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 暂存附件生命周期（FA-2b）的真实库验证：上限只数未绑定的、回收只删真正无主的。
 *
 * <p>回收是**物理删除**，误删不可逆，所以这里承重的是「不删」的那几条断言：
 * 有关系行的、被业务列引用的、还没超期的，都必须原地保留。
 */
@DisplayName("FA-2b 暂存附件回收（PG IT）")
class FileScratchCleanupPgIT extends ScmW3PgITBase {

    @Autowired
    private FileDao fileDao;

    @Autowired
    private FileScratchCleanupJob cleanupJob;

    @Autowired
    private org.apache.ibatis.session.SqlSessionFactory sqlSessionFactory;

    /**
     * 绕过 DAO 改库之后还要走 mapper 读，必须先清一级缓存：
     * 用例整体在一个 Spring 事务里，SqlSession 与事务同生命周期，不清会读到旧值。
     */
    private void evict() {
        org.mybatis.spring.SqlSessionUtils.getSqlSession(sqlSessionFactory).clearCache();
    }

    private int unbound() {
        evict();
        return fileDao.countUnboundScratchByCreator(777L, 1);
    }

    private String key(String suffix) {
        return FileFolderTypeEnum.SCRATCH.getFolder() + prefix.toLowerCase() + "-" + suffix;
    }

    private Long seedScratch(String suffix, int ageDays) {
        String fileKey = key(suffix);
        java.time.LocalDateTime createTime = LocalDateTime.now().minusDays(ageDays);
        fileDao.insert(new net.lab1024.sa.base.module.support.file.domain.entity.FileEntity() {{
            setFileKey(fileKey);
            setFileName(suffix);
            setFileType("image/png");
            setFolderType(FileFolderTypeEnum.SCRATCH.getValue());
            setFileSize(10L);
            setCreatorId(777L);
            setCreatorUserType(1);
            setCreatorName("FA-2b IT");
            setCreateTime(createTime);
        }});
        FileVO stored = fileDao.getByFileKey(fileKey);
        assertThat(stored).as("夹具未落库：%s", fileKey).isNotNull();
        return stored.getFileId();
    }

    private boolean stillThere(String fileKey) {
        return fileDao.getByFileKey(fileKey) != null;
    }

    @Test
    @DisplayName("未绑定计数只数暂存目录：绑定一份就少一份，其它目录的行不计入")
    void unboundCountFollowsBindingAndFolderType() {
        String bound = key("bound.png");
        seedScratch("bound.png", 1);
        seedScratch("free.png", 1);
        assertThat(unbound()).as("本类夹具只有两条未绑定暂存件").isEqualTo(2);

        jdbc.update("INSERT INTO t_file_relation (file_key, biz_type, biz_id) VALUES (?, 'NOTICE', 88001)", bound);
        assertThat(unbound())
                .as("建立关系行后立即不再计入上限 —— 绑定就是解除暂存状态")
                .isEqualTo(1);

        // 普通私有目录（folder_type=1）的行不能被算进暂存上限，否则上限会被历史附件提前吃掉
        jdbc.update("INSERT INTO t_file (file_name, file_key, file_type, folder_type, creator_id, creator_user_type,"
                + " creator_name) VALUES ('common', 'private/common/cap-probe.png', 'image/png', 1, 777, 1, 'IT')");
        assertThat(unbound()).isEqualTo(1);
    }

    @Test
    @DisplayName("只回收超期且无引用无关系的暂存件，其余三种情况一律保留")
    void cleanupOnlyRemovesTrueOrphans() {
        Long freshId = seedScratch("fresh.png", 1);
        String orphanKey = key("orphan.png");
        Long orphanId = seedScratch("orphan.png", 30);
        String boundKey = key("bound2.png");
        Long boundId = seedScratch("bound2.png", 30);
        String referencedKey = key("referenced.png");
        Long referencedId = seedScratch("referenced.png", 30);

        jdbc.update("INSERT INTO t_file_relation (file_key, biz_type, biz_id) VALUES (?, 'NOTICE', 88002)", boundKey);
        jdbc.update("INSERT INTO t_notice (notice_type_id, title, all_visible_flag, scheduled_publish_flag,"
                + " publish_time, content_text, content_html, attachment, deleted_flag)"
                + " VALUES (1, ?, TRUE, FALSE, now(), 'x', 'x', ?, FALSE)", prefix + " 引用公告", referencedKey);

        String summary = cleanupJob.run("7");

        assertThat(stillThere(referencedKey)).as("仍被业务列引用的附件绝不能被删除（误删不可逆）").isTrue();
        assertThat(stillThere(boundKey)).as("有关系行的附件不在回收范围内").isTrue();
        assertThat(stillThere(key("fresh.png"))).as("未超期的附件不回收").isTrue();
        assertThat(stillThere(orphanKey))
                .as("超期、无关系行、无业务引用的暂存件必须被回收；本地存储 delete 恒成功，因此这条是决定性的")
                .isFalse();
        assertThat(summary).contains("已回收").contains("保留 7 天");
    }

    @Test
    @DisplayName("参数非法退回默认 7 天窗口，而不是整轮跳过")
    void badParamFallsBackToDefaultWindow() {
        seedScratch("param.png", 30);
        assertThat(cleanupJob.run("not-a-number")).contains("保留 7 天");
        assertThat(cleanupJob.run("0")).contains("保留 7 天");
        assertThat(cleanupJob.run("3")).contains("保留 3 天");
    }

}
