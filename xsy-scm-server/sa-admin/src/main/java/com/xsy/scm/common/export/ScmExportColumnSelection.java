package com.xsy.scm.common.export;

import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.scope.ScmDataScopeException;
import cn.idev.excel.FastExcel;
import jakarta.servlet.http.HttpServletResponse;
import net.lab1024.sa.base.common.util.SmartResponseUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.xsy.scm.common.error.ScmCommonErrorCode.VALIDATION_ERROR;

/**
 * 自定义导出列只从原查询结果里挑字段，不允许客户端拼 SQL 字段名。
 */
public final class ScmExportColumnSelection {
    private ScmExportColumnSelection() {
    }

    public record Column(String key, String title, boolean costRestricted) {
        public Column(String key, String title) {
            this(key, title, false);
        }
    }

    public record Selection(List<String> titles, List<Integer> positions) {
        public List<List<Object>> project(List<List<Object>> originalRows) {
            List<List<Object>> result = new ArrayList<>(originalRows.size());
            for (var row : originalRows) {
                List<Object> values = new ArrayList<>(positions.size());
                for (int index : positions) {
                    if (index >= row.size())
                        throw new IllegalStateException("导出行字段数与列配置不一致");
                    Object value = row.get(index);
                    values.add(value == null ? "" : value);
                }
                result.add(values);
            }
            return result;
        }
    }

    public static void writeSelected(HttpServletResponse response, String filename, String sheetName, Selection choice,
        List<List<Object>> fullRows) throws IOException {
        SmartResponseUtil.setDownloadFileHeader(response, filename, null);
        var heads = choice.titles().stream().map(List::of).toList();
        FastExcel.write(response.getOutputStream()).head(heads).autoCloseStream(Boolean.FALSE).sheet(sheetName)
            .doWrite(choice.project(fullRows));
    }

    public static Selection resolve(List<String> requested, List<Column> schema, boolean canViewCost) {
        if (schema.isEmpty())
            throw new IllegalStateException("导出字段列表不能为空");
        if (requested != null && (requested.isEmpty() || requested.size() > 30)) {
            throw new ScmBusinessException(VALIDATION_ERROR);
        }
        Map<String, Integer> positionByKey = new HashMap<>();
        for (int i = 0; i < schema.size(); i++) {
            if (positionByKey.putIfAbsent(schema.get(i).key(), i) != null) {
                throw new IllegalStateException("导出字段列表存在重复标识");
            }
        }
        List<String> keys = requested == null ? schema.stream().map(Column::key).toList() : requested;
        Set<String> seen = new HashSet<>();
        List<String> titles = new ArrayList<>(keys.size());
        List<Integer> positions = new ArrayList<>(keys.size());
        for (String key : keys) {
            Integer index = positionByKey.get(key);
            if (index == null || !seen.add(key))
                throw new ScmBusinessException(VALIDATION_ERROR);
            Column column = schema.get(index);
            if (column.costRestricted() && !canViewCost) {
                // 不传字段时沿用旧的全列导出；显式选到受限列就拒绝。
                if (requested != null)
                    throw new ScmDataScopeException();
            }
            titles.add(column.title());
            positions.add(index);
        }
        return new Selection(List.copyOf(titles), List.copyOf(positions));
    }
}
