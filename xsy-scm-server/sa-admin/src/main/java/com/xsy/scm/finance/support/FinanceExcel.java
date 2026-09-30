package com.xsy.scm.finance.support;

import com.xsy.scm.common.json.ScmOffsetDateTimeSerializer;
import cn.idev.excel.FastExcel;
import jakarta.servlet.http.HttpServletResponse;
import net.lab1024.sa.base.common.util.SmartResponseUtil;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Synchronous finance Excel output with fixed decimal and timestamp text formats. */
public final class FinanceExcel {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private FinanceExcel() {
    }

    public static void write(HttpServletResponse response, String fileName, String sheetName, List<String> titles,
            List<List<Object>> rows) throws IOException {
        SmartResponseUtil.setDownloadFileHeader(response, fileName, null);
        List<List<String>> head = new ArrayList<>(titles.size());
        for (String title : titles) {
            head.add(List.of(title));
        }
        FastExcel.write(response.getOutputStream()).head(head).autoCloseStream(Boolean.FALSE).sheet(sheetName)
                .doWrite(rows);
    }

    public static List<Object> row(List<String> titles, Object... cells) {
        if (cells.length != titles.size()) {
            throw new IllegalStateException("导出行列数(" + cells.length + ")与表头列数(" + titles.size() + ")不一致");
        }
        List<Object> row = new ArrayList<>(cells.length);
        for (Object cell : cells) {
            row.add(cell(cell));
        }
        return row;
    }

    private static Object cell(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof OffsetDateTime dateTime) {
            return dateTime.atZoneSameInstant(ScmOffsetDateTimeSerializer.DISPLAY_ZONE).format(DATE_TIME);
        }
        if (value instanceof LocalDate date) {
            return date.format(DateTimeFormatter.ISO_LOCAL_DATE);
        }
        if (value instanceof BigDecimal number) {
            return number.toPlainString();
        }
        return value;
    }
}
