package com.xsy.scm.report;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.report.constant.ScmOrderExceptionTypeEnum;
import com.xsy.scm.report.dao.ScmOrderExceptionReadDao;
import com.xsy.scm.report.domain.form.ScmOrderExceptionQueryForm;
import java.time.OffsetDateTime;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.AdminApplication;
import net.lab1024.sa.admin.test.PgITPaths;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/** 使用独立空库检查整个异常联合查询的列、类型和空授权失败关闭。 */
@SpringBootTest(classes = AdminApplication.class, properties = {
        "project.log-directory=" + PgITPaths.DEFAULT_LOG_DIR,
        "file.storage.local.upload-path=" + PgITPaths.DEFAULT_UPLOAD_PATH,
        "file.storage.local.url-prefix=http://127.0.0.1:18082",
        "logging.level.root=WARN"})
class ScmOrderExceptionReadDaoPgIT {
    @Autowired
    private ScmOrderExceptionReadDao dao;

    @Test
    void emptyScopeCannotExposeExceptionsAndBothStockProjectionPathsCompile() {
        var query = new ScmOrderExceptionQueryForm();
        var start = OffsetDateTime.now().minusDays(7);
        var end = OffsetDateTime.now().plusDays(1);
        var types = Arrays.stream(ScmOrderExceptionTypeEnum.values()).map(Enum::name).toList();
        assertThat(dao.query(new Page<>(1, 20, false), query, start, end, null, types, false, false))
                .isEmpty();
        assertThat(dao.query(new Page<>(1, 20, false), query, start, end, null, types, false, true))
                .isEmpty();
        assertThat(dao.summary(query, start, end, null, types, false)).isEmpty();
    }
}
