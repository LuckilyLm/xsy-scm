package net.lab1024.sa.base.module.support.file.constant;

import lombok.Getter;

/**
 * 文件关系授权的业务对象类型白名单。
 *
 * <p>枚举值必须与 {@code t_file_relation.biz_type} 的 CHECK 白名单逐字一致：
 * 数据库挡得住写入，注册表决定放行，两边错位会让附件静默不可读。
 */
@Getter
public enum FileRelationBizTypeEnum {

    /** OA 企业档案（营业执照等），biz_id = t_oa_enterprise.enterprise_id */
    ENTERPRISE("企业档案"),

    /** 公告附件，biz_id = t_notice.notice_id */
    NOTICE("公告"),

    /** 帮助文档附件，biz_id = t_help_doc.help_doc_id */
    HELP_DOC("帮助文档"),

    /** 意见反馈截图，biz_id = t_feedback.feedback_id */
    FEEDBACK("反馈"),

    /** SCM 商品图片（含仍留在私有目录的存量行），biz_id = product_image.spu_id */
    PRODUCT("商品");

    private final String desc;

    FileRelationBizTypeEnum(String desc) {
        this.desc = desc;
    }
}
