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
 * 操作人编号按员工表解析成人名，用短期缓存避免每个字段查一次库。非员工用户和找不到的编号原样返回。
 */
@Component
@RequiredArgsConstructor
public class ScmOperatorNameResolver {

    /** 缓存有效期。名字改了晚一分钟生效没关系。 */
    private static final long TTL_MILLIS = 60_000L;

    /** {@code userType:userId} 的格式。只认这个格式，避免把真名或别的标识改错。 */
    private static final Pattern OPERATOR_ID = Pattern.compile("^(\\d+):(\\d+)$");

    private final EmployeeDao employeeDao;

    private volatile Map<Long, String> nameById = Map.of();

    private volatile long loadedAt = 0L;

    @PostConstruct
    void bind() {
        ScmOperatorNameSerializer.bind(this::display);
    }

    /** 展示用姓名；形状不对或解析不到时原样返回。 */
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
     * 格式不对、或不是管理端员工时返回 {@code null}：其它 userType 的 id 和员工 id 不是一套编号，查员工表会张冠李戴。
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
