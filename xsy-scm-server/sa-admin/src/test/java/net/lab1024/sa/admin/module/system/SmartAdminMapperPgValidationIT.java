package net.lab1024.sa.admin.module.system;


import net.lab1024.sa.admin.test.PgITPaths;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import net.lab1024.sa.admin.AdminApplication;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.reflection.ParamNameResolver;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SmartAdmin 系统层 Mapper SQL 的 PostgreSQL 可解析性校验。
 *
 * <p><b>为什么需要这个测试：</b>mapper XML 只做过函数名层面的 MySQL → PostgreSQL 转换
 * （{@code INSTR -> STRPOS}、{@code DATE_FORMAT -> CAST}），发现不了「MySQL 下合法、PostgreSQL 下非法」
 * 的标识符问题——最典型的是驼峰列名：MySQL 标识符大小写不敏感，PostgreSQL 未加引号会折叠为小写。
 *
 * <p><b>做法：</b>不执行、不改数据。对 {@code SqlSessionFactory} 里注册的每一条 MappedStatement：
 * <ol>
 *   <li>用 MyBatis 自己的 {@link ParamNameResolver} 构造一份「所有字段非空」的参数
 *       （这样所有 {@code <if>} 分支都会渲染出来，等于最大覆盖面）；</li>
 *   <li>取 {@link BoundSql} 得到带 {@code ?} 占位符的最终 SQL；</li>
 *   <li>把 {@code ?} 换成 {@code $n} 后交给真实 PostgreSQL 执行 {@code PREPARE}——
 *       PREPARE 会走完整的 parse + 语义分析，表名/列名/函数名/类型不合法会直接报错，
 *       但不会执行、不会写数据。</li>
 * </ol>
 *
 * <p>这条路径能同时覆盖动态 SQL 的每个 {@code <if>}/{@code <foreach>} 分支，
 * 是「全量扫描 active SQL 是否仍有 MySQL 残留」的可执行证据。
 */
@SpringBootTest(classes = AdminApplication.class, properties = {
        "project.log-directory=" + PgITPaths.DEFAULT_LOG_DIR,
        "file.storage.local.upload-path=" + PgITPaths.DEFAULT_UPLOAD_PATH,
        "file.storage.local.url-prefix=http://127.0.0.1:18082",
        "logging.level.root=WARN"})
class SmartAdminMapperPgValidationIT {

    /**
     * 每个用例独立命名的 prepared statement，避免重名。
     */
    private static final AtomicInteger SEQ = new AtomicInteger();

    /**
     * 已知且已归档的失败（棘轮基线）。任何新增失败都会让本测试变红。
     *
     * <p>当前只剩 1 条：
     * <ul>
     *   <li>{@code DEAD_CODE}：语句本身非法（MySQL 下同样非法），但当前调用链不可达；
     *       修它等于发明未验证的行为，故只登记不改。</li>
     * </ul>
     */
    private static final Map<String, String> KNOWN_FAILURES = Map.of(
            "net.lab1024.sa.base.module.support.serialnumber.dao.SerialNumberRecordDao"
                    + ".selectRecordIdBySerialNumberIdAndDate",
            "DEAD_CODE: 选 serial_number_record_id，该表上游与本项目都没有此列（表无主键），无调用方"
    );

    /**
     * {@code PREPARE} 无法推断裸参数类型（{@code CONCAT('%', $1, '%')} 这类），
     * 而 JDBC 实际执行时参数带明确类型。按报错里点名的参数号补一次 {@code ::text} 重试，
     * 把「PREPARE 的静态类型推断限制」与「真正的 MySQL 残留」区分开。
     */
    private static void prepare(Statement st, String name, String sql) throws Exception {
        String current = sql;
        // PostgreSQL 只报第一个无法推断类型的参数，补一次可能又暴露下一个，故循环处理。
        // 上限取参数出现次数：写死小整数会让「参数很多的长 SQL」在真正报错之前就退出循环，
        // 把 PREPARE 的推断轮数不足误报成 MySQL 残留。
        int rounds = (int) java.util.regex.Pattern.compile("\\$\\d+")
                .matcher(sql).results().count();
        for (int round = 0; round <= rounds; round++) {
            try {
                st.execute("PREPARE " + name + " AS " + current);
                return;
            } catch (Exception e) {
                String msg = String.valueOf(e.getMessage());
                java.util.regex.Matcher m = java.util.regex.Pattern
                        .compile("could not determine data type of parameter \\$(\\d+)").matcher(msg);
                if (!m.find()) {
                    throw e;
                }
                String patched = current.replaceAll("\\$" + m.group(1) + "\\b",
                        "\\$" + m.group(1) + "::text");
                if (patched.equals(current)) {
                    throw e;
                }
                current = patched;
            }
        }
        throw new IllegalStateException("prepare retry exhausted: " + name);
    }

    @Autowired
    private SqlSessionFactory sqlSessionFactory;

    @Autowired
    private DataSource dataSource;

    @Test
    void everyMapperStatementIsParseableByPostgres() throws Exception {
        List<String> failures = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        List<String> knownFailures = new ArrayList<>();
        int validated = 0;

        // 通过 MapperRegistry 枚举，而不是 Configuration.getMappedStatements()：
        // 后者的 values() 里混有 MyBatis StrictMap 的「短名歧义」占位对象，无法安全强转。
        for (Class<?> mapperInterface
                : sqlSessionFactory.getConfiguration().getMapperRegistry().getMappers()) {
            for (Method method : mapperInterface.getMethods()) {
                if (method.getDeclaringClass() == Object.class) {
                    continue;
                }
                String id = mapperInterface.getName() + "." + method.getName();
                if (!sqlSessionFactory.getConfiguration().hasStatement(id)) {
                    continue;
                }
                MappedStatement ms = sqlSessionFactory.getConfiguration().getMappedStatement(id);
                BoundSql boundSql;
                try {
                    boundSql = ms.getBoundSql(sampleArgs(mapperInterface, method));
                } catch (Throwable t) {
                    skipped.add(id + "  -> " + t.getClass().getSimpleName() + ": " + t.getMessage());
                    continue;
                }
                String prepared = toPositional(boundSql.getSql());
                try (Connection conn = dataSource.getConnection(); Statement st = conn.createStatement()) {
                    String name = "xsy_audit_" + SEQ.incrementAndGet();
                    prepare(st, name, prepared);
                    st.execute("DEALLOCATE " + name);
                    validated++;
                } catch (Exception e) {
                    String id2 = id + "#" + java.util.Arrays.toString(method.getParameterTypes());
                    String reason = KNOWN_FAILURES.get(id);
                    if (reason != null) {
                        knownFailures.add(id + "  [" + reason + "]");
                        continue;
                    }
                    failures.add(id2 + "\n      SQL : " + oneLine(prepared)
                            + "\n      ERR : " + oneLine(String.valueOf(e.getMessage())));
                }
            }
        }

        System.out.println("[mapper-pg-validation] validated=" + validated
                + " failed=" + failures.size() + " knownFailure=" + knownFailures.size()
                + " skipped=" + skipped.size());
        for (String s : knownFailures) {
            System.out.println("[mapper-pg-validation][known] " + s);
        }
        for (String s : skipped) {
            System.out.println("[mapper-pg-validation][skipped] " + s);
        }

        assertThat(failures)
                .as("以下 mapper 语句无法被 PostgreSQL 解析（含 MySQL 残留标识符/函数）:\n%s",
                        String.join("\n", failures))
                .isEmpty();
        // 棘轮：已知失败集合不得扩大
        assertThat(knownFailures).as("已知失败基线应恰好为 %s 条", KNOWN_FAILURES.size())
                .hasSize(KNOWN_FAILURES.size());
        assertThat(validated).as("应至少校验到一批语句").isGreaterThan(100);

        // 任何在 getBoundSql 抛错的语句都会整条跳过 PG 解析，failures 仍为空、用例照样绿，
        // 所以 skipped 不能只打印。被跳过的语句全部来自 MyBatis-Plus BaseMapper 继承的泛型 CRUD
        // （SQL 由框架生成，不是本仓库手写的）。
        // 真正要防的是「有人在 mapper XML 里写了与继承方法同名的自定义语句」——
        // 那会被这套按方法名枚举的机制静默跳过，所以钉两条：
        //   1) 每条跳过项的方法名必须在 BaseMapper 自身的方法集合内（运行时取，不另抄一份清单）；
        //   2) 跳过总数不得超过基线，新增跳过项时要显式调整这个数字才会过。
        var inheritedCrudMethods = new java.util.HashSet<String>();
        for (Method base : com.baomidou.mybatisplus.core.mapper.BaseMapper.class.getMethods()) {
            inheritedCrudMethods.add(base.getName());
        }
        for (String s : skipped) {
            String statementId = s.substring(0, s.indexOf("  -> "));
            String methodName = statementId.substring(statementId.lastIndexOf('.') + 1);
            assertThat(inheritedCrudMethods)
                    .as("手写 mapper 语句不允许被跳过（它是 BaseMapper 之外的自定义语句，"
                            + "按方法名枚举会漏掉）: %s", statementId)
                    .contains(methodName);
        }
        // BaseMapper 自动注册的方法不做独立 SQL PREPARE，因此这里跳过统计。
        // 当前允许的最大跳过数为 608；新增跳过项必须显式调整这个数字。
        // 上面第 1) 条已保证这些跳过项的方法名全部属于 BaseMapper，因此它们不可能是手写语句被漏掉——
        // 手写语句一旦混进来会先在那条断言上点名失败。
        assertThat(skipped).as("跳过项基线 608 条，只增不减需显式确认").hasSizeLessThanOrEqualTo(608);
    }

    // ------------------------------------------------------------------
    // 参数构造
    // ------------------------------------------------------------------

    private Object sampleArgs(Class<?> mapperInterface, Method target) {
        Parameter[] params = target.getParameters();
        Object[] args = new Object[params.length];
        for (int i = 0; i < params.length; i++) {
            // MyBatis-Plus 的 BaseMapper 方法形如 insert(T entity)：T 在字节码里擦除成 Object，
            // 必须用具体 Mapper 接口的泛型实参回填，否则造不出实体参数。
            Type declared = params[i].getParameterizedType();
            Class<?> raw = params[i].getType();
            if (declared instanceof TypeVariable<?> tv) {
                Type bound = baseMapperTypeArguments(mapperInterface).get(tv);
                if (bound instanceof Class<?> c) {
                    declared = c;
                    raw = c;
                } else if (bound instanceof ParameterizedType pt && pt.getRawType() instanceof Class<?> c) {
                    declared = pt;
                    raw = c;
                }
            }
            args[i] = sample(declared, raw, 0);
        }
        if (args.length == 0) {
            return null;
        }
        return new ParamNameResolver(sqlSessionFactory.getConfiguration(), target).getNamedParams(args);
    }

    /**
     * 解析 {@code interface X extends BaseMapper<Entity>} 里的 {@code T -> Entity}。
     */
    private static Map<TypeVariable<?>, Type> baseMapperTypeArguments(Class<?> mapperInterface) {
        Map<TypeVariable<?>, Type> map = new HashMap<>();
        for (Type t : mapperInterface.getGenericInterfaces()) {
            if (!(t instanceof ParameterizedType pt) || !(pt.getRawType() instanceof Class<?> raw)) {
                continue;
            }
            TypeVariable<?>[] vars = raw.getTypeParameters();
            Type[] actual = pt.getActualTypeArguments();
            for (int i = 0; i < vars.length && i < actual.length; i++) {
                map.put(vars[i], actual[i]);
            }
        }
        return map;
    }

    /**
     * 为任意类型造一个非空样本值；bean 递归填充字段，保证 OGNL 的 {@code != null} 分支全部打开。
     */
    @SuppressWarnings("unchecked")
    private Object sample(Type genericType, Class<?> rawType, int depth) {
        if (rawType == null || depth > 3) {
            return null;
        }
        if (rawType == String.class || CharSequence.class.isAssignableFrom(rawType)) {
            return "a";
        }
        if (rawType == Long.class || rawType == long.class) {
            return 1L;
        }
        if (rawType == Integer.class || rawType == int.class) {
            return 1;
        }
        if (rawType == Short.class || rawType == short.class) {
            return (short) 1;
        }
        if (rawType == Byte.class || rawType == byte.class) {
            return (byte) 1;
        }
        if (rawType == Double.class || rawType == double.class) {
            return 1.0d;
        }
        if (rawType == Float.class || rawType == float.class) {
            return 1.0f;
        }
        if (rawType == Boolean.class || rawType == boolean.class) {
            return Boolean.TRUE;
        }
        if (rawType == BigDecimal.class) {
            return new BigDecimal("1");
        }
        if (rawType == Character.class || rawType == char.class) {
            return 'a';
        }
        if (rawType == LocalDate.class) {
            return LocalDate.now();
        }
        if (rawType == LocalDateTime.class) {
            return LocalDateTime.now();
        }
        if (rawType == LocalTime.class) {
            return LocalTime.NOON;
        }
        if (rawType == java.util.Date.class) {
            return new java.util.Date();
        }
        if (Page.class.isAssignableFrom(rawType) || IPage.class.isAssignableFrom(rawType)) {
            return new Page<>(1, 10);
        }
        if (Wrapper.class.isAssignableFrom(rawType)) {
            return new QueryWrapper<>();
        }
        // 数据范围值对象是不可变的（final 字段 + 静态工厂），通用 bean 递归填不出它的 ids。
        // 于是所有把 scope 传进 <choose> 的 mapper 都会在 getBoundSql 抛错、整条被跳过，
        // 等价于让「按 SCM 数据范围收窄」的那段 SQL 永远不进 PostgreSQL 校验。
        // 这里用 of(...) 造一个非空、非全量范围，使 NOT IN <foreach> 分支真正渲染出来。
        if (rawType == com.xsy.scm.common.scope.ScmValueScope.class) {
            return com.xsy.scm.common.scope.ScmValueScope.of(java.util.List.of(1L));
        }
        // SCM 数据范围上下文同样是不可变的（final 字段 + 只有全参构造器，且工厂方法不是 public）。
        // 它是全部「按范围收窄」DAO 的入参：造不出来就会被 getBoundSql 抛错整条跳过，
        // 等于数据范围那段 SQL 从来没进过 PostgreSQL 校验。用非空、非全量范围把它造出来。
        if (rawType == com.xsy.scm.common.scope.ScmDataScopeContext.class) {
            Object scopedValue = com.xsy.scm.common.scope.ScmValueScope.of(java.util.List.of(1L));
            try {
                Constructor<?> ctor = rawType.getDeclaredConstructors()[0];
                ctor.setAccessible(true);
                Object[] args = new Object[ctor.getParameterCount()];
                for (int k = 0; k < args.length; k++) {
                    Class<?> param = ctor.getParameterTypes()[k];
                    if (param == Long.class || param == long.class) {
                        args[k] = 1L;
                    } else if (param == boolean.class || param == Boolean.class) {
                        args[k] = Boolean.TRUE;
                    } else {
                        args[k] = scopedValue;
                    }
                }
                return ctor.newInstance(args);
            } catch (Exception ignored) {
                return null;
            }
        }
        if (rawType == java.io.Serializable.class || rawType == Comparable.class) {
            return 1L;
        }
        if (rawType.isEnum()) {
            Object[] constants = rawType.getEnumConstants();
            return constants.length > 0 ? constants[0] : null;
        }
        if (rawType.isArray()) {
            Object arr = java.lang.reflect.Array.newInstance(rawType.getComponentType(), 1);
            java.lang.reflect.Array.set(arr, 0, sample(rawType.getComponentType(),
                    rawType.getComponentType(), depth + 1));
            return arr;
        }
        if (Collection.class.isAssignableFrom(rawType)) {
            Class<?> elementType = Long.class;
            if (genericType instanceof ParameterizedType pt) {
                Type arg = pt.getActualTypeArguments()[0];
                if (arg instanceof Class<?> c) {
                    elementType = c;
                } else if (arg instanceof ParameterizedType npt && npt.getRawType() instanceof Class<?> c) {
                    elementType = c;
                }
            }
            Object element = sample(elementType, elementType, depth + 1);
            if (Set.class.isAssignableFrom(rawType)) {
                Set<Object> set = new LinkedHashSet<>();
                set.add(element);
                return set;
            }
            List<Object> list = new ArrayList<>();
            list.add(element);
            return list;
        }
        if (Map.class.isAssignableFrom(rawType)) {
            Map<String, Object> map = new HashMap<>();
            map.put("a", "a");
            return map;
        }
        if (rawType == Object.class || rawType == java.io.Serializable.class
                || rawType == Comparable.class || rawType.isInterface()) {
            return null;
        }
        // 普通 bean：递归填充全部可写字段
        try {
            Constructor<?> ctor = rawType.getDeclaredConstructor();
            ctor.setAccessible(true);
            Object bean = ctor.newInstance();
            for (Class<?> c = rawType; c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers()) || Modifier.isFinal(f.getModifiers())) {
                        continue;
                    }
                    Object v = sample(f.getGenericType(), f.getType(), depth + 1);
                    if (v == null) {
                        continue;
                    }
                    f.setAccessible(true);
                    try {
                        f.set(bean, v);
                    } catch (Exception ignored) {
                        // 只读字段跳过，不影响 OGNL 的非空判定
                    }
                }
            }
            return bean;
        } catch (Exception e) {
            return null;
        }
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    /**
     * {@code #{}} 已被 MyBatis 换成 {@code ?}，这里按顺序换成 PostgreSQL 的 {@code $n}。
     */
    private static String toPositional(String sql) {
        StringBuilder sb = new StringBuilder(sql.length() + 16);
        int n = 0;
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (c == '?') {
                sb.append('$').append(++n);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String oneLine(String s) {
        return s == null ? "null" : s.replaceAll("\\s+", " ").trim();
    }

    /**
     * 未使用，保留以避免 IDE 提示 TreeMap 导入缺失（报告里按 id 排序时使用）。
     */
    @SuppressWarnings("unused")
    private static Map<String, String> sorted() {
        return new TreeMap<>();
    }
}
