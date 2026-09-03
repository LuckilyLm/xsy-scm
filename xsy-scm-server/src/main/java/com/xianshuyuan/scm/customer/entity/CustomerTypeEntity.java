package com.xianshuyuan.scm.customer.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.OffsetDateTime;

@TableName("customer_type")
public class CustomerTypeEntity {
 @TableId(type=IdType.AUTO) private Long id; private String typeCode; private String name; private EnabledStatus status; @Version private Integer version; @TableLogic private Boolean deleted; private OffsetDateTime createdAt; private OffsetDateTime updatedAt; private String createdBy; private String updatedBy;
 public Long getId(){return id;} public void setId(Long v){id=v;} public String getTypeCode(){return typeCode;} public void setTypeCode(String v){typeCode=v;} public String getName(){return name;} public void setName(String v){name=v;} public EnabledStatus getStatus(){return status;} public void setStatus(EnabledStatus v){status=v;} public Integer getVersion(){return version;} public void setVersion(Integer v){version=v;} public Boolean getDeleted(){return deleted;} public void setDeleted(Boolean v){deleted=v;} public OffsetDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(OffsetDateTime v){createdAt=v;} public OffsetDateTime getUpdatedAt(){return updatedAt;} public void setUpdatedAt(OffsetDateTime v){updatedAt=v;} public String getCreatedBy(){return createdBy;} public void setCreatedBy(String v){createdBy=v;} public String getUpdatedBy(){return updatedBy;} public void setUpdatedBy(String v){updatedBy=v;}
}
