package com.xsy.scm.mall.home.domain.form;

import com.xsy.scm.common.validation.ScmEnumValue;
import com.xsy.scm.mall.home.constant.MallHomeBlockType;
import com.xsy.scm.mall.home.constant.MallHomeLinkType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.Data;

@Data
public class MallHomeBlockForm {
    @NotBlank(message = "组件标识不能为空")
    @Size(max = 64, message = "组件标识不能超过64个字符")
    private String key;
    @NotNull(message = "组件版本不能为空")
    @Min(value = 1, message = "组件版本必须为1")
    @Max(value = 1, message = "组件版本必须为1")
    private Integer schemaVersion;
    @NotNull(message = "组件类型不能为空")
    @ScmEnumValue(enumClass = MallHomeBlockType.class, message = "组件类型无效")
    private String type;
    @NotNull(message = "显示顺序不能为空")
    @Min(value = 0, message = "显示顺序不能小于0")
    @Max(value = 1000, message = "显示顺序不能超过1000")
    private Integer sortOrder;
    @NotNull(message = "组件状态不能为空")
    private Boolean enabled;
    @Size(max = 80, message = "标题不能超过80个字符")
    private String title;
    @Size(max = 160, message = "副标题不能超过160个字符")
    private String subtitle;
    @Size(max = 600, message = "公告内容不能超过600个字符")
    private String text;
    @Size(max = 300, message = "图片标识不能超过300个字符")
    private String imageFileKey;
    @ScmEnumValue(enumClass = MallHomeLinkType.class, message = "跳转类型无效")
    private String linkType;
    @Size(max = 500, message = "跳转目标不能超过500个字符")
    private String linkTarget;
    @Size(max = 20, message = "单个推荐组件最多20个商品规格")
    private List<Long> skuIds;
    @Size(max = 100, message = "单个组件最多指定100个客户")
    private List<Long> customerIds;
    private OffsetDateTime startAt;
    private OffsetDateTime endAt;
}
