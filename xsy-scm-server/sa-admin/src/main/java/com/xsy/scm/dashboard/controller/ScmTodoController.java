package com.xsy.scm.dashboard.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import com.xsy.scm.dashboard.domain.vo.ScmTodoVO;
import com.xsy.scm.dashboard.permission.DashboardPermission;
import com.xsy.scm.dashboard.service.ScmTodoQueryService;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 业务待办只读聚合入口。
 *
 * <p>
 * {@code scm:todo:query} 只授权访问待办本身，不隐含库存 / 收货 / 审批 / 配送权限； 服务端按「待办权限 ∩ 领域权限」逐卡计算，无权卡片省略，动作仍由各领域接口鉴权。
 * 本接口不写任何业务表、不发消息、不落快照。
 */
@RestController
@RequestMapping("/scm/dashboard")
@Tag(name = "SCM 业务待办")
@RequiredArgsConstructor
public class ScmTodoController {

    @Resource
    private ScmTodoQueryService scmTodoQueryService;

    @GetMapping("/todo")
    @SaCheckPermission(DashboardPermission.TODO_QUERY)
    public ResponseDTO<List<ScmTodoVO>> todo() {
        return ResponseDTO.ok(scmTodoQueryService.currentEmployeeTodos());
    }
}
