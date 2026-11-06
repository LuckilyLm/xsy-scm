package com.xsy.scm.common.json;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import com.xsy.scm.admin.module.system.employee.dao.EmployeeDao;
import com.xsy.scm.base.common.enumeration.UserTypeEnum;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 操作人编号按员工表解析成人名，用短期缓存避免每个字段查一次库。
 *
 * <p>
 * 员工表是逻辑删除（{@code deleted_flag}），离职员的名字仍在表里，历史记录照样显示真名；只有整行被物理删掉时才回落到「已删除员工」。
 * 商城客户（{@code 2:…}）显示「商城客户」，其余认不出的形状原样返回，不在这里猜。
 */
@Component
@RequiredArgsConstructor
public class ScmOperatorNameResolver {

    /** 员工已被删除（或编号查不到）时的写法：页面只显示人名，不露 {@code userType:userId}。 */
    public static final String UNKNOWN_EMPLOYEE = "已删除员工";

    /** 商城客户操作人只给身份；无姓名可查时不编造。 */
    public static final String MALL_CUSTOMER = "商城客户";

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

    /**
     * 展示用姓名。
     *
     * <p>
     * {@code userType:userId} 形状的标识一定换成可读文案：员工还在就显示姓名，员工表里查不到就显示「已删除员工」。 原始编号不再出现在页面上；不是这个形状的值（已有名字、{@code SYSTEM}
     * 等）原样返回。
     */
    public String display(String operator) {
        Long employeeId = adminEmployeeId(operator);
        if (employeeId != null) {
            return names().getOrDefault(employeeId, UNKNOWN_EMPLOYEE);
        }
        // 商城客户下的单，createdBy / updatedBy 存的是 2:账号id；这里只能给出身份，姓名由订单日志单独解析
        return isMallCustomer(operator) ? MALL_CUSTOMER : operator;
    }

    /** {@code 2:账号id}：商城客户标识，不是员工编号，只用于给出身份文案。 */
    static boolean isMallCustomer(String operator) {
        if (operator == null) {
            return false;
        }
        String prefix = UserTypeEnum.MALL_CUSTOMER.getValue() + ":";
        return operator.startsWith(prefix) && operator.length() > prefix.length();
    }

    /**
     * 从 {@code userType:userId} 里取出管理端员工 id。
     *
     * <p>
     * 格式不对、或不是管理端员工时返回 {@code null}：其它 userType 的 id 和员工 id 不是一套编号，查员工表会张冠李戴。
     */
    public static Long adminEmployeeId(String operator) {
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
