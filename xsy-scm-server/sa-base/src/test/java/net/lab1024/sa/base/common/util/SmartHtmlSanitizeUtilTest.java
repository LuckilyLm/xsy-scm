package net.lab1024.sa.base.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 富文本 HTML 白名单清洗（FIX-03）。
 *
 * <p>公告与帮助文档的正文存 HTML、前端用 {@code v-html} 渲染 —— 清洗不到位就是存储型 XSS：
 * 一次提交，所有有权限查看的人打开页面都会执行。本类按「会被浏览器执行的东西一律不许留下」来断言，
 * 而不是逐条挑几个 payload 打勾。
 */
@DisplayName("富文本 HTML 白名单清洗（FIX-03）")
class SmartHtmlSanitizeUtilTest {

    @Nested
    @DisplayName("移除可执行内容")
    class RemovesExecutableContent {

        @Test
        @DisplayName("script 标签及其内容必须整体消失")
        void removesScriptTag() {
            String cleaned = SmartHtmlSanitizeUtil.clean("<p>正常</p><script>alert(1)</script><p>结尾</p>");
            assertThat(cleaned).doesNotContain("script").doesNotContain("alert(1)");
            assertThat(cleaned).contains("正常").contains("结尾");
        }

        @Test
        @DisplayName("事件属性（on*）一个都不许留")
        void removesEventHandlers() {
            String cleaned = SmartHtmlSanitizeUtil.clean(
                    "<img src=\"/a.png\" onerror=\"alert(1)\" onload=\"steal()\">"
                            + "<div onclick=\"evil()\" onmouseover=\"evil()\">正文</div>");
            assertThat(cleaned).doesNotContain("onerror").doesNotContain("onload")
                    .doesNotContain("onclick").doesNotContain("onmouseover");
            assertThat(cleaned).doesNotContain("alert(1)").doesNotContain("evil()");
            assertThat(cleaned).contains("正文");
        }

        @Test
        @DisplayName("iframe / object / embed / form 等另一个页面或可执行容器一律移除")
        void removesDangerousContainers() {
            String cleaned = SmartHtmlSanitizeUtil.clean(
                    "<iframe src=\"https://evil.example\"></iframe>"
                            + "<object data=\"x.swf\"></object>"
                            + "<embed src=\"x.swf\">"
                            + "<form action=\"/steal\"><input name=\"a\"></form>"
                            + "<p>保留我</p>");
            assertThat(cleaned).doesNotContain("<iframe").doesNotContain("<object")
                    .doesNotContain("<embed").doesNotContain("<form").doesNotContain("<input");
            assertThat(cleaned).contains("保留我");
        }

        @Test
        @DisplayName("style 标签与 style 属性移除：视觉欺骗与外部资源探测都从这两处来")
        void removesStyle() {
            String cleaned = SmartHtmlSanitizeUtil.clean(
                    "<style>body{display:none}</style><p style=\"position:fixed;top:0;left:0\">正文</p>");
            assertThat(cleaned).doesNotContain("<style").doesNotContain("position:fixed")
                    .doesNotContain("display:none");
            assertThat(cleaned).contains("正文");
        }
    }

    @Nested
    @DisplayName("危险链接协议")
    class StripsDangerousSchemes {

        @Test
        @DisplayName("javascript: 与 vbscript: 协议必须摘掉，标签本身可以留")
        void stripsJavascriptScheme() {
            String cleaned = SmartHtmlSanitizeUtil.clean(
                    "<a href=\"javascript:alert(1)\">点我</a><a href=\"vbscript:msgbox(1)\">再来</a>");
            assertThat(cleaned).doesNotContain("javascript:").doesNotContain("vbscript:");
            assertThat(cleaned).contains("点我").contains("再来");
        }

        @Test
        @DisplayName("大小写与空白变体同样拦得住")
        void stripsObfuscatedScheme() {
            String cleaned = SmartHtmlSanitizeUtil.clean(
                    "<a href=\"JaVaScRiPt:alert(1)\">A</a><a href=\"  javascript:alert(1)\">B</a>");
            assertThat(cleaned.toLowerCase()).doesNotContain("javascript:");
        }

        @Test
        @DisplayName("data: 协议摘掉：data:text/html 等价于一个可执行页面")
        void stripsDataScheme() {
            String cleaned = SmartHtmlSanitizeUtil.clean(
                    "<a href=\"data:text/html,<script>alert(1)</script>\">点我</a>"
                            + "<img src=\"data:image/svg+xml,<svg onload=alert(1)>\">");
            assertThat(cleaned).doesNotContain("data:text/html").doesNotContain("data:image/svg");
            assertThat(cleaned).doesNotContain("alert(1)");
        }

        @Test
        @DisplayName("正常协议与相对路径必须保留，别把功能一起清掉")
        void keepsSafeLinks() {
            String cleaned = SmartHtmlSanitizeUtil.clean(
                    "<a href=\"https://example.com/x\">外链</a>"
                            + "<a href=\"/admin/notice\">内链</a>"
                            + "<a href=\"#anchor\">锚点</a>"
                            + "<a href=\"mailto:a@b.com\">邮件</a>"
                            + "<img src=\"/img/logo.png\">");
            assertThat(cleaned).contains("https://example.com/x")
                    .contains("/admin/notice").contains("#anchor").contains("mailto:a@b.com")
                    .contains("/img/logo.png");
        }
    }

    @Nested
    @DisplayName("保留排版能力")
    class KeepsFormatting {

        @Test
        @DisplayName("常用排版标签与表格保留，后台正文不能因此变形")
        void keepsFormattingTags() {
            String html = "<h2>标题</h2><p><strong>粗</strong><em>斜</em><u>下划</u></p>"
                    + "<ul><li>一</li><li>二</li></ul>"
                    + "<blockquote>引用</blockquote>"
                    + "<pre><code>code()</code></pre>"
                    + "<table><thead><tr><th>列</th></tr></thead>"
                    + "<tbody><tr><td colspan=\"2\">值</td></tr></tbody></table>"
                    + "<p>行<br>内</p><hr>";
            String cleaned = SmartHtmlSanitizeUtil.clean(html);
            assertThat(cleaned).contains("<h2>").contains("<strong>").contains("<em>")
                    .contains("<ul>").contains("<li>").contains("<blockquote>")
                    .contains("<pre>").contains("<code>").contains("<table>")
                    .contains("<th>").contains("<td colspan=\"2\">").contains("<br>").contains("<hr>");
        }

        @Test
        @DisplayName("img 的 alt/title/width/height 保留，排版属性不该丢")
        void keepsImageAttributes() {
            String cleaned = SmartHtmlSanitizeUtil.clean(
                    "<img src=\"/a.png\" alt=\"说明\" title=\"标题\" width=\"100\" height=\"50\">");
            assertThat(cleaned).contains("alt=\"说明\"").contains("title=\"标题\"")
                    .contains("width=\"100\"").contains("height=\"50\"");
        }

        @Test
        @DisplayName("pre 里的空白与缩进不能被重排版吃掉")
        void keepsPreWhitespace() {
            String cleaned = SmartHtmlSanitizeUtil.clean("<pre>line1\n    indented\nline3</pre>");
            assertThat(cleaned).contains("    indented");
        }
    }

    @Nested
    @DisplayName("边界")
    class Boundaries {

        @Test
        @DisplayName("null / 空串原样返回，不制造空字符串")
        void handlesBlank() {
            assertThat(SmartHtmlSanitizeUtil.clean(null)).isNull();
            assertThat(SmartHtmlSanitizeUtil.clean("")).isEmpty();
            assertThat(SmartHtmlSanitizeUtil.clean("   ")).isEqualTo("   ");
        }

        @Test
        @DisplayName("清洗是幂等的：再清一次结果不变")
        void isIdempotent() {
            String once = SmartHtmlSanitizeUtil.clean(
                    "<p>正文</p><script>alert(1)</script><a href=\"javascript:x()\">链接</a>");
            assertThat(SmartHtmlSanitizeUtil.clean(once)).isEqualTo(once);
        }

        @Test
        @DisplayName("嵌套 payload：剥离外层后里层不能被重新激活")
        void stripsNestedPayload() {
            String cleaned = SmartHtmlSanitizeUtil.clean(
                    "<div><noscript><p title=\"</noscript><img src=x onerror=alert(1)>\"></p></noscript></div>");
            assertThat(cleaned).doesNotContain("onerror").doesNotContain("alert(1)");
        }

        @Test
        @DisplayName("纯文本与无标签内容不受影响")
        void keepsPlainText() {
            assertThat(SmartHtmlSanitizeUtil.clean("就是一段普通文字，没有标签"))
                    .isEqualTo("就是一段普通文字，没有标签");
        }
    }
}
