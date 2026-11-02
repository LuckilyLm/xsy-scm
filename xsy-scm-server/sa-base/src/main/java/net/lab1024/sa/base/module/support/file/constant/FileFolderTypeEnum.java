package net.lab1024.sa.base.module.support.file.constant;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.lab1024.sa.base.common.enumeration.BaseEnum;

/**
 * 文件服务 文件夹位置类型枚举类
 *
 */
@AllArgsConstructor
@Getter
public enum FileFolderTypeEnum implements BaseEnum {

    /**
     * 通用
     */
    COMMON(1, FileFolderTypeEnum.FOLDER_PUBLIC + "/common/", "通用"),

    /**
     * 公告
     */
    NOTICE(2, FileFolderTypeEnum.FOLDER_PUBLIC + "/notice/", "公告"),

    /**
     * 帮助中心
     */
    HELP_DOC(3, FileFolderTypeEnum.FOLDER_PUBLIC + "/help-doc/", "帮助中心"),

    /**
     * 意见反馈
     */
    FEEDBACK(4, FileFolderTypeEnum.FOLDER_PUBLIC + "/feedback/", "意见反馈"),

    /**
     * 公开图片：对象为 public-read，URL 静态可缓存、不过期，供面向客户的展示使用。
     */
    PUBLIC_IMAGE(5, FileFolderTypeEnum.FOLDER_PUBLIC + "/image/", "公开图片"),

    /**
     * 上传后尚未绑定任何业务对象的暂存目录。
     *
     * <p>两段式生命周期的第一段：先入暂存，业务表单保存时才建 {@code t_file_relation}；
     * 超期仍无关系行的由 {@code FileScratchCleanupJob} 回收（见 docs/decisions.md
     * 「P0 基线收口裁决」第 14 条）。
     */
    SCRATCH(6, FileFolderTypeEnum.FOLDER_PUBLIC + "/common/scratch/", "未绑定暂存"),

    ;

    /**
     * 公用读取文件夹 public：对象为 public-read，URL 静态可缓存、不过期。
     */
    public static final String FOLDER_PUBLIC = "public";

    /**
     * 私有读取文件夹 private：对象为私有读写，URL 带过期时间。
     *
     * <p>2026-11-02 起所有新上传目录都改为 {@code public/}；当时没有业务需要私有附件，
     * 身份证、营业执照等敏感资料后续再新增 {@code private/} 目录。这里保留常量是为了让
     * 存量 {@code private/} 文件继续按原授权读取，并给未来的敏感目录留出落点。
     */
    public static final String FOLDER_PRIVATE = "private";

    /**
     * 存量私有目录白名单：只用于继续读取改造前上传的文件。
     *
     * <p>新上传不再进入这些前缀；未知的 {@code private/} 子目录仍然按失败关闭处理，
     * 不能仅凭前缀就到手读取权。
     */
    public static final List<String> LEGACY_PRIVATE_FOLDERS = List.of(
            FOLDER_PRIVATE + "/common/",
            FOLDER_PRIVATE + "/notice/",
            FOLDER_PRIVATE + "/help-doc/",
            FOLDER_PRIVATE + "/feedback/");

    private final Integer value;

    private final String folder;

    private final String desc;
}

