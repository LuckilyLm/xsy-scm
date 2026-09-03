package com.xianshuyuan.scm.customer.vo;
import com.xianshuyuan.scm.customer.entity.*; import java.time.OffsetDateTime; import java.util.List;
public record CustomerResponse(Long id,Integer version,String customerCode,String name,Long customerTypeId,String customerTypeName,EnabledStatus status,VisibilityPolicy visibilityPolicy,List<CustomerSkuVisibilityResponse> visibilities,OffsetDateTime updatedAt) {}
