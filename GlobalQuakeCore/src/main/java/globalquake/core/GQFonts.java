package globalquake.core;

import javax.swing.*;
import java.awt.*;
import java.io.InputStream;

/**
 * 全局字体工具类。
 * 加载打包的思源黑体（Source Han Sans CN），
 * 保证中文字符在所有 UI 与画布绘制中正常显示。
 */
public final class GQFonts {

    private static Font regular;
    private static Font bold;
    private static boolean initialized = false;

    private GQFonts() {
    }

    /**
     * 必须在任何 UI 创建之前调用（由 Settings 的 static 块触发）。
     */
    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;

        Font fallback = new Font("Dialog", Font.PLAIN, 12);
        regular = loadFont("fonts/SourceHanSansCN-Regular.otf", fallback);
        bold = loadFont("fonts/SourceHanSansCN-Bold.otf", regular.deriveFont(Font.BOLD));

        try {
            UIManager.put("defaultFont", regular.deriveFont(12f));
        } catch (Exception ignored) {
            // 设置失败时使用 Swing 默认字体
        }
    }

    private static Font loadFont(String path, Font fallback) {
        try (InputStream is = GQFonts.class.getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                return fallback;
            }
            Font font = Font.createFont(Font.TRUETYPE_FONT, is);
            try {
                GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font);
            } catch (Exception ignored) {
                // 无图形环境时跳过注册
            }
            return font;
        } catch (Exception e) {
            return fallback;
        }
    }

    /**
     * 获取指定样式与大小的全局字体。
     *
     * @param style Font.PLAIN / Font.BOLD / Font.ITALIC
     * @param size  字号
     */
    public static Font font(int style, int size) {
        if (!initialized) {
            init();
        }
        Font base = (style & Font.BOLD) != 0 ? bold : regular;
        return base.deriveFont(style, (float) size);
    }
}
