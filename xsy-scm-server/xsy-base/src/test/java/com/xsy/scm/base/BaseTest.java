package com.xsy.scm.base;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidTypeIdException;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.xsy.scm.base.common.domain.PageResult;
import com.xsy.scm.base.module.support.job.repository.domain.XsyJobEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;
import org.junit.jupiter.api.Nested;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.xsy.scm.base.module.support.operatelog.OperateLogBusinessType;
import com.xsy.scm.base.common.json.PreviousClassNameHandler;
import com.xsy.scm.base.common.util.XsyHtmlSanitizeUtil;
import com.xsy.scm.base.config.YamlProcessor;
import com.xsy.scm.base.module.support.operatelog.core.OperateLogParamMask;

class BaseTest {

    @Nested
    class PreviousClassNameHandlerTest {
        private ObjectMapper mapper() {
            return new ObjectMapper().addHandler(new PreviousClassNameHandler())
                .activateDefaultTyping(LaissezFaireSubTypeValidator.instance, ObjectMapper.DefaultTyping.NON_FINAL);
        }

        @Test
        void readsPreviousPackageWithOriginalValues() throws Exception {
            var page = new PageResult<>();
            page.setTotal(7L);
            String previous = mapper().writeValueAsString(page)
                .replace("com.xsy.scm.base.", "net.lab1024.sa.base.");
            Object value = mapper().readValue(previous, Object.class);
            assertThat(value).isInstanceOf(PageResult.class);
            assertThat(((PageResult<?>) value).getTotal()).isEqualTo(7L);
        }

        @Test
        void readsPreviousClassNameAndArray() throws Exception {
            String job = mapper().writeValueAsString(new XsyJobEntity())
                .replace(XsyJobEntity.class.getName(),
                    "net.lab1024.sa.base.module.support.job.repository.domain.SmartJobEntity");
            assertThat(mapper().readValue(job, Object.class)).isInstanceOf(XsyJobEntity.class);
            String array = mapper().writeValueAsString(new PageResult<?>[] {new PageResult<>()})
                .replace("com.xsy.scm.base.", "net.lab1024.sa.base.");
            assertThat(mapper().readValue(array, Object.class)).isInstanceOf(PageResult[].class);
        }

        @Test
        void unknownPreviousTypeStillFails() {
            assertThatThrownBy(() -> mapper().readValue("[\"net.lab1024.sa.base.MissingType\",{}]", Object.class))
                .isInstanceOf(InvalidTypeIdException.class);
        }
    }

    @Nested
    @DisplayName("富文本 HTML 白名单清洗（FIX-03）")
    class XsyHtmlSanitizeUtilTest {
        @Nested
        @DisplayName("移除可执行内容")
        class RemovesExecutableContent {

            @Test
            @DisplayName("script 标签及其内容必须整体消失")
            void removesScriptTag() {
                String cleaned = XsyHtmlSanitizeUtil.clean("<p>正常</p><script>alert(1)</script><p>结尾</p>");
                assertThat(cleaned).doesNotContain("script").doesNotContain("alert(1)");
                assertThat(cleaned).contains("正常").contains("结尾");
            }

            @Test
            @DisplayName("事件属性（on*）一个都不许留")
            void removesEventHandlers() {
                String cleaned = XsyHtmlSanitizeUtil.clean(
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
                String cleaned = XsyHtmlSanitizeUtil.clean(
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
                String cleaned = XsyHtmlSanitizeUtil.clean(
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
                String cleaned = XsyHtmlSanitizeUtil.clean(
                        "<a href=\"javascript:alert(1)\">点我</a><a href=\"vbscript:msgbox(1)\">再来</a>");
                assertThat(cleaned).doesNotContain("javascript:").doesNotContain("vbscript:");
                assertThat(cleaned).contains("点我").contains("再来");
            }

            @Test
            @DisplayName("大小写与空白变体同样拦得住")
            void stripsObfuscatedScheme() {
                String cleaned = XsyHtmlSanitizeUtil.clean(
                        "<a href=\"JaVaScRiPt:alert(1)\">A</a><a href=\"  javascript:alert(1)\">B</a>");
                assertThat(cleaned.toLowerCase()).doesNotContain("javascript:");
            }

            @Test
            @DisplayName("data: 协议摘掉：data:text/html 等价于一个可执行页面")
            void stripsDataScheme() {
                String cleaned = XsyHtmlSanitizeUtil.clean(
                        "<a href=\"data:text/html,<script>alert(1)</script>\">点我</a>"
                                + "<img src=\"data:image/svg+xml,<svg onload=alert(1)>\">");
                assertThat(cleaned).doesNotContain("data:text/html").doesNotContain("data:image/svg");
                assertThat(cleaned).doesNotContain("alert(1)");
            }

            @Test
            @DisplayName("正常协议与相对路径必须保留，别把功能一起清掉")
            void keepsSafeLinks() {
                String cleaned = XsyHtmlSanitizeUtil.clean(
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
                String cleaned = XsyHtmlSanitizeUtil.clean(html);
                assertThat(cleaned).contains("<h2>").contains("<strong>").contains("<em>")
                        .contains("<ul>").contains("<li>").contains("<blockquote>")
                        .contains("<pre>").contains("<code>").contains("<table>")
                        .contains("<th>").contains("<td colspan=\"2\">").contains("<br>").contains("<hr>");
            }

            @Test
            @DisplayName("img 的 alt/title/width/height 保留，排版属性不该丢")
            void keepsImageAttributes() {
                String cleaned = XsyHtmlSanitizeUtil.clean(
                        "<img src=\"/a.png\" alt=\"说明\" title=\"标题\" width=\"100\" height=\"50\">");
                assertThat(cleaned).contains("alt=\"说明\"").contains("title=\"标题\"")
                        .contains("width=\"100\"").contains("height=\"50\"");
            }

            @Test
            @DisplayName("pre 里的空白与缩进不能被重排版吃掉")
            void keepsPreWhitespace() {
                String cleaned = XsyHtmlSanitizeUtil.clean("<pre>line1\n    indented\nline3</pre>");
                assertThat(cleaned).contains("    indented");
            }
        }

        @Nested
        @DisplayName("边界")
        class Boundaries {

            @Test
            @DisplayName("null / 空串原样返回，不制造空字符串")
            void handlesBlank() {
                assertThat(XsyHtmlSanitizeUtil.clean(null)).isNull();
                assertThat(XsyHtmlSanitizeUtil.clean("")).isEmpty();
                assertThat(XsyHtmlSanitizeUtil.clean("   ")).isEqualTo("   ");
            }

            @Test
            @DisplayName("清洗是防重复处理的：再清一次结果不变")
            void isIdempotent() {
                String once = XsyHtmlSanitizeUtil.clean(
                        "<p>正文</p><script>alert(1)</script><a href=\"javascript:x()\">链接</a>");
                assertThat(XsyHtmlSanitizeUtil.clean(once)).isEqualTo(once);
            }

            @Test
            @DisplayName("嵌套 payload：剥离外层后里层不能被重新激活")
            void stripsNestedPayload() {
                String cleaned = XsyHtmlSanitizeUtil.clean(
                        "<div><noscript><p title=\"</noscript><img src=x onerror=alert(1)>\"></p></noscript></div>");
                assertThat(cleaned).doesNotContain("onerror").doesNotContain("alert(1)");
            }

            @Test
            @DisplayName("纯文本与无标签内容不受影响")
            void keepsPlainText() {
                assertThat(XsyHtmlSanitizeUtil.clean("就是一段普通文字，没有标签"))
                        .isEqualTo("就是一段普通文字，没有标签");
            }
        }
    }

    @Nested
    class YamlProcessorTest {
        @Test
        void renamedSharedConfigurationIsLoaded() {
            StandardEnvironment environment = new StandardEnvironment();
            new YamlProcessor().postProcessEnvironment(environment, null);
            assertThat(environment.containsProperty("spring.datasource.url")).isTrue();
            assertThat(environment.getProperty("spring.flyway.locations")).isEqualTo("classpath:db/migration/modules");
            assertThat(environment.containsProperty("xsy.job.enabled")).isTrue();
        }
    }

    @Nested
    @DisplayName("操作日志参数脱敏")
    class OperateLogParamMaskTest {
        @Test
        @DisplayName("敏感键值被遮蔽，大小写与嵌套层级都覆盖")
        void masksSecretKeysIncludingNestedAndCaseInsensitive() {
            String param = """
                    [{"editForm":{"password":"P@ssw0rd","NickName":"小明"},\
                    "authorization":"Bearer abc","session":{"refreshToken":"rt-1","deviceId":"d-9"}}]""";

            String masked = OperateLogParamMask.mask(param);

            assertThat(masked).doesNotContain("P@ssw0rd").doesNotContain("Bearer abc").doesNotContain("rt-1");
            assertThat(masked).contains("\"password\":\"******\"")
                    .contains("\"authorization\":\"******\"")
                    .contains("\"refreshToken\":\"******\"");
            // 非敏感键保持原值与原顺序
            assertThat(masked).contains("\"NickName\":\"小明\"").contains("\"deviceId\":\"d-9\"");
        }

        @Test
        @DisplayName("容器键自身命中时整棵子树遮蔽，不留明文子节点")
        void masksWholeSubtreeWhenContainerKeyNameMatches() {
            String param = "[{\"token\":{\"accessKey\":\"AK-1\",\"expireAt\":\"2099-01-01\"}}]";

            assertThat(OperateLogParamMask.mask(param)).doesNotContain("AK-1").doesNotContain("2099-01-01");
        }

        @Test
        @DisplayName("业务对象键与筛选规则不受影响")
        void keepsBusinessObjectKeysIntact() {
            String param = "[{\"spuId\":12,\"customerId\":345,\"routeId\":7}]";

            assertThat(OperateLogParamMask.mask(param)).isEqualTo(param);

            // 发生遮蔽时要重新序列化，此处钉住列表筛选依赖的精确边界片段
            String mixed = "[{\"spuId\":12,\"customerId\":9,\"password\":\"P@ssw0rd\"}]";
            assertThat(OperateLogParamMask.mask(mixed))
                    .contains("\"spuId\":12,")
                    .contains("\"customerId\":9,")
                    .doesNotContain("P@ssw0rd");
        }

        @Test
        @DisplayName("不含敏感键时返回同一份文本，不重排 JSON")
        void returnsIdenticalTextWhenNothingToMask() {
            String param = "[{\"customerId\":9,\"remark\":\"a\",\"items\":[{\"skuId\":3,\"qty\":2}]}]";

            assertThat(OperateLogParamMask.mask(param)).isSameAs(param);
        }

        @Test
        @DisplayName("非 JSON 脏值原样放行；疑似 JSON 但解析失败时不得留下原文")
        void passesThroughNonJsonButDropsRawTextWhenParseFails() {
            assertThat(OperateLogParamMask.mask(null)).isNull();
            assertThat(OperateLogParamMask.mask("")).isEmpty();
            assertThat(OperateLogParamMask.mask("plain text")).isEqualTo("plain text");

            // 截断 / 脏 JSON：这是写库出口，返回原文等于把凭据明文永久写入数据库，前端第二层遮不住数据库行。
            // 与 FileKeyVoSerializer「不再有任何分支写出 value」同规则。
            String truncated = "{\"password\":\"P@ssw0rd\"";
            assertThat(OperateLogParamMask.mask(truncated)).doesNotContain("P@ssw0rd").doesNotContain(truncated);

            // 结构彻底损坏的数组载荷同样不得回声原文
            String brokenArray = "[{\"token\":\"rt-secret\"";
            assertThat(OperateLogParamMask.mask(brokenArray)).doesNotContain("rt-secret");
        }
    }

    @Nested
    @DisplayName("操作日志领域归属识别")
    class OperateLogBusinessTypeTest {
        @Test
        @DisplayName("按精确 JSON 键识别商品与客户")
        void recognizesProductAndCustomerByExactJsonKeys() {
            assertThat(OperateLogBusinessType.recognize("[{\"spuId\":12,\"skuId\":34}]", "/api/product/spu/edit"))
                    .containsExactly(OperateLogBusinessType.PRODUCT);
            assertThat(OperateLogBusinessType.recognize("[{\"customerId\":9,\"parentCustomerId\":3}]", "/api/scm/customer/edit"))
                    .containsExactly(OperateLogBusinessType.CUSTOMER);
            assertThat(OperateLogBusinessType.recognize("[{\"spuId\":12,\"customerId\":9}]", "/api/x"))
                    .containsExactly(OperateLogBusinessType.PRODUCT, OperateLogBusinessType.CUSTOMER);
        }

        @Test
        @DisplayName("线路 ID 落在路径变量里，故按 URL 的线路前缀段识别")
        void recognizesDeliveryRouteByUrlSegmentOnly() {
            assertThat(OperateLogBusinessType.recognize("[{\"version\":0}]", "/api/scm/delivery/routes/12/print/orders"))
                    .containsExactly(OperateLogBusinessType.DELIVERY_ROUTE);
            assertThat(OperateLogBusinessType.recognize("[{\"longitude\":113.9}]", "/api/scm/delivery/routes/12/stops/7"))
                    .containsExactly(OperateLogBusinessType.DELIVERY_ROUTE);
        }

        @Test
        @DisplayName("识别不出领域的通用系统日志返回空集，不被额外权限拦截")
        void returnsEmptyForUnrecognizedSystemLogs() {
            assertThat(OperateLogBusinessType.recognize("[{\"configKey\":\"sys.name\"}]", "/api/config/update")).isEmpty();
            assertThat(OperateLogBusinessType.recognize(null, null)).isEmpty();
            assertThat(OperateLogBusinessType.recognize("", "")).isEmpty();
        }
    }
}
