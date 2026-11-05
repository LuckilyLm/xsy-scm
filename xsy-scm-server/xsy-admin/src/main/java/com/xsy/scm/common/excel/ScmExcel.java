package com.xsy.scm.common.excel;

import cn.idev.excel.FastExcel;
import com.xsy.scm.common.json.ScmOffsetDateTimeSerializer;
import jakarta.servlet.http.HttpServletResponse;
import com.xsy.scm.base.common.util.XsyResponseUtil;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.error.ScmCommonErrorCode;

/**
 * 同步导出 Excel：日期按北京时间输出；正常金额保留数值类型供计算，超出Excel精度的存量值保留文本。
 */
public final class ScmExcel {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private ScmExcel() {
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
        XsyResponseUtil.setDownloadFileHeader(response, fileName, null);
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
            return number.stripTrailingZeros().precision() <= 15 ? number : number.toPlainString();
        }
        return value;
    }

    /**
     * 写入客户模板的可计算数值；空值保留空白，超出Excel精度时拒绝整份文件，避免公式静默漏算。
     *
     * @param target
     *            必填目标单元格
     * @param value
     *            四位业务数值，null表示未核实；最多15位有效数字
     * @throws ScmBusinessException
     *             数字超出Excel可安全计算范围
     */
    public static void setNumber(Cell target, BigDecimal value) {
        if (value == null) {
            target.setBlank();
        } else if (value.stripTrailingZeros().precision() > 15) {
            throw new ScmBusinessException(ScmCommonErrorCode.VALIDATION_ERROR,
                "金额或数量超过Excel的15位有效数字计算范围，请缩小数据范围或使用明细台账导出");
        } else {
            target.setCellValue(value.doubleValue());
        }
    }
}
