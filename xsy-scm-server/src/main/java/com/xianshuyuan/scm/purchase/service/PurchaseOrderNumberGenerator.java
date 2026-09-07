package com.xianshuyuan.scm.purchase.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.*;

@Component
public class PurchaseOrderNumberGenerator {
    private final JdbcTemplate jdbc;

    public PurchaseOrderNumberGenerator(JdbcTemplate j) {
        jdbc = j;
    }

    public String next() {
        Long n = jdbc.queryForObject("SELECT nextval('purchase_order_no_seq')", Long.class);
        return "PO" + LocalDate.now().toString().replace("-", "") + String.format("%06d", n);
    }
}
