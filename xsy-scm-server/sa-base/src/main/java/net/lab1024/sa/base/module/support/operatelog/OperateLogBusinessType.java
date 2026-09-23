package net.lab1024.sa.base.module.support.operatelog;

import org.apache.commons.lang3.StringUtils;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 操作日志所属 SCM 业务领域的识别口径。
 *
 * <p>日志表没有业务对象列，领域归属只能从写入时留下的 {@code param} / {@code url} 推断，
 * 因此这里的判定规则必须与 {@code OperateLogMapper.xml} 的结构化业务筛选保持一致：
 * 精确 JSON 键名判定商品、客户，线路 ID 走 {@code @PathVariable} 故判定 URL 段。
 *
 * <p>只用于「宁可多要求一个读取权」的授权收紧：误判成某领域只会额外要求该领域读取权限，
 * 不会放宽任何权限。
 */
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
