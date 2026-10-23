import type {Component} from 'vue';
import * as antIcons from '@ant-design/icons-vue';

const ICON_REGISTRY = antIcons as unknown as Record<string, unknown>;

/** 菜单图标名转组件；空值或未知图标名用通用文件图标。 */
export function resolveMenuIcon(icon?: string | null): Component {
  const resolved = icon ? (ICON_REGISTRY[icon] as Component | undefined) : undefined;
  return resolved ?? antIcons.FileOutlined;
}
