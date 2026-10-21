import type {Component} from 'vue';
import * as antIcons from '@ant-design/icons-vue';

const ICON_REGISTRY = antIcons as unknown as Record<string, unknown>;

/**
 * 菜单图标名转成组件。
 *
 * 图标名存在数据库的 `t_menu.icon` 里，可能为空，也可能是图标库里没有的名字（改过名、手写过）。
 * 两种情况都返回 undefined，由调用方渲染一个等宽占位 —— 直接把 undefined 交给 `<component :is>`
 * 会渲染成空元素，同一组菜单里有图标的行和没图标的行，行首文字会差一个图标位。
 */
export function resolveMenuIcon(icon?: string | null): Component | undefined {
  return icon ? (ICON_REGISTRY[icon] as Component | undefined) : undefined;
}
