package com.xianshuyuan.scm.system;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class MenuMigrationIT {
    @Test void v20ConvertsPagePreservingIdentityJoinsAndLegacyDirectoryMetadata() {
        String schema = "menu_migration_" + UUID.randomUUID().toString().replace("-", "");
        var source = new DriverManagerDataSource(System.getenv("XSY_TEST_DB_URL"), System.getenv("XSY_DB_USERNAME"), System.getenv("XSY_DB_PASSWORD"));
        var jdbc = new JdbcTemplate(source);
        try {
            Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema).target("19").load().migrate();
            jdbc.update("insert into " + schema + ".sys_menu(id,type,name,path,route_key) values(90001,'PAGE','Page','/products','products'),(90002,'DIRECTORY','Legacy','/legacy',null)");
            jdbc.update("insert into " + schema + ".sys_role_menu(id,role_id,menu_id) values(90003,90004,90001)");
            Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema).load().migrate();
            assertThat(jdbc.queryForObject("select type from " + schema + ".sys_menu where id=90001", String.class)).isEqualTo("MENU");
            assertThat(jdbc.queryForObject("select menu_id from " + schema + ".sys_role_menu where id=90003 and deleted=false", Long.class)).isEqualTo(90001);
            assertThat(jdbc.queryForObject("select path from " + schema + ".sys_menu where id=90002", String.class)).isEqualTo("/legacy");
            assertThatThrownBy(() -> jdbc.update("insert into " + schema + ".sys_menu(type,name,path,route_key) values('PAGE','Bad','/bad','bad')"))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update("insert into " + schema + ".sys_menu(type,name) values('MENU','Bad')"))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        } finally { jdbc.execute("drop schema if exists " + schema + " cascade"); }
    }
}
