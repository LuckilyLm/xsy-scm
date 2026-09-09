package com.xianshuyuan.scm.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xianshuyuan.scm.auth.security.SystemUserDetails;
import com.xianshuyuan.scm.common.api.PageData;
import com.xianshuyuan.scm.common.exception.BusinessException;
import com.xianshuyuan.scm.common.exception.ErrorCode;
import com.xianshuyuan.scm.system.SystemErrorCodes;
import com.xianshuyuan.scm.system.converter.DepartmentConverter;
import com.xianshuyuan.scm.system.dto.*;
import com.xianshuyuan.scm.system.entity.DepartmentEntity;
import com.xianshuyuan.scm.system.mapper.DepartmentMapper;
import com.xianshuyuan.scm.system.mapper.SystemUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class DepartmentService {
    private final DepartmentMapper departments;
    private final SystemUserMapper users;
    private final DepartmentConverter converter;
    private final ObjectMapper json;

    public PageData<DepartmentResponse> list(DepartmentQuery query) {
        var wrapper = new LambdaQueryWrapper<DepartmentEntity>();
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            String keyword = query.getKeyword().strip();
            wrapper.and(w -> w.like(DepartmentEntity::getCode, keyword).or().like(DepartmentEntity::getName, keyword));
        }
        wrapper.eq(query.getStatus() != null, DepartmentEntity::getStatus, query.getStatus());
        wrapper.eq(query.getParentId() != null, DepartmentEntity::getParentId, query.getParentId());
        wrapper.orderByAsc(DepartmentEntity::getSortOrder, DepartmentEntity::getId);
        var page = departments.selectPage(new Page<>(query.getPage(), query.getPageSize()), wrapper);
        return new PageData<>(page.getRecords().stream().map(converter::toResponse).toList(),
                page.getCurrent(), page.getSize(), page.getTotal());
    }

    public DepartmentResponse detail(long id) {
        return converter.toResponse(require(departments.selectById(id)));
    }

    public List<DepartmentResponse> descendants(long id) {
        detail(id);
        return departments.descendants(id).stream().map(converter::toResponse).toList();
    }

    public List<DepartmentTreeResponse> tree() {
        var rows = departments.selectList(new LambdaQueryWrapper<DepartmentEntity>()
                .orderByAsc(DepartmentEntity::getSortOrder, DepartmentEntity::getId));
        Map<Long, DepartmentTreeResponse> nodes = new LinkedHashMap<>();
        rows.forEach(row -> nodes.put(row.getId(), converter.toTreeResponse(row)));
        List<DepartmentTreeResponse> roots = new ArrayList<>();
        for (var node : nodes.values()) {
            Set<Long> seen = new HashSet<>();
            for (var ancestor = node; ancestor != null; ancestor = nodes.get(ancestor.getParentId())) {
                if (!seen.add(ancestor.getId())) throw new BusinessException(SystemErrorCodes.DEPARTMENT_CYCLE);
            }
            if (node.getParentId() == null) roots.add(node);
            else {
                var parent = nodes.get(node.getParentId());
                if (parent == null) throw new BusinessException(SystemErrorCodes.INVALID_DEPARTMENT);
                parent.getChildren().add(node);
            }
        }
        return roots;
    }

    @Transactional
    public DepartmentResponse create(CreateDepartmentRequest request, Authentication actor) {
        users.lockSecurityWrites();
        validateParent(null, request.parentId());
        validateCode(null, request.code().strip());
        var department = new DepartmentEntity();
        department.setCode(request.code().strip());
        department.setName(request.name().strip());
        department.setParentId(request.parentId());
        department.setSortOrder(request.sortOrder());
        department.setStatus("ENABLED");
        department.setVersion(0);
        department.setDeleted(false);
        department.setCreatedAt(OffsetDateTime.now());
        department.setUpdatedAt(department.getCreatedAt());
        department.setCreatedBy(actor.getName());
        department.setUpdatedBy(actor.getName());
        departments.insert(department);
        var result = converter.toResponse(department);
        audit(department.getId(), actor, "DEPARTMENT_CREATE", null, result);
        return result;
    }

    @Transactional
    public DepartmentResponse update(long id, UpdateDepartmentRequest request, Authentication actor) {
        users.lockSecurityWrites();
        var department = locked(id, request.version());
        validateParent(id, request.parentId());
        validateCode(id, request.code().strip());
        var before = converter.toResponse(department);
        department.setCode(request.code().strip());
        department.setName(request.name().strip());
        department.setParentId(request.parentId());
        department.setSortOrder(request.sortOrder());
        save(department, actor);
        var result = detail(id);
        audit(id, actor, "DEPARTMENT_UPDATE", before, result);
        // Current authentication contains user ID/roles/permissions, not department ancestry.
        // Moving/renaming a department changes neither assignments nor authorization identity.
        // Future department data scopes must invalidate affected identities when introduced.
        return result;
    }

    @Transactional
    public DepartmentResponse changeStatus(long id, DepartmentStatusRequest request, Authentication actor) {
        users.lockSecurityWrites();
        var department = locked(id, request.version());
        if ("DISABLED".equals(request.status())) rejectReferences(id);
        else validateParent(id, department.getParentId());
        var before = converter.toResponse(department);
        department.setStatus(request.status());
        save(department, actor);
        var result = detail(id);
        audit(id, actor, "DEPARTMENT_STATUS", before, result);
        return result;
    }

    @Transactional
    public void delete(long id, int version, Authentication actor) {
        users.lockSecurityWrites();
        var department = locked(id, version);
        rejectReferences(id);
        var before = converter.toResponse(department);
        department.setDeleted(true);
        save(department, actor);
        audit(id, actor, "DEPARTMENT_DELETE", before, null);
    }

    private void validateCode(Long id, String code) {
        if (departments.selectCount(new LambdaQueryWrapper<DepartmentEntity>()
                .eq(DepartmentEntity::getCode, code).ne(id != null, DepartmentEntity::getId, id)) > 0)
            throw new BusinessException(ErrorCode.DATA_CONFLICT);
    }

    private void rejectReferences(long id) {
        // The shared advisory lock precedes department locks and serializes all user assignments.
        // Both disabled children and disabled users are still live references; never cascade.
        if (departments.countActiveChildren(id) > 0 || departments.countAssignedUsers(id) > 0)
            throw new BusinessException(SystemErrorCodes.DEPARTMENT_IN_USE);
    }

    private void validateParent(Long id, Long parentId) {
        Set<Long> seen = new HashSet<>();
        if (id != null) seen.add(id);
        for (Long next = parentId; next != null;) {
            if (!seen.add(next)) throw new BusinessException(SystemErrorCodes.DEPARTMENT_CYCLE);
            var parent = departments.lockActive(next);
            if (parent == null || !"ENABLED".equals(parent.getStatus()))
                throw new BusinessException(SystemErrorCodes.INVALID_DEPARTMENT);
            next = parent.getParentId();
        }
    }

    private DepartmentEntity locked(long id, int version) {
        var department = require(departments.lockActive(id));
        if (!Objects.equals(department.getVersion(), version)) throw new BusinessException(ErrorCode.DATA_CONFLICT);
        return department;
    }

    private DepartmentEntity require(DepartmentEntity department) {
        if (department == null) throw new BusinessException(SystemErrorCodes.DEPARTMENT_NOT_FOUND);
        return department;
    }

    private void save(DepartmentEntity department, Authentication actor) {
        department.setUpdatedBy(actor.getName());
        if (departments.updateDepartment(department) != 1) throw new BusinessException(ErrorCode.DATA_CONFLICT);
    }

    private void audit(long id, Authentication actor, String operation, DepartmentResponse before, DepartmentResponse after) {
        Long actorId = actor.getPrincipal() instanceof SystemUserDetails details ? details.getUser().userId() : null;
        try {
            departments.insertAudit(id, actorId, actor.getName(), operation,
                    json.writeValueAsString(before), json.writeValueAsString(after));
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Cannot serialize department audit", failure);
        }
    }
}
