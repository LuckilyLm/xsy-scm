package com.xsy.scm;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * SCM 分层与跨域边界的可执行规则。
 *
 * <p>这些约束此前只写在文档里：越界依赖不会让编译失败，也不会让接口报错，只会在事后
 * 让「谁改了这张表」变得无法回答。写成断言之后，新增一条越界依赖当场失败。
 *
 * <p>与 {@code FinanceReadOnlyContractTest} 的分工：那边扫源码文本，钉住「财务域不写业务表」
 * 这条 SQL 级事实；这里看编译产物的类型依赖，钉住分层方向与跨域引用。两者不重叠 ——
 * 一句 {@code UPDATE sales_order} 不产生任何类型依赖，而一次注入 DAO 也不产生任何 SQL 文本。
 *
 * <p>The importer checks both the SmartAdmin SCM package and {@code com.xsy.scm};
 * {@link #importedSourceSetIsNotEmpty} prevents an empty or misconfigured import from
 * making every architecture rule pass vacuously.
 */
@AnalyzeClasses(
        packages = {
                ScmArchitectureTest.LEGACY_SCM_PACKAGE,
                ScmArchitectureTest.XSY_SCM_PACKAGE,
        },
        importOptions = ImportOption.DoNotIncludeTests.class)
class ScmArchitectureTest {

    /** Both namespaces remain in the scan so reintroduced legacy SCM classes are detected. */
    static final String LEGACY_SCM_PACKAGE = "net.lab1024.sa.admin.module.scm";
    static final String XSY_SCM_PACKAGE = "com.xsy.scm";

    /**
     * SCM 具体业务域。{@code common} 不在其中：它是被所有域依赖的一侧，
     * 反向依赖任何一个具体域都会让「公共层」变成隐式的全域耦合点。
     */
    private static final String[] CONCRETE_DOMAINS = {
            "..scm.product..", "..scm.customer..", "..scm.supplier..", "..scm.pricing..",
            "..scm.order..", "..scm.purchase..", "..scm.inventory..", "..scm.sorting..",
            "..scm.delivery..", "..scm.finance..", "..scm.report..", "..scm.dashboard..",
            "..scm.screen..", "..scm.warehouse..",
    };

    private static final String[] CONCRETE_DOMAINS_EXCEPT_FINANCE = {
            "..scm.product..", "..scm.customer..", "..scm.supplier..", "..scm.pricing..",
            "..scm.order..", "..scm.purchase..", "..scm.inventory..", "..scm.sorting..",
            "..scm.delivery..", "..scm.report..", "..scm.dashboard..",
            "..scm.screen..", "..scm.warehouse..",
    };

    /**
     * 测试库在生产 classpath 上是可见的：{@code sa-base} 以 compile 作用域声明了
     * {@code spring-boot-starter-test}，所以编译器不会拦住生产代码 import AssertJ。
     * 这条规则是唯一的防线。
     */
    private static final String[] TEST_ONLY_LIBRARIES = {
            "org.junit..", "org.assertj..", "org.mockito..", "org.testcontainers..",
            "org.springframework.boot.test..", "org.springframework.test..",
            "com.tngtech.archunit..",
    };

    @ArchTest
    static final ArchRule controllersDoNotReachDaos =
            noClasses().that().resideInAPackage("..scm..controller..")
                    .should().dependOnClassesThat().resideInAPackage("..scm..dao..")
                    .because("Controller 只做入参校验与编排；直连 DAO 会绕过 Service 上的事务边界与数据权限");

    @ArchTest
    static final ArchRule daosDoNotReachUpwards =
            noClasses().that().resideInAPackage("..scm..dao..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..scm..controller..", "..scm..service..", "..scm..manager..")
                    .because("DAO 只表达取数与写数；反向依赖会让一次查询触发业务编排，形成环");

    @ArchTest
    static final ArchRule servicesAndManagersDoNotDependOnControllers =
            noClasses().that().resideInAnyPackage("..scm..service..", "..scm..manager..")
                    .should().dependOnClassesThat().resideInAPackage("..scm..controller..")
                    .because("Controller 是最外层入口，被内层依赖就意味着业务规则开始感知 HTTP 形态");

    @ArchTest
    static final ArchRule domainObjectsStayFreeOfApplicationLayers =
            noClasses().that().resideInAPackage("..scm..domain..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "..scm..controller..", "..scm..service..", "..scm..manager..", "..scm..dao..")
                    .because("entity / form / vo / dto 是数据形状，依赖应用层会让它们无法被复用与序列化");

    /**
     * common 不得依赖任何具体业务域。
     *
     * <p>Compile-time {@code static final String} constants are inlined by javac, so this
     * bytecode rule cannot detect source-only references to another domain's permission
     * catalog. Permission ownership is checked by the source-level permission contract.
     */
    @ArchTest
    static final ArchRule commonDoesNotDependOnConcreteDomains =
            noClasses().that().resideInAPackage("..scm.common..")
                    .should().dependOnClassesThat().resideInAnyPackage(CONCRETE_DOMAINS)
                    .because("common 被所有域依赖；它一旦依赖某个具体域，该域就被隐式耦合进每一个域");

    @ArchTest
    static final ArchRule financeDoesNotDependOnOtherDomains =
            noClasses().that().resideInAPackage("..scm.finance..")
                    .should().dependOnClassesThat().resideInAnyPackage(CONCRETE_DOMAINS_EXCEPT_FINANCE)
                    .because("财务域只消费既有事实、只生产自己的事实；跨域读走自己的只读 DAO，跨域写一律禁止");

    /**
     * 防止「一条规则都没跑到」被读成「所有规则都过了」。
     *
     * <p>ArchUnit rules pass vacuously when their imported class set is empty. This
     * assertion proves the configured package set contains SCM classes before the rules run.
     */
    @ArchTest
    static void importedSourceSetIsNotEmpty(JavaClasses classes) {
        long scmClasses = 0;
        boolean sawLegacyPackage = false;
        for (JavaClass clazz : classes) {
            String packageName = clazz.getPackageName();
            if (packageName.startsWith(LEGACY_SCM_PACKAGE)) {
                sawLegacyPackage = true;
            } else if (!packageName.startsWith(XSY_SCM_PACKAGE)) {
                continue;
            }
            scmClasses++;
        }
        if (scmClasses == 0) {
            throw new AssertionError("ArchUnit imported 0 SCM classes from {"
                    + LEGACY_SCM_PACKAGE + ", " + XSY_SCM_PACKAGE
                    + "}; the analyzed package set is misconfigured, not clean");
        }
        // SmartAdmin may have no SCM classes; the XSY namespace must still contain SCM classes.
        if (!sawLegacyPackage) {
            System.out.println("[ScmArchitectureTest] legacy SCM package is empty");
        }
    }

    @ArchTest
    static final ArchRule productionCodeDoesNotUseTestLibraries =
            noClasses().should().dependOnClassesThat().resideInAnyPackage(TEST_ONLY_LIBRARIES)
                    .because("测试库以 compile 作用域泄漏到生产 classpath，编译器不会拦，只能由这条规则拦");

    /**
     * Concrete SCM business domains must reside under {@code com.xsy.scm}.
     *
     * <p>This positive rule complements {@link #importedSourceSetIsNotEmpty}: it catches business classes
     * stranded outside the target namespace, while the companion rule prevents a vacuous empty scan.
     */
    @ArchTest
    static final ArchRule scmProductionCodeLivesInXsyPackage =
            classes().that().resideInAnyPackage(CONCRETE_DOMAINS)
                    .should().resideInAPackage(XSY_SCM_PACKAGE + "..")
                    .because("SCM business domains must remain under " + XSY_SCM_PACKAGE);
}
