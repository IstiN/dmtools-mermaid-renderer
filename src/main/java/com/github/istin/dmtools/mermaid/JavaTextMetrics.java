package com.github.istin.dmtools.mermaid;

import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.GraphicsEnvironment;
import java.awt.font.FontRenderContext;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Real AWT-based text metrics using bundled NotoSans fonts.
 * Replaces the fake character-count heuristics used in the GraalJS shim,
 * giving Mermaid layout engine accurate pixel widths and heights.
 */
final class JavaTextMetrics {

    private static final FontRenderContext FRC = new FontRenderContext(null, true, false);
    private static final Map<String, Font> BASE_FONT_CACHE = new ConcurrentHashMap<>();
    private static boolean fontsLoaded = false;

    static {
        loadBundledFont("/fonts/TrebuchetMS-Regular.ttf");
        loadBundledFont("/fonts/TrebuchetMS-Bold.ttf");
        loadBundledFont("/fonts/NotoSans-Regular.ttf");
        loadBundledFont("/fonts/NotoSans-Bold.ttf");
        loadBundledFont("/fonts/NotoEmoji-Regular.ttf");
        fontsLoaded = true;
    }

    private JavaTextMetrics() {
    }

    private static void loadBundledFont(String resource) {
        try (InputStream is = JavaTextMetrics.class.getResourceAsStream(resource)) {
            if (is != null) {
                Font font = Font.createFont(Font.TRUETYPE_FONT, is);
                GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font);
            }
        } catch (FontFormatException | IOException e) {
            // fall through — system fonts will be used as fallback
        }
    }

    static double measureWidth(String text, double fontSize, String fontFamily) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        Font font = resolveFont(fontFamily, Font.PLAIN, (float) fontSize);
        Rectangle2D bounds = font.getStringBounds(text, FRC);
        return Math.max(0, bounds.getWidth());
    }

    static double measureHeight(double fontSize, String fontFamily) {
        Font font = resolveFont(fontFamily, Font.PLAIN, (float) fontSize);
        return font.getLineMetrics("Ag", FRC).getHeight();
    }

    static double measureAscent(double fontSize, String fontFamily) {
        Font font = resolveFont(fontFamily, Font.PLAIN, (float) fontSize);
        return font.getLineMetrics("Ag", FRC).getAscent();
    }

    private static Font resolveFont(String family, int style, float size) {
        String cacheKey = (family == null ? "" : family.toLowerCase(Locale.ROOT)) + "|" + style;
        Font base = BASE_FONT_CACHE.computeIfAbsent(cacheKey, k -> {
            String[] candidates = (family == null ? "" : family).split(",");
            for (String candidate : candidates) {
                String name = candidate.trim().replace("'", "").replace("\"", "");
                String resolved = resolveGenericFamily(name);
                Font font = new Font(resolved, style, 12);
                // "Dialog" is AWT's fallback name when the font is not found
                if (!font.getFamily(Locale.ROOT).equalsIgnoreCase("Dialog")) {
                    return font;
                }
            }
            return new Font("Noto Sans", style, 12);
        });
        return base.deriveFont(style, size);
    }

    private static String resolveGenericFamily(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            // Mermaid's default font stack: "trebuchet ms, verdana, arial, sans-serif"
            // Use Trebuchet MS for accurate layout metrics matching browser rendering
            case "trebuchet ms" -> "Trebuchet MS";
            case "verdana" -> "Verdana";
            case "arial" -> "Arial";
            case "sans-serif", "helvetica", "helvetica neue", "tahoma" -> "Trebuchet MS";
            case "serif", "times new roman", "times", "georgia",
                 "palatino" -> Font.SERIF;
            case "monospace", "courier new", "courier", "consolas", "menlo" -> Font.MONOSPACED;
            default -> name;
        };
    }

}
