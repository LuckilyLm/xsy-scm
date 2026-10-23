package com.xsy.scm.common.json;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.system.employee.dao.EmployeeDao;
import net.lab1024.sa.base.common.enumeration.UserTypeEnum;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 后台操作人编号按员工表解析成人名，使用短期缓存避免逐字段查库。非员工用户及找不到的员工编号保持原文。
 */
@Component
@RequiredArgsConstructor
public class ScmOperatorNameResolver {

    /**
     * 缓存有效期。姓名变更不敏感，一分钟的延迟可以接受。
     */
    private static final long TTL_MILLIS = 60_000L;

    /**
     * {@code userType:userId} 的形状。只认这个形状，避免误改真实人名或其它标识。
     */
    private static final Pattern OPERATOR_ID = Pattern.compile("^(\\d+):(\\d+)$");

    private final EmployeeDao employeeDao;

    private volatile Map<Long, String> nameById = Map.of();

    private volatile long loadedAt = 0L;

    @PostConstruct
    void bind() {
        ScmOperatorNameSerializer.bind(this::display);
    }

    /**
     * 展示用姓名；认不出形状 / 解析不到时原样返回。
     */
    public String display(String operator) {
        Long employeeId = adminEmployeeId(operator);
        if (employeeId == null) {
            return operator;
        }
        return names().getOrDefault(employeeId, operator);
    }

    /**
     * 从 {@code userType:userId} 里取出管理端员工 id。
     *
     * <p>
     * 形状不对、或不是管理端员工（其它 userType 的数字 id 与员工 id 不在同一空间，拿员工表查会张冠李戴）时返回 {@code null}，由调用方原样回落。
     */
    static Long adminEmployeeId(String operator) {
        if (operator == null) {
            return null;
        }
        var matcher = OPERATOR_ID.matcher(operator);
        if (!matcher.matches()) {
            return null;
        }
        if (!String.valueOf(UserTypeEnum.ADMIN_EMPLOYEE.getValue()).equals(matcher.group(1))) {
            return null;
        }
        try {
            return Long.parseLong(matcher.group(2));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Map<Long, String> names() {
        if (System.currentTimeMillis() - loadedAt < TTL_MILLIS) {
            return nameById;
        }
        synchronized (this) {
            if (System.currentTimeMillis() - loadedAt < TTL_MILLIS) {
                return nameById;
            }
            nameById = employeeDao.selectList(null).stream().filter(employee -> employee.getActualName() != null)
                .collect(Collectors.toMap(employee -> employee.getEmployeeId(), employee -> employee.getActualName(),
                    (first, second) -> first));
            loadedAt = System.currentTimeMillis();
            return nameById;
        }
    }
}
