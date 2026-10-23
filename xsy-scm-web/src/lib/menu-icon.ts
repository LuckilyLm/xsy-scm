import type {Component} from 'vue';
import * as antIcons from '@ant-design/icons-vue';

const ICON_REGISTRY = antIcons as unknown as Record<string, unknown>;

/**
 * 菜单图标名转成组件。
 *
 * 图标名存在数据库的 `t_menu.icon` 里，可能为空，也可能是图标库里没有的名字（改过名、手写过）。
 * 空值或未知图标名使用通用文件图标，保证新增菜单仍能被识别。
 */
export function resolveMenuIcon(icon?: string | null): Component {
  const resolved = icon ? (ICON_REGISTRY[icon] as Component | undefined) : undefined;
  return resolved ?? antIcons.FileOutlined;
}
