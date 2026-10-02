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
 * 模板模型校验：受控模板的**唯一**准入点。
 *
 * <p>
 * 校验的取向是「失败关闭」：任何不在白名单里的 key、任何结构不明的东西一律拒收，不做
 * 「忽略未知字段」这种宽容处理 —— 宽容会让一份写错的模板静默变成另一张单据，
 * 而用户以为它生效了。
 *
 * <p>
 * 文本字段（标题、页脚备注）额外拒绝尖括号：渲染侧本来就会转义，但在这里直接拒绝能把
 * 「模板不是 HTML」这条边界写在接口契约里，而不是留给下一个人去猜。
 */
public final class ScmPrintTemplateValidator {

    private static final int MAX_TITLE_LENGTH = 40;

    private static final int MAX_FOOTER_LENGTH = 200;

    private ScmPrintTemplateValidator() {
    }

    /**
     * 校验并返回**归一化后**的模型（去重、trim）。
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
        HEADER, COLUMN
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

    /**
     * 逐项校验白名单并去重。重复 key 不做静默去重后放行 —— 重复说明调用方拼错了，直接拒绝。
     */
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
