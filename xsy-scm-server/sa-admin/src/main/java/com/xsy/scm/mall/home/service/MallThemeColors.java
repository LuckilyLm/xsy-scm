package com.xsy.scm.mall.home.service;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import com.xsy.scm.mall.home.constant.MallTheme;

/**
 * 主题色派生：后台只维护一个主色，客户端需要的深浅色阶在这里统一算好。
 *
 * <p>
 * 输出固定为 {@code #RRGGBB} 大写；校验只拦「浅到看不清」的颜色， 不追求正文 4.5:1 —— 品牌色常作为按钮底色配白字，严格 4.5 会把常用的绿/橙全部拒掉。 正文链接使用派生出的 ink，而不是主色本身。
 */
public final class MallThemeColors {

    private static final Pattern HEX = Pattern.compile("^#[0-9A-Fa-f]{6}$");

    /** 白字压在主题色上的最低对比度；低于它说明颜色太浅（例如亮黄）。 */
    private static final double MIN_WHITE_CONTRAST = 2.5;

    private static final Map<String, String> PRESETS = Map.of(MallTheme.FRESH.getCode(), "#16A34A",
        MallTheme.WARM.getCode(), "#F97316", MallTheme.OCEAN.getCode(), "#0284C7");

    private MallThemeColors() {
    }

    public static boolean isValid(String color) {
        return color != null && HEX.matcher(color).matches();
    }

    public static String normalize(String color) {
        return color == null ? null : color.trim().toUpperCase(Locale.ROOT);
    }

    /** 预设编码对应的主色；未知编码按默认绿色，和客户端回退口径一致。 */
    public static String baseColor(String themeCode) {
        // Map.of 不接受 null 键：getOrDefault(null, ...) 也会抛 NPE，历史发布记录的
        // theme_code 可能为空，先挡掉再查表。
        return themeCode == null
            ? PRESETS.get(MallTheme.FRESH.getCode())
            : PRESETS.getOrDefault(themeCode, PRESETS.get(MallTheme.FRESH.getCode()));
    }

    public static double whiteContrast(String color) {
        double background = luminance(color);
        return 1.05 / (background + 0.05);
    }

    public static boolean readableOnWhite(String color) {
        return whiteContrast(color) >= MIN_WHITE_CONTRAST;
    }

    public static String dark(String color) {
        return mix(color, 0, 0, 0, 0.18);
    }

    public static String ink(String color) {
        return mix(color, 0, 0, 0, 0.32);
    }

    public static String light(String color) {
        return mix(color, 255, 255, 255, 0.90);
    }

    public static String disabled(String color) {
        return mix(color, 255, 255, 255, 0.55);
    }

    private static String mix(String color, int red, int green, int blue, double ratio) {
        int r = channel(color, 1);
        int g = channel(color, 3);
        int b = channel(color, 5);
        return String.format("#%02X%02X%02X", Math.round(r * (1 - ratio) + red * ratio),
            Math.round(g * (1 - ratio) + green * ratio), Math.round(b * (1 - ratio) + blue * ratio));
    }

    private static int channel(String color, int offset) {
        return Integer.parseInt(color.substring(offset, offset + 2), 16);
    }

    private static double luminance(String color) {
        double[] channels = new double[]{channel(color, 1), channel(color, 3), channel(color, 5)};
        double[] linear = new double[channels.length];
        for (int index = 0; index < channels.length; index++) {
            double value = channels[index] / 255;
            linear[index] = value <= 0.03928 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
        }
        return 0.2126 * linear[0] + 0.7152 * linear[1] + 0.0722 * linear[2];
    }
}
