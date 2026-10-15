package net.lab1024.sa.base.module.support.operatelog;

import org.apache.commons.lang3.StringUtils;

import java.util.LinkedHashSet;
import java.util.Set;

/** 根据日志请求参数与 URL 判定所属的 SCM 业务模块，筛选规则要与数据库查询保持一致。 */
public final class OperateLogBusinessType {

    public static final String PRODUCT = "PRODUCT";

    public static final String CUSTOMER = "CUSTOMER";

    public static final String DELIVERY_ROUTE = "DELIVERY_ROUTE";

    /**
     * 线路 URL 段前缀，与列表筛选里的 {@code /scm/delivery/routes/} 同值。
     */
    private static final String DELIVERY_ROUTE_URL_PREFIX = "/scm/delivery/routes/";

    private OperateLogBusinessType() {
    }

    /**
     * 识别一条日志涉及的领域；无法识别时返回空集，调用方按「通用日志权限」处理。
     *
     * @return 去重且顺序稳定的领域类型集合，永不为 {@code null}
     */
    public static Set<String> recognize(String param, String url) {
        Set<String> types = new LinkedHashSet<>(3);
        if (StringUtils.contains(param, "\"spuId\":")) {
            types.add(PRODUCT);
        }
        if (StringUtils.contains(param, "\"customerId\":")) {
            types.add(CUSTOMER);
        }
        if (StringUtils.contains(url, DELIVERY_ROUTE_URL_PREFIX)) {
            types.add(DELIVERY_ROUTE);
        }
        return types;
    }
}
