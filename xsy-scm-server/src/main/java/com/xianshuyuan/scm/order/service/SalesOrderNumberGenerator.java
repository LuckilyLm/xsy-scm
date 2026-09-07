package com.xianshuyuan.scm.order.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.function.Supplier;

@Component
public class SalesOrderNumberGenerator {
    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private final JdbcTemplate jdbcTemplate;
    private final Supplier<LocalDate> dateSupplier;

    @Autowired
    public SalesOrderNumberGenerator(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, LocalDate::now);
    }

    SalesOrderNumberGenerator(JdbcTemplate jdbcTemplate, Supplier<LocalDate> dateSupplier) {
        this.jdbcTemplate = jdbcTemplate;
        this.dateSupplier = dateSupplier;
    }

    public String next() {
        Long value = Objects.requireNonNull(jdbcTemplate.queryForObject("SELECT nextval('sales_order_no_seq')", Long.class));
        return "SO" + DATE.format(dateSupplier.get()) + String.format("%06d", value);
    }
}
