package com.xsy.scm.common;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 集成测试夹具的展示名称。
 *
 * <p>夹具造出来的名字会出现在页面、导出文件和审计日志里（例如「谁改了哪张单」），
 * 用随机十六进制串拼出来的名字看起来像密钥而不是数据，截图和演示时无法使用。
 * 这里统一给成真实业务里会出现的名字：品牌 + 门店号。
 *
 * <p>唯一性靠递增序号，同一 JVM 内不会重复。跨进程并发跑同一套用例仍可能撞名，
 * 但那种场景本来就应该跑在独立 schema 上。
 */
final class PgITFixtureNames {

    /**
     * 品牌名池：覆盖生鲜供应链里常见的自营 / 合作品牌，避免所有夹具都叫同一个名字。
     */
    private static final String[] BRANDS = {"鲜蔬园", "绿源", "禾丰", "优鲜达", "鲜果记", "青禾"};

    /**
     * 操作人姓名池：审计日志里的「操作人」列直接用这些名字。
     */
    private static final String[] OPERATORS = {"张伟", "李静", "王海涛", "陈晓芸", "周敏"};

    private static final AtomicInteger BRAND_SEQ = new AtomicInteger();

    private static final AtomicInteger OPERATOR_SEQ = new AtomicInteger();

    private PgITFixtureNames() {
    }

    /**
     * 本次用例的品牌前缀，形如 {@code 绿源137}。
     *
     * <p>调用方习惯拼成「前缀 + 商品 / 仓 / 线路 / 客户」，因此这里只给品牌 + 号段，
     * 拼出来就是「绿源137仓」「绿源137商品」这类内部叫法。
     */
    static String brand() {
        int n = BRAND_SEQ.incrementAndGet();
        return BRANDS[n % BRANDS.length] + (100 + n % 900);
    }

    /**
     * 本次用例的操作人姓名。
     */
    static String operator() {
        int n = OPERATOR_SEQ.incrementAndGet();
        return OPERATORS[n % OPERATORS.length];
    }
}
