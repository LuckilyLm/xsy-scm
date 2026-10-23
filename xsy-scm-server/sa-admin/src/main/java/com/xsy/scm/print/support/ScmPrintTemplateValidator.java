package com.xsy.scm.print.support;

import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.print.constant.ScmPrintDocumentTypeEnum;
import com.xsy.scm.print.constant.ScmPrintErrorCode;
import com.xsy.scm.print.constant.ScmPrintField;
import com.xsy.scm.print.constant.ScmPrintOrientationEnum;
import com.xsy.scm.print.constant.ScmPrintPaperEnum;
import com.xsy.scm.print.domain.model.ScmPrintTemplateModel;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 打印模板的字段与结构必须在允许范围内，未知字段直接拒绝。标题和备注不允许 HTML 标记，模板不能执行脚本。
 */
public final class ScmPrintTemplateValidator {

    private static final int MAX_TITLE_LENGTH = 40;

    private static final int MAX_FOOTER_LENGTH = 200;

    private ScmPrintTemplateValidator() {
    }

    /**
     * 校验并返回整理后的模型（去重、trim）。
     *
     * @throws ScmBusinessException
     *             任一规则不满足；错误码区分「字段不被支持」与「模型不合法」两类
     */
    public static ScmPrintTemplateModel validate(ScmPrintDocumentTypeEnum type, ScmPrintTemplateModel model) {
        ScmPrintTemplateModel normalized = new ScmPrintTemplateModel();
        normalized.setTitle(requirePlainText(model.getTitle(), "标题", MAX_TITLE_LENGTH, true));
        normalized.setFooterNote(requirePlainText(model.getFooterNote(), "页脚备注", MAX_FOOTER_LENGTH, false));
        normalized.setPaper(requirePaper(model.getPaper()));
        normalized.setOrientation(requireOrientation(normalized.getPaper(), model.getOrientation()));
        normalized.setHeaderFields(requireFields(model.getHeaderFields(), type, FieldScope.HEADER));
        normalized.setColumns(requireFields(model.getColumns(), type, FieldScope.COLUMN));
        if (normalized.getColumns().isEmpty()) {
            throw new ScmBusinessException(ScmPrintErrorCode.COLUMN_REQUIRED);
        }
        if (Boolean.TRUE.equals(model.isShowTotals()) && type.getTotals().isEmpty()) {
            // 该类型没有合计字段却要求打印合计：与其静默打出一行空表，不如直接拒绝
            throw new ScmBusinessException(ScmPrintErrorCode.MODEL_INVALID);
        }
        normalized.setShowTotals(model.isShowTotals());
        return normalized;
    }

    private enum FieldScope {
        HEADER,
        COLUMN
    }

    private static String requirePlainText(String value, String label, int maxLength, boolean required) {
        String text = value == null ? null : value.trim();
        if (text == null || text.isEmpty()) {
            if (required) {
                throw new ScmBusinessException(ScmPrintErrorCode.MODEL_INVALID);
            }
            return null;
        }
        if (text.length() > maxLength) {
            throw new ScmBusinessException(ScmPrintErrorCode.MODEL_INVALID);
        }
        if (text.indexOf('<') >= 0 || text.indexOf('>') >= 0) {
            throw new ScmBusinessException(ScmPrintErrorCode.TEXT_NOT_PLAIN);
        }
        return text;
    }

    private static String requirePaper(String paper) {
        if (!ScmPrintPaperEnum.isSupported(paper)) {
            throw new ScmBusinessException(ScmPrintErrorCode.MODEL_INVALID);
        }
        return paper;
    }

    private static String requireOrientation(String paper, String orientation) {
        if (!ScmPrintOrientationEnum.isSupported(orientation)) {
            throw new ScmBusinessException(ScmPrintErrorCode.MODEL_INVALID);
        }
        ScmPrintPaperEnum paperEnum = ScmPrintPaperEnum.valueOf(paper);
        if (ScmPrintOrientationEnum.LANDSCAPE.name().equals(orientation) && !paperEnum.isLandscapeAllowed()) {
            // 80mm 定宽卷纸没有横向：允许配置只会产出一张打印机不认的版面
            throw new ScmBusinessException(ScmPrintErrorCode.PAPER_ORIENTATION_INVALID);
        }
        return orientation;
    }

    /** 逐项校验字段并去重；重复的 key 说明调用方拼错了，直接拒绝，不做静默去重。 */
    private static List<String> requireFields(List<String> keys, ScmPrintDocumentTypeEnum type, FieldScope scope) {
        List<String> normalized = new ArrayList<>();
        if (keys == null) {
            return normalized;
        }
        Set<String> seen = new LinkedHashSet<>();
        for (String raw : keys) {
            if (raw == null) {
                continue;
            }
            String key = raw.trim();
            if (key.isEmpty()) {
                continue;
            }
            ScmPrintField field = scope == FieldScope.HEADER ? type.headerField(key) : type.column(key);
            if (field == null) {
                throw new ScmBusinessException(ScmPrintErrorCode.FIELD_NOT_ALLOWED);
            }
            if (!seen.add(key)) {
                throw new ScmBusinessException(ScmPrintErrorCode.MODEL_INVALID);
            }
            normalized.add(key);
        }
        return normalized;
    }
}
