package net.lab1024.sa.admin.module.scm.order.service;

import net.lab1024.sa.admin.module.scm.order.dao.SalesOrderDao;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrderNumberGenerator {
    private final SalesOrderDao dao;

    public String order() {
        return format("SO", dao.nextOrder());
    }

    public String returned() {
        return format("RT", dao.nextReturn());
    }

    public String refund() {
        return format("RF", dao.nextRefund());
    }

    public static String format(String prefix, long number) {
        return prefix + java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE) + String.format(java.util.Locale.ROOT, "%06d", number);
    }
}
