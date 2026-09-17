package net.lab1024.sa.admin.test;

/**
 * 集成测试运行目录常量（编译期常量，可直接用于 {@code @SpringBootTest(properties = ...)}）。
 *
 * <p><b>背景：</b>历史基线把这些目录硬编码为某台开发机的绝对路径
 * {@code D:/Browser Download/xsy-scm/.runtime/...}。那台机器有 D 盘，本仓库其它环境未必有。
 * 目录不存在时 {@code FileService.fileUpload(...)} 会返回 {@code ok=false}，
 * 于是 {@code ProductPgIT#usesSmartAdminFilesAndSwitchesPrimaryImageWithoutReplacingIdentity}
 * 之类的用例会失败——失败原因是环境，不是业务代码。
 *
 * <p><b>取值约定：</b>改用仓库内相对路径 {@code .runtime/...}。IT 由 {@code sa-admin}
 * 模块目录启动（{@code ${user.dir}} = {@code xsy-scm-server/sa-admin}），
 * 因此这里显式回退到父目录，保证无论从哪个模块运行都能落到同一个仓库级运行时目录。
 *
 * <p>需要指向别处时，直接改这两个常量即可；它们是编译期常量，
 * 不影响 {@code @SpringBootTest(properties = ...)} 的常量要求。
 */
public interface PgITPaths {

    /** 仓库级运行时根目录（相对 sa-admin 模块目录）。 */
    String RUNTIME_ROOT = ".." + "/.runtime";

    /** 集成测试日志目录。 */
    String DEFAULT_LOG_DIR = RUNTIME_ROOT + "/logs/test";

    /** 集成测试上传目录（注意结尾斜杠：FileService 直接做路径拼接）。 */
    String DEFAULT_UPLOAD_PATH = RUNTIME_ROOT + "/upload/";
}
