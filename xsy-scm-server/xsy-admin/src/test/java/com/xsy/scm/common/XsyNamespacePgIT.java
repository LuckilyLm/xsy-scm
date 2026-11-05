package com.xsy.scm.common;

import com.xsy.scm.admin.XsyApplication;
import com.xsy.scm.base.module.support.job.core.XsyJob;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/** 已保存的任务配置必须能找到当前执行类，包名调整不能让任务失效。 */
class XsyNamespacePgIT extends ScmW6PgITBase {
    @Test
    void jobClassNamesResolveAfterMigration() throws Exception {
        var names = jdbc.queryForList("SELECT job_class FROM t_smart_job WHERE deleted_flag = false", String.class);
        assertThat(names).isNotEmpty();
        for (String name : names) {
            assertThat(name).startsWith("com.xsy.scm.");
            assertThat(XsyJob.class.isAssignableFrom(Class.forName(name))).as(name).isTrue();
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM t_smart_job WHERE job_class LIKE ?", Integer.class,
            "%module.support.job.sample.XsyJobSample%" )).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM t_smart_job WHERE job_class=?", Integer.class,
            "com.xsy.scm.admin.module.system.support.FileScratchCleanupJob")).isEqualTo(1);
    }

    @Test
    void singleScanRootIncludesEveryModule() {
        assertThat(XsyApplication.COMPONENT_SCAN).isEqualTo("com.xsy.scm");
        assertThat(XsyApplication.COMPONENT_SCAN_PATHS).containsExactly("com.xsy.scm");
    }
}
