package com.xianshuyuan.scm.system.dto;

import lombok.Getter;
import lombok.Setter;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class DepartmentTreeResponse {
    private Long id;
    private Long parentId;
    private String code;
    private String name;
    private Integer sortOrder;
    private String status;
    private Integer version;
    private List<DepartmentTreeResponse> children = new ArrayList<>();
}
