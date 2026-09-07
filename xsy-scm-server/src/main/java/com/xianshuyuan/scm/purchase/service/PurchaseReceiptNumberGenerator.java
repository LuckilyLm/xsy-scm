package com.xianshuyuan.scm.purchase.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.time.LocalDate;

@Component
public class PurchaseReceiptNumberGenerator {
    private final JdbcTemplate jdbc;
    public PurchaseReceiptNumberGenerator(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public String next() { Long n=jdbc.queryForObject("SELECT nextval('purchase_receipt_no_seq')",Long.class); return "PR"+LocalDate.now().toString().replace("-","")+String.format("%06d",n); }
}
