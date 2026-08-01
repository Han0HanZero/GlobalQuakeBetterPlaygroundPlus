package globalquake.core.intensity;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

/**
 * 全局烈度配色方案切换。
 *
 * 方案：
 *  0 默认（各烈度标准自带的颜色）
 *  1 要石（Kanameishi）：JMA / MMI / CSIS 共用一套霓虹系配色，数字无阴影
 *  2 SREV：JMA 专用配色（带彩色边框），数字无阴影
 *
 * Level 匹配：JMA 的 Level 名称为数字（5/6 带 +/− 后缀），MMI / CSIS 为罗马数字，
 * 据此区分查表。查不到时回退到 Level 自带颜色（默认方案）。
 */
public final class LevelPalette {

    public static final int DEFAULT = 0;
    public static final int KANAMEISHI = 1;
    public static final int SREV = 2;

    private static final Map<String, Color> JMA_BG = new HashMap<>();
    private static final Map<String, Color> JMA_FG = new HashMap<>();
    private static final Map<String, Color> ROMAN_BG = new HashMap<>();
    private static final Map<String, Color> ROMAN_FG = new HashMap<>();
    private static final Map<String, Color> SREV_BG = new HashMap<>();
    private static final Map<String, Color> SREV_FG = new HashMap<>();
    private static final Map<String, Color> SREV_BORDER = new HashMap<>();

    private static volatile int current = DEFAULT;

    static {
        // 要石 JMA 表
        jma("0", "9f9f9f", 0x000000);
        jma("1", "cfcfcf", 0x000000);
        jma("2", "3fafff", 0x000000);
        jma("3", "5fdf8f", 0x000000);
        jma("4", "f7e757", 0x000000);
        jma("5弱", "ff8f00", 0x000000);
        jma("5強", "ff4f00", 0xffffff);
        jma("6弱", "df0f0f", 0xffffff);
        jma("6強", "af0000", 0xffffff);
        jma("7", "7f007f", 0xffffff);

        // 要石 MMI / CSIS 表
        roman("1", "9f9f9f", 0x000000);
        roman("2", "cfcfcf", 0x000000);
        roman("3", "5fcfff", 0x000000);
        roman("4", "3fafff", 0x000000);
        roman("5", "5fdf8f", 0x000000);
        roman("6", "f7e757", 0x000000);
        roman("7", "ff8f00", 0x000000);
        roman("8", "ff4f00", 0xffffff);
        roman("9", "df0f0f", 0xffffff);
        roman("10", "7f007f", 0xffffff);

        // SREV JMA 表（背景 / 文字 / 边框）
        srev("0", "686870", "1c1c1c", "525257");
        srev("1", "3098bd", "ffffff", "217895");
        srev("2", "4cd0a7", "1c1c1c", "3daa7e");
        srev("3", "f6cb51", "1c1c1c", "cea735");
        srev("4", "ff9939", "1c1c1c", "d17d2e");
        srev("5弱", "e52a18", "ffffff", "f5947c");
        srev("5強", "c31b1b", "ffffff", "f58985");
        srev("6弱", "a30a6b", "ffffff", "e55eb1");
        srev("6強", "86046e", "ffffff", "d855d1");
        srev("7", "54068e", "ffffff", "cd3beb");
    }

    private LevelPalette() {
    }

    public static int getCurrent() {
        return current;
    }

    public static void setCurrent(int palette) {
        current = palette;
    }

    /** 非默认方案时数字不加阴影。 */
    public static boolean noShadow() {
        return current != DEFAULT;
    }

    /** 烈度背景色；查不到时回退 Level 自带颜色。 */
    public static Color bg(Level level) {
        String key = key(level);
        switch (current) {
            case KANAMEISHI -> {
                Color c = isRoman(level) ? ROMAN_BG.get(key) : JMA_BG.get(key);
                return c != null ? c : level.getColor();
            }
            case SREV -> {
                Color c = SREV_BG.get(key);
                return c != null ? c : level.getColor();
            }
            default -> {
                return level.getColor();
            }
        }
    }

    /** 文字色；默认方案返回 null（沿用原有阴影/描边绘制）。 */
    public static Color fg(Level level) {
        String key = key(level);
        switch (current) {
            case KANAMEISHI -> {
                Color c = isRoman(level) ? ROMAN_FG.get(key) : JMA_FG.get(key);
                return c != null ? c : Color.BLACK;
            }
            case SREV -> {
                Color c = SREV_FG.get(key);
                return c != null ? c : Color.WHITE;
            }
            default -> {
                return null;
            }
        }
    }

    /** 边框色；默认/要石为白色，SREV 用表内边框色。 */
    public static Color border(Level level) {
        if (current == SREV) {
            Color c = SREV_BORDER.get(key(level));
            return c != null ? c : Color.WHITE;
        }
        return Color.WHITE;
    }

    /** 把 Level 归一为配色表键：JMA 5-/5+ → 5弱/5強，罗马数字 → 阿拉伯数字（10~12 归为 10）。 */
    private static String key(Level level) {
        String n = level.getName();
        String s = level.getSuffix();
        if ("5".equals(n)) {
            return "+".equals(s) ? "5強" : "5弱";
        }
        if ("6".equals(n)) {
            return "+".equals(s) ? "6強" : "6弱";
        }
        return switch (n) {
            case "I", "Ⅰ" -> "1";
            case "II", "Ⅱ" -> "2";
            case "III", "Ⅲ" -> "3";
            case "IV", "Ⅳ" -> "4";
            case "V", "Ⅴ" -> "5";
            case "VI", "Ⅵ" -> "6";
            case "VII", "Ⅶ" -> "7";
            case "VIII", "Ⅷ" -> "8";
            case "IX", "Ⅸ" -> "9";
            case "X", "Ⅹ", "XI", "Ⅺ", "XII", "Ⅻ" -> "10";
            default -> n;
        };
    }

    /** JMA 的 Level 名称为数字（0~7），MMI / CSIS 为罗马数字（I~XII）。 */
    private static boolean isRoman(Level level) {
        char c = level.getName().charAt(0);
        return c == 'I' || c == 'V' || c == 'X' || c == 'Ⅰ' || c == 'Ⅴ' || c == 'Ⅹ';
    }

    private static void jma(String key, String hex, int fg) {
        JMA_BG.put(key, parse(hex));
        JMA_FG.put(key, new Color(fg));
    }

    private static void roman(String key, String hex, int fg) {
        ROMAN_BG.put(key, parse(hex));
        ROMAN_FG.put(key, new Color(fg));
    }

    private static void srev(String key, String hexBg, String hexFg, String hexBorder) {
        SREV_BG.put(key, parse(hexBg));
        SREV_FG.put(key, parse(hexFg));
        SREV_BORDER.put(key, parse(hexBorder));
    }

    private static Color parse(String hex) {
        return new Color(Integer.parseInt(hex, 16));
    }
}
