package net.lab1024.sa.admin.module.system.support;

import lombok.extern.slf4j.Slf4j;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.module.support.file.dao.FileDao;
import net.lab1024.sa.base.module.support.file.domain.vo.FileVO;
import net.lab1024.sa.base.module.support.file.service.IFileStorageService;
import net.lab1024.sa.base.module.support.job.core.SmartJob;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 暂存附件回收（FA-2b）：删除「超期 + 无关系行 + 无任何业务表直接引用」的对象存储文件。
 *
 * <p>三条件缺一不可，其中「无业务表引用」是最后一道防误删闸门：物理删除不可逆，
 * 宁可这一轮不删、留到人工确认，也绝不能把某个表单正在引用的附件删掉。
 *
 * <p>参数为保留天数，缺省 7 天；每轮最多处理 {@link #BATCH_LIMIT} 条，避免一次任务
 * 打满存储侧配额。
 */
@Slf4j
@Service
public class FileScratchCleanupJob implements SmartJob {

    private static final int DEFAULT_RETENTION_DAYS = 7;

    private static final int BATCH_LIMIT = 200;

    /**
     * 可能直接持有 fileKey 的业务列（表名, 列名）。
     *
     * <p>新领域如果新增附件列，必须同时登记到这里，否则该列引用的暂存件会被误判为孤儿。
     */
    private static final String[][] BUSINESS_KEY_COLUMNS = {
            {"product_image", "file_key"},
            {"t_notice", "attachment"},
            {"t_help_doc", "attachment"},
            {"t_feedback", "feedback_attachment"},
            {"t_oa_enterprise", "enterprise_logo"},
            {"t_oa_enterprise", "business_license"},
            {"t_employee", "avatar"},
    };

    private final FileDao fileDao;

    private final IFileStorageService fileStorageService;

    private final JdbcTemplate jdbcTemplate;

    public FileScratchCleanupJob(FileDao fileDao, IFileStorageService fileStorageService, JdbcTemplate jdbcTemplate) {
        this.fileDao = fileDao;
        this.fileStorageService = fileStorageService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public String run(String param) {
        int retentionDays = parseRetentionDays(param);
        List<FileVO> candidates = fileDao.listScratchCandidates(retentionDays, BATCH_LIMIT);
        int referenced = 0;
        int failed = 0;
        int recycled = 0;
        for (FileVO candidate : candidates) {
            String key = candidate.getFileKey();
            if (referencedByBusinessColumn(key)) {
                referenced++;
                continue;
            }
            // 先删对象再删登记行：反过来的话对象删除失败会留下一条指向不存在对象的记录，
            // 那比多留一轮更难排查。
            ResponseDTO<String> deleted = fileStorageService.delete(key);
            if (deleted == null || !deleted.getOk()) {
                failed++;
                log.warn("暂存件对象删除失败，本轮跳过: key={}", key);
                continue;
            }
            fileDao.deleteById(candidate.getFileId());
            recycled++;
        }
        return String.format("暂存件回收：候选 %d，仍被业务引用 %d，对象删除失败 %d，已回收 %d（保留 %d 天）",
                candidates.size(), referenced, failed, recycled, retentionDays);
    }

    /**
     * 参数缺失或非法一律按默认窗口处理：清理任务不该因为运维填错一个数字而整轮跳过。
     */
    private static int parseRetentionDays(String param) {
        if (param == null || param.isBlank()) {
            return DEFAULT_RETENTION_DAYS;
        }
        try {
            int days = Integer.parseInt(param.trim());
            return days > 0 ? days : DEFAULT_RETENTION_DAYS;
        } catch (NumberFormatException e) {
            return DEFAULT_RETENTION_DAYS;
        }
    }

    /**
     * 子串匹配是刻意的保守判断：命中即不删，代价只是一轮不回收。
     * 附件列有「逗号分隔多 key」的形态，逐个精确切分反而容易因写法差异漏判。
     */
    private boolean referencedByBusinessColumn(String key) {
        for (String[] column : BUSINESS_KEY_COLUMNS) {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM " + column[0] + " WHERE " + column[1] + " IS NOT NULL"
                            + " AND position(? in " + column[1] + ") > 0",
                    Integer.class, key);
            if (count != null && count > 0) {
                return true;
            }
        }
        return false;
    }
}
