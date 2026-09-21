package net.lab1024.sa.admin.module.scm.common;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import lombok.Data;
import net.lab1024.sa.admin.test.PgITDatabase;
import net.lab1024.sa.base.config.MybatisPlusConfig;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * G1 gate: real PostgreSQL + production interceptor; no permanent fixture table.
 */
class ScmOptimisticLockTest {
    @Data
    @TableName("w1_version_probe")
    public static class Probe {
        @TableId
        private Long id;
        private String name;
        @Version
        private Integer version;
    }

    public interface ProbeDao extends BaseMapper<Probe> {
    }

    @Test
    void staleUpdateIsRejectedAndPersistedVersionAdvances() throws Exception {
        PGSimpleDataSource source = new PGSimpleDataSource();
        source.setURL(PgITDatabase.url());
        source.setUser(PgITDatabase.user());
        source.setPassword(PgITDatabase.password());
        MybatisConfiguration config = new MybatisConfiguration();
        config.setEnvironment(new Environment("g1", new JdbcTransactionFactory(), source));
        config.addInterceptor(new MybatisPlusConfig().paginationInterceptor());
        config.addMapper(ProbeDao.class);
        try (var session = new MybatisSqlSessionFactoryBuilder().build(config).openSession(false)) {
            try (var statement = session.getConnection().createStatement()) {
                statement.execute("CREATE TEMP TABLE w1_version_probe (id bigint primary key, name text, version integer not null) ON COMMIT DROP");
                statement.execute("INSERT INTO w1_version_probe VALUES (1, 'original', 0)");
            }
            var dao = session.getMapper(ProbeDao.class);
            Probe first = dao.selectById(1L);
            // Simulate a second reader, not the same first-level-cache object.
            session.clearCache();
            Probe stale = dao.selectById(1L);
            assertThat(stale).isNotSameAs(first);
            first.setName("first");
            assertThat(dao.updateById(first)).isEqualTo(1);
            stale.setName("stale");
            assertThat(dao.updateById(stale)).as("stale version must not overwrite first update").isZero();
            Probe saved = dao.selectById(1L);
            assertThat(saved.getVersion()).isEqualTo(1);
            assertThat(saved.getName()).isEqualTo("first");
            session.rollback();
        }
    }
}
