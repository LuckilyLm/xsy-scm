package com.xsy.scm.preference.filter;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.filter.ScmAdvancedFilterCatalog;
import com.xsy.scm.common.filter.ScmAdvancedFilterCatalog.Condition;
import com.xsy.scm.product.permission.ProductPermission;
import com.xsy.scm.product.support.ProductAdvancedFilters;
import com.xsy.scm.report.constant.ScmReportPermission;
import com.xsy.scm.report.support.ScmReportAdvancedFilters;
import com.xsy.scm.preference.filter.UserFilterPresetModel.Preset;
import com.xsy.scm.preference.filter.UserFilterPresetModel.Row;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.xsy.scm.common.error.ScmCommonErrorCode.VALIDATION_ERROR;
import static com.xsy.scm.common.error.ScmCommonErrorCode.VERSION_CONFLICT;

@Service
@RequiredArgsConstructor
public class UserFilterPresetService {
    private static final int MAX_PRESETS = 20;
    private static final TypeReference<List<Condition>> CONDITIONS = new TypeReference<>() {
    };
    private final UserFilterPresetDao userFilterPresetDao;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<Preset> list(String viewKey) {
        var fields = authorizedFields(viewKey);
        return userFilterPresetDao.list(ScmOperator.current(), viewKey).stream().map(row -> preset(row, fields))
                .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(UserFilterPresetModel.Create form) {
        var fields = authorizedFields(form.getViewKey());
        var conditions = form.getConditions();
        ScmAdvancedFilterCatalog.resolve(conditions, fields);
        String owner = ScmOperator.current();
        String name = form.getName().trim();
        if (name.isEmpty())
            throw new ScmBusinessException(VALIDATION_ERROR);
        userFilterPresetDao.lockOwner(owner, form.getViewKey());
        if (userFilterPresetDao.count(owner, form.getViewKey()) >= MAX_PRESETS) {
            throw new ScmBusinessException(VALIDATION_ERROR);
        }
        Row row = new Row();
        try {
            userFilterPresetDao.insert(owner, form.getViewKey(), name, objectMapper.writeValueAsString(conditions),
                    row);
        } catch (DuplicateKeyException duplicate) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        } catch (JsonProcessingException invalid) {
            throw new ScmBusinessException(VALIDATION_ERROR);
        }
        return row.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void remove(UserFilterPresetModel.Remove form) {
        authorizedFields(form.getViewKey());
        if (form.getId() == null || form.getId() <= 0) {
            throw new ScmBusinessException(VALIDATION_ERROR);
        }
        if (userFilterPresetDao.remove(form.getId(), ScmOperator.current(), form.getViewKey()) != 1) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
    }

    private Preset preset(Row row, List<ScmAdvancedFilterCatalog.Field> fields) {
        try {
            List<Condition> conditions = objectMapper.readValue(row.getConditionsJson(), CONDITIONS);
            ScmAdvancedFilterCatalog.resolve(conditions, fields);
            if (conditions.isEmpty())
                throw new ScmBusinessException(VALIDATION_ERROR);
            return new Preset(row.getId(), row.getName(), conditions, true, row.getCreatedAt());
        } catch (JsonProcessingException | ScmBusinessException outdated) {
            // 老方案不因字段权限或规则更新绕过校验；仍允许当前用户删除。
            return new Preset(row.getId(), row.getName(), List.of(), false, row.getCreatedAt());
        }
    }

    private List<ScmAdvancedFilterCatalog.Field> authorizedFields(String viewKey) {
        if (viewKey == null)
            throw new ScmBusinessException(VALIDATION_ERROR);
        return switch (viewKey) {
            case "PRODUCT" -> {
                StpUtil.checkPermission(ProductPermission.QUERY);
                yield ProductAdvancedFilters.FIELDS;
            }
            case "SALES_ORDER" -> {
                StpUtil.checkPermission(ScmReportPermission.SALES_QUERY);
                yield ScmReportAdvancedFilters.SALES_ORDER;
            }
            case "PURCHASE_PRODUCT" -> {
                StpUtil.checkPermission(ScmReportPermission.PURCHASE_QUERY);
                yield ScmReportAdvancedFilters.PURCHASE_PRODUCT;
            }
            default -> throw new ScmBusinessException(VALIDATION_ERROR);
        };
    }
}
