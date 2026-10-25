/**
 * 商城主题色：与后端 MallThemeColors 保持同一套推导规则。
 *
 * 后台只维护一个主色，客户端按比例混出深/浅色阶；两边算法不一致会导致
 * 后台预览与小程序实际配色对不上，修改这里时需同步后端。
 */
export const MALL_THEME_PRESETS: Record<string, string> = {
  FRESH: '#16A34A',
  WARM: '#F97316',
  OCEAN: '#0284C7',
};

/** 白字压在主题色上的最低对比度，与后端 MIN_WHITE_CONTRAST 一致。 */
export const MIN_WHITE_CONTRAST = 2.5;

const HEX = /^#[0-9A-Fa-f]{6}$/;

export interface MallThemePalette {
  primary: string;
  dark: string;
  ink: string;
  light: string;
  disabled: string;
}

export function isValidMallThemeColor(color?: string | null): boolean {
  return Boolean(color && HEX.test(color));
}

export function normalizeMallThemeColor(color?: string | null): string | null {
  return color ? color.trim().toUpperCase() : null;
}

export function mallThemeBaseColor(themeCode?: string | null, themeColor?: string | null): string {
  if (isValidMallThemeColor(themeColor)) return normalizeMallThemeColor(themeColor) as string;
  return MALL_THEME_PRESETS[themeCode || ''] || MALL_THEME_PRESETS.FRESH;
}

function channel(color: string, offset: number): number {
  return Number.parseInt(color.slice(offset, offset + 2), 16);
}

function mix(color: string, red: number, green: number, blue: number, ratio: number): string {
  const toHex = (value: number) => Math.round(value).toString(16).padStart(2, '0').toUpperCase();
  return `#${toHex(channel(color, 1) * (1 - ratio) + red * ratio)}${toHex(channel(color, 3) * (1 - ratio) + green * ratio)}${toHex(
    channel(color, 5) * (1 - ratio) + blue * ratio
  )}`;
}

function luminance(color: string): number {
  const linear = (value: number) => {
    const c = value / 255;
    return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
  };
  return 0.2126 * linear(channel(color, 1)) + 0.7152 * linear(channel(color, 3)) + 0.0722 * linear(channel(color, 5));
}

/** 白色文字与主题色的对比度。 */
export function whiteContrast(color: string): number {
  return 1.05 / (luminance(isValidMallThemeColor(color) ? color : MALL_THEME_PRESETS.FRESH) + 0.05);
}

export function mallThemePalette(color: string): MallThemePalette {
  return {
    primary: color,
    dark: mix(color, 0, 0, 0, 0.18),
    ink: mix(color, 0, 0, 0, 0.32),
    light: mix(color, 255, 255, 255, 0.9),
    disabled: mix(color, 255, 255, 255, 0.55),
  };
}
