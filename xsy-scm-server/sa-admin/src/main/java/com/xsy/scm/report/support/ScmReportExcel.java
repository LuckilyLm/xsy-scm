package com.xsy.scm.report.support;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import com.xsy.scm.common.json.ScmOffsetDateTimeSerializer;

import cn.idev.excel.FastExcel;
import jakarta.servlet.http.HttpServletResponse;
import net.lab1024.sa.base.common.util.SmartResponseUtil;

/**
 * 报表 Excel 导出使用同一套字段和金额口径，并遵守当前用户的数据范围与导出权限。
 */
public final class ScmReportExcel {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private ScmReportExcel() {
    }

    /**
     * 写出一张表。
     *
     * @param titles
     *            表头，顺序即列顺序
     * @param rows
     *            行数据，每行元素数必须与 {@code titles} 等长（用 {@link #row} 构造可自检）
     */
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

    /**
     * 构造一行，并保证列数与表头一致。
     *
     * <p>
     * 列数不齐会让 Excel 出现整列错位而不报错，那是导出里最难被发现的一类缺陷。
     */
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

    /**
     * 报表导出时，日期先转换成北京时间文本，金额与数量按完整定点字符串输出，避免 Excel 自动舍入或使用科学计数法。
     */
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

    /**
     * null 与零必须可辨：null 导出成空单元格，不能变成 0。
     */
    public static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
