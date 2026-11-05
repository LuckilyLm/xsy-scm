# 公共模块

- Java包统一使用com.xsy.scm.base，提供通用配置、文件、日志、字典、任务和安全工具；不能依赖系统管理或供应链业务实现。
- 配置文件使用xsy-base.yaml，YamlProcessor按xsy-*.yaml加载；修改资源名时必须同步加载规则和测试。
- PreviousClassNameHandler只负责读取升级前缓存中已知的类名；新记录使用当前名称，未知或不兼容类型继续拒绝。
- 登录传输密钥和密码处理规则不能随产品标识调整。第三方许可来源统一放在根NOTICE和LICENSE，源码不保留旧作者联系方式。
