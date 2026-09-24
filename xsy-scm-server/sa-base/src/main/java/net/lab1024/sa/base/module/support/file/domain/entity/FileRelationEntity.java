package net.lab1024.sa.base.module.support.file.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 业务对象与文件附件的授权关系：有权看对象 ⇒ 能看它绑定的文件。
 */
@Data
@TableName(value = "t_file_relation")
public class FileRelationEntity {

    @TableId(type = IdType.AUTO)
    private Long relationId;

    /**
     * t_file.file_key 的值，不建外键
     */
    private String fileKey;

    /**
     * {@link net.lab1024.sa.base.module.support.file.constant.FileRelationBizTypeEnum} 的名称
     */
    private String bizType;

    /**
     * 该业务类型下的对象主键
     */
    private Long bizId;

    private Boolean deletedFlag;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
