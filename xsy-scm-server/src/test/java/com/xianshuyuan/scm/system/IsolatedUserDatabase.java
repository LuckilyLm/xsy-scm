package com.xianshuyuan.scm.system;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.sql.DriverManager;
import java.util.UUID;

/** Owns only randomly named test schemas; never changes shared application users. */
abstract class IsolatedUserDatabase {
    private static final String SCHEMA = "user_it_" + UUID.randomUUID().toString().replace("-", "");
    private static final String EXTERNAL = SCHEMA + "_external";
    private static final String URL = System.getenv("XSY_TEST_DB_URL");
    private static final String USER = System.getenv("XSY_DB_USERNAME");
    private static final String PASSWORD = System.getenv("XSY_DB_PASSWORD");
    static {
        try (var connection = DriverManager.getConnection(URL, USER, PASSWORD); var statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + SCHEMA);
            statement.execute("CREATE SCHEMA " + EXTERNAL);
            // A usable ambient administrator in another schema must not affect the invariant.
            statement.execute("CREATE TABLE " + EXTERNAL + ".sys_user (id bigint, username text, administrator boolean, deleted boolean, status text, password_hash text)");
            try (var insert = connection.prepareStatement("INSERT INTO " + EXTERNAL + ".sys_user VALUES (1,'external_admin',true,false,'ENABLED',?)")) {
                insert.setString(1, new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("test-password-123"));
                insert.executeUpdate();
            }
        } catch (java.sql.SQLException failure) { throw new IllegalStateException("Cannot initialize isolated user test schemas", failure); }
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try (var connection = DriverManager.getConnection(URL, USER, PASSWORD); var statement = connection.createStatement()) {
                statement.execute("DROP SCHEMA " + SCHEMA + " CASCADE");
                statement.execute("DROP SCHEMA " + EXTERNAL + " CASCADE");
            } catch (java.sql.SQLException ignored) { /* Only disposable test-owned schemas may remain. */ }
        }));
    }
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.hikari.schema", () -> SCHEMA);
        properties.add("spring.flyway.default-schema", () -> SCHEMA);
        properties.add("spring.flyway.schemas", () -> SCHEMA);
    }
}
