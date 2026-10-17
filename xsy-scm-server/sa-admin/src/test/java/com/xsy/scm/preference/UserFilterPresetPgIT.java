package com.xsy.scm.preference;

import com.xsy.scm.preference.filter.UserFilterPresetDao;
import com.xsy.scm.preference.filter.UserFilterPresetModel.Row;
import java.util.UUID;
import net.lab1024.sa.admin.AdminApplication;
import net.lab1024.sa.admin.test.PgITPaths;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = AdminApplication.class, properties = {
        "project.log-directory=" + PgITPaths.DEFAULT_LOG_DIR,
        "file.storage.local.upload-path=" + PgITPaths.DEFAULT_UPLOAD_PATH,
        "file.storage.local.url-prefix=http://127.0.0.1:18082",
        "logging.level.root=WARN"})
@Transactional
class UserFilterPresetPgIT {
    @Autowired
    private UserFilterPresetDao dao;

    @Test
    void aUsersPresetIsInvisibleAndUndeletableByOtherUsersOrViews() {
        String owner = "1:" + UUID.randomUUID();
        String other = "1:" + UUID.randomUUID();
        String json = "[{\"key\":\"keyword\",\"operator\":\"CONTAINS\",\"value\":\"白菜\"}]";
        assertThat(dao.lockOwner(owner, "PRODUCT")).isEqualTo(1);
        Row row = new Row();
        assertThat(dao.insert(owner, "PRODUCT", "我的商品", json, row)).isEqualTo(1);
        assertThat(row.getId()).isPositive();
        assertThat(dao.list(owner, "PRODUCT")).singleElement()
                .satisfies(found -> {
                    assertThat(found.getName()).isEqualTo("我的商品");
                    assertThat(found.getConditionsJson()).contains("keyword", "白菜");
                });
        assertThat(dao.list(other, "PRODUCT")).isEmpty();
        assertThat(dao.list(owner, "SALES_ORDER")).isEmpty();
        assertThat(dao.remove(row.getId(), other, "PRODUCT")).isZero();
        assertThat(dao.remove(row.getId(), owner, "SALES_ORDER")).isZero();
        assertThat(dao.count(owner, "PRODUCT")).isEqualTo(1);
        assertThat(dao.remove(row.getId(), owner, "PRODUCT")).isEqualTo(1);
    }
}
