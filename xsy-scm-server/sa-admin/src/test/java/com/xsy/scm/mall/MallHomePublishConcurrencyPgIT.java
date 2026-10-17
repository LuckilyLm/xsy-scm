package com.xsy.scm.mall;

import com.xsy.scm.common.ScmW5PgITBase;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.mall.home.domain.form.MallHomeBlockForm;
import com.xsy.scm.mall.home.domain.form.MallHomeDraftSaveForm;
import com.xsy.scm.mall.home.domain.form.MallHomePublishForm;
import com.xsy.scm.mall.home.service.MallHomeLayoutService;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import net.lab1024.sa.admin.module.system.login.domain.RequestEmployee;
import net.lab1024.sa.base.common.enumeration.UserTypeEnum;
import net.lab1024.sa.base.common.util.SmartRequestUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/** Each worker has its own PostgreSQL transaction; temporary schema keeps global publication rows untouched. */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MallHomePublishConcurrencyPgIT extends ScmW5PgITBase {
    @Autowired
    private MallHomeLayoutService layouts;
    private String schema;

    @Test
    void concurrentPublishCreatesOneReleaseAndOnePointerAdvance() throws Exception {
        schema = "mall_home_it_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("CREATE SCHEMA " + schema);
        try {
            for (String table : List.of("xsy_mall_home_draft", "xsy_mall_home_release", "xsy_mall_home_pointer")) {
                jdbc.execute("CREATE TABLE " + schema + "." + table + " (LIKE " + table + " INCLUDING ALL)");
            }
            jdbc.execute("INSERT INTO " + schema + ".xsy_mall_home_draft(id) VALUES (1)");
            jdbc.execute("INSERT INTO " + schema + ".xsy_mall_home_pointer(id) VALUES (1)");
            var draft = inSchema(layouts::draft);
            var form = new MallHomeDraftSaveForm();
            form.setVersion(draft.version());
            var block = new MallHomeBlockForm();
            block.setKey("notice");
            block.setSchemaVersion(1);
            block.setType("ANNOUNCEMENT");
            block.setSortOrder(0);
            block.setEnabled(true);
            block.setText("并发发布");
            block.setLinkType("NONE");
            form.setBlocks(List.of(block));
            var saved = inSchema(() -> layouts.save(form));
            var publish = new MallHomePublishForm();
            publish.setDraftVersion(saved.version());
            publish.setPointerVersion(saved.pointerVersion());

            var ready = new CountDownLatch(2);
            var go = new CountDownLatch(1);
            var pool = Executors.newFixedThreadPool(2);
            try {
                var first = pool.submit(() -> publishAsAdmin(ready, go, publish));
                var second = pool.submit(() -> publishAsAdmin(ready, go, publish));
                assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
                go.countDown();
                var outcomes = List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
                assertThat(outcomes.stream().filter(outcome -> outcome.releaseId() != null).toList()).hasSize(1);
                assertThat(outcomes.stream().filter(outcome -> outcome.error() != null).toList()).hasSize(1)
                        .allSatisfy(outcome -> assertThat(outcome.error()).isInstanceOf(ScmBusinessException.class));
                var current = inSchema(layouts::draft);
                assertThat(current.pointerVersion()).isEqualTo(saved.pointerVersion() + 1);
                assertThat(current.currentReleaseId()).isEqualTo(outcomes.stream()
                        .map(Outcome::releaseId).filter(id -> id != null).findFirst().orElseThrow());
                assertThat(jdbc.queryForObject("SELECT count(*) FROM " + schema + ".xsy_mall_home_release",
                        Integer.class)).isEqualTo(1);
            } finally {
                go.countDown();
                pool.shutdownNow();
                assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
            }
        } finally {
            jdbc.execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }

    private Outcome publishAsAdmin(CountDownLatch ready, CountDownLatch go, MallHomePublishForm form) {
        RequestEmployee employee = new RequestEmployee();
        employee.setEmployeeId(1L);
        employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
        employee.setAdministratorFlag(true);
        SmartRequestUtil.setRequestUser(employee);
        try {
            ready.countDown();
            if (!go.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Concurrent publish did not start");
            try {
                return new Outcome(inSchema(() -> layouts.publish(form)), null);
            } catch (RuntimeException failure) {
                return new Outcome(null, failure);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return new Outcome(null, new IllegalStateException(interrupted));
        } finally {
            SmartRequestUtil.remove();
        }
    }

    private <T> T inSchema(Supplier<T> operation) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            jdbc.execute("SET LOCAL search_path TO " + schema + ", public");
            return operation.get();
        });
    }

    private record Outcome(Long releaseId, RuntimeException error) {
    }
}
