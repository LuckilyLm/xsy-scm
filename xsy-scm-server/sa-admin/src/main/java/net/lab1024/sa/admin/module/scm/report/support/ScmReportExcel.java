package net.lab1024.sa.admin.module.scm.report.support;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import net.lab1024.sa.admin.module.scm.common.json.ScmOffsetDateTimeSerializer;

import cn.idev.excel.FastExcel;
import jakarta.servlet.http.HttpServletResponse;
import net.lab1024.sa.base.common.util.SmartResponseUtil;

/**
 * 报表的同步 Excel 导出写出（Finance R0 计划 §38）。
 *
 * <p>用 FastExcel 的动态表头而不是给每张报表建一个 {@code @ExcelProperty} 模型：
 * 报表列是「口径」的一部分，与查询 SQL 一一对应，做成两个平行结构后两边很容易长歪。
 * 因此每个导出在同一处给出 titles 与 rows，列顺序不可能分叉。
 *
 * <p>单元格一律写字符串：金额/数量的精度由后端序列化前就定死，
 * 交给 Excel 的数值格式会按 Excel 的显示规则收敛位数。
 */
public final class ScmReportExcel {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private ScmReportExcel() {
    }

    /**
     * 写出一张表。
     *
     * @param titles  表头，顺序即列顺序
     * @param rows    行数据，每行元素数必须与 {@code titles} 等长（用 {@link #row} 构造可自检）
     */
    public static void write(HttpServletResponse response, String fileName, String sheetName,
                             List<String> titles, List<List<Object>> rows) throws IOException {
        SmartResponseUtil.setDownloadFileHeader(response, fileName, null);
        List<List<String>> head = new ArrayList<>(titles.size());
        for (String title : titles) {
            head.add(List.of(title));
        }
        FastExcel.write(response.getOutputStream())
                .head(head)
                .autoCloseStream(Boolean.FALSE)
                .sheet(sheetName)
                .doWrite(rows);
    }

    /**
     * 构造一行，并保证列数与表头一致。
     *
     * <p>列数不齐会让 Excel 出现整列错位而不报错，那是导出里最难被发现的一类缺陷。
     */
    public static List<Object> row(List<String> titles, Object... cells) {
        if (cells.length != titles.size()) {
            throw new IllegalStateException("导出行列数(" + cells.length + ")与表头列数("
                    + titles.size() + ")不一致");
        }
        List<Object> row = new ArrayList<>(cells.length);
        for (Object cell : cells) {
            row.add(cell(cell));
        }
        return row;
    }

    /**
     * 单元格归一化。
     *
     * <p>时间必须在服务端转成字符串：FastExcel 没有 {@code OffsetDateTime} 的 Converter，
     * 直接塞进去会在写出的那一刻抛 {@code ExcelWriteDataConvertException}（HTTP 500，
     * 用户看到的是「点了导出没反应」）。字符串也顺带保证导出与接口显示同一套北京时间格式。
     *
     * <p>{@code BigDecimal} 走 {@code toPlainString()}：科学计数法形态与位数交给文本固定下来，
     * 不让 Excel 按单元格默认格式重新收敛小数位（R0 的 4 位定点口径）。
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

    /** null 与零必须可辨：null 导出成空单元格，不能变成 0。 */
    public static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
