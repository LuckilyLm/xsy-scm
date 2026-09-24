package net.lab1024.sa.base.module.support.file.constant;

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
    COMMON(1, FileFolderTypeEnum.FOLDER_PRIVATE + "/common/", "通用"),

    /**
     * 公告
     */
    NOTICE(2, FileFolderTypeEnum.FOLDER_PRIVATE + "/notice/", "公告"),

    /**
     * 帮助中心
     */
    HELP_DOC(3, FileFolderTypeEnum.FOLDER_PRIVATE + "/help-doc/", "帮助中心"),

    /**
     * 意见反馈
     */
    FEEDBACK(4, FileFolderTypeEnum.FOLDER_PRIVATE + "/feedback/", "意见反馈"),

    /**
     * 公开图片：对象为 public-read，URL 静态可缓存、不过期，供面向客户的展示使用。
     * 与 private/* 的区别只有目录前缀一个来源，业务用它来表达「这份资产本来就不机密」。
     */
    PUBLIC_IMAGE(5, FileFolderTypeEnum.FOLDER_PUBLIC + "/image/", "公开图片"),

    /**
     * 上传后尚未绑定任何业务对象的暂存目录。
     *
     * <p>两段式生命周期的第一段：先入暂存，业务表单保存时才建 {@code t_file_relation}；
     * 超期仍无关系行的由 {@code FileScratchCleanupJob} 回收（见 docs/decisions.md
     * 「P0 基线收口裁决」第 14 条）。
     */
    SCRATCH(6, FileFolderTypeEnum.FOLDER_PRIVATE + "/common/scratch/", "未绑定暂存"),

    ;

    /**
     * 公用读取文件夹 public
     */
    public static final String FOLDER_PUBLIC = "public";

    /**
     * 私有读取文件夹 private， 私有文件夹会设置 只读权限，并且 文件url 拥有过期时间
     */
    public static final String FOLDER_PRIVATE = "private";

    private final Integer value;

    private final String folder;

    private final String desc;
}

