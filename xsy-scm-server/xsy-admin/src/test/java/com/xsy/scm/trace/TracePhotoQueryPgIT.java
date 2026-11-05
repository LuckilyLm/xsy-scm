package com.xsy.scm.trace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.xsy.scm.common.ScmW3PgITBase;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.trace.service.TraceInspectionReportService;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 检测报告照片的只读筛选：上传日期按北京时间业务日折算，关键字匹配报告名或上传员工姓名。
 */
@DisplayName("检测报告照片筛选（PG IT）")
class TracePhotoQueryPgIT extends ScmW3PgITBase {

    @Autowired
    private TraceInspectionReportService reports;

    @Test
    @DisplayName("按上传日期与报告名筛选；开始日期晚于结束日期返回 41543")
    void filtersByUploadDateAndReportName() {
        insertPhoto("张三-十月蔬菜检测", "2026-10-01T10:00:00+08:00");
        insertPhoto("李四-十一月肉类检测", "2026-11-01T10:00:00+08:00");
        insertPhoto("王五-十月蔬菜检测", "2026-10-31T23:30:00+08:00");

        var october = reports.photos(null, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), null);
        assertThat(october).extracting(report -> report.getReportName())
            .containsExactlyInAnyOrder("张三-十月蔬菜检测", "王五-十月蔬菜检测");

        var byName = reports.photos(null, null, null, "李四");
        assertThat(byName).extracting(report -> report.getReportName()).containsExactly("李四-十一月肉类检测");

        assertThatThrownBy(() -> reports.photos(null, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 10, 1), null))
            .isInstanceOf(ScmBusinessException.class)
            .extracting(error -> ((ScmBusinessException) error).getErrorCode().getCode()).isEqualTo(41543);
    }

    @Test
    void uploaderNameMatchesEvenWhenFileNameContainsNoName() {
        String name = "报告上传员" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("UPDATE t_employee SET actual_name = ? WHERE employee_id = 1", name);
        insertPhoto("market-inspection-2026.jpg", "2026-11-01T10:00:00+08:00");
        assertThat(reports.photos(null, null, null, name)).extracting(report -> report.getReportName())
            .containsExactly("market-inspection-2026.jpg");
    }

    private void insertPhoto(String reportName, String createdAt) {
        jdbc.update("""
                INSERT INTO xsy_trace_inspection_report (lot_id, report_no, report_name, inspection_date,
                    inspection_org, conclusion, created_by, updated_by, operation_history, created_at)
                VALUES (NULL, ?, ?, NULL, NULL, 'UNDETERMINED', '1:1', '1:1', '[]'::jsonb, ?::timestamptz)
                """, "PHOTO-IT-" + UUID.randomUUID(), reportName, createdAt);
    }
}
