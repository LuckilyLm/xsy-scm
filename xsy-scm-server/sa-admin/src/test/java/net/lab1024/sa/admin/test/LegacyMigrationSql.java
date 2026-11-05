package net.lab1024.sa.admin.test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** 从固定Git对象读取仅供重新执行测试的旧SQL，不再将退役迁移打包进应用。 */
public final class LegacyMigrationSql {

    private static final String REVISION = "2940c3b5f08781a6833804ecede43c50a29afb1d";

    private LegacyMigrationSql() {
    }

    /**
     * @param fileName 不含路径的原V迁移文件名，不允许为空或读取其他Git路径
     * @return 固定提交中的UTF-8原文，保留历史用例的SQL分段与业务断言
     * @throws IllegalArgumentException 文件名为空或含路径时拒绝读取
     * @throws IllegalStateException 缺少Git或固定历史对象时失败，不能静默跳过执行
     */
    public static String read(String fileName) {
        if (fileName == null || !fileName.matches("V[0-9]+__[^/\\\\]+\\.sql")) {
            throw new IllegalArgumentException("必须提供不含路径的历史V迁移文件名");
        }
        String object = REVISION + ":xsy-scm-server/sa-admin/src/main/resources/db/migration/" + fileName;
        try {
            Process process = new ProcessBuilder("git", "show", object).start();
            byte[] content = process.getInputStream().readAllBytes();
            String error = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            if (process.waitFor() != 0) {
                throw new IllegalStateException("重新执行历史SQL需要完整Git对象 " + REVISION + ": " + error);
            }
            return new String(content, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("无法读取历史SQL；请确认本地Git和固定提交对象可用", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("读取历史SQL被中断", exception);
        }
    }
}
