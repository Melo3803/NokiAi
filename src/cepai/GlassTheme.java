package cepai;

import java.util.Vector;
import javax.microedition.lcdui.*;

/** Small, software-composited glass surfaces: no alpha or GPU dependency. */
final class GlassTheme {
    static final int INK = 0xEFF5FF, MUTED = 0xA8B9D2, MINT = 0x84EDCF;
    static final Font SMALL = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
    static final Font BOLD = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_SMALL);
    static final Font TITLE = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_MEDIUM);
    static final Font HERO = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_LARGE);
    private Image background;
    private int width, height;

    static int blend(int a, int b, int amount) {
        int r = (((a >> 16) & 255) * (255 - amount) + ((b >> 16) & 255) * amount) / 255;
        int g = (((a >> 8) & 255) * (255 - amount) + ((b >> 8) & 255) * amount) / 255;
        int c = ((a & 255) * (255 - amount) + (b & 255) * amount) / 255;
        return (r << 16) | (g << 8) | c;
    }
    private int colorAt(int x, int y) {
        int base = blend(0x0B1020, 0x112432, Math.min(255, Math.max(0, y * 255 / Math.max(1, height))));
        int radius = Math.max(1, width * width);
        int dx = x - width * 4 / 5, dy = y - height / 10;
        int light = Math.max(0, radius - dx * dx - dy * dy) * 110 / radius;
        base = blend(base, 0x6558A4, light);
        dx = x + width / 6; dy = y - height;
        light = Math.max(0, radius - dx * dx - dy * dy) * 92 / radius;
        return blend(base, 0x258C8B, light);
    }
    void background(Graphics g, int w, int h) {
        if (background == null || width != w || height != h) {
            width = w; height = h;
            int[] rgb = new int[w * h];
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) rgb[y * w + x] = colorAt(x, y);
            background = Image.createRGBImage(rgb, w, h, false);
        }
        g.drawImage(background, 0, 0, Graphics.TOP | Graphics.LEFT);
    }
    void release() { background = null; }
    void panel(Graphics g, int x, int y, int w, int h, boolean selected) {
        int tint = selected ? 0x315F68 : 0x25344C;
        int first = Math.max(1, g.getClipY() - y);
        int last = Math.min(h - 1, g.getClipY() + g.getClipHeight() - y);
        for (int row = first; row < last; row++) {
            int inset = row < 3 || row >= h - 3 ? 4 : (row < 6 || row >= h - 6 ? 2 : 1);
            g.setColor(blend(colorAt(x + w / 2, y + row), tint, selected ? 182 : 155));
            g.drawLine(x + inset, y + row, x + w - inset - 1, y + row);
        }
        g.setColor(selected ? 0x79CDBD : 0x51617C);
        g.drawRoundRect(x, y, w - 1, h - 1, 12, 12);
        g.setColor(selected ? 0xB1F4DF : 0x728098);
        g.drawLine(x + 8, y + 1, x + w - 9, y + 1);
    }
    static void text(Graphics g, String value, int x, int y, Font font, int color) {
        g.setFont(font); g.setColor(color); g.drawString(value, x, y, Graphics.TOP | Graphics.LEFT);
    }
    static String fit(String value, Font font, int width) {
        if (font.stringWidth(value) <= width) return value;
        int end = value.length();
        while (end > 0 && font.substringWidth(value, 0, end) + font.stringWidth("…") > width) end--;
        return value.substring(0, end) + "…";
    }
    static String[] wrap(String value, Font font, int width) {
        Vector lines = new Vector(); int start = 0;
        while (start < value.length()) {
            if (value.charAt(start) == '\r') { start++; continue; }
            if (value.charAt(start) == '\n') { lines.addElement(""); start++; continue; }
            int end = start, space = -1;
            while (end < value.length() && value.charAt(end) != '\n' && value.charAt(end) != '\r') {
                if (font.substringWidth(value, start, end - start + 1) > width && end > start) break;
                if (value.charAt(end) == ' ') space = end;
                end++;
            }
            if (end < value.length() && value.charAt(end) != '\n' && value.charAt(end) != '\r' && space > start) end = space;
            lines.addElement(value.substring(start, end)); start = end;
            if (start < value.length() && (value.charAt(start) == '\n' || value.charAt(start) == '\r')) start++;
            else while (start < value.length() && value.charAt(start) == ' ') start++;
        }
        if (lines.size() == 0) lines.addElement("");
        String[] result = new String[lines.size()]; lines.copyInto(result); return result;
    }
    static void lines(Graphics g, String[] lines, int x, int y, Font font, int color) {
        for (int i = 0; i < lines.length; i++) text(g, lines[i], x, y + i * (font.getHeight() + 2), font, color);
    }
    static void sparkle(Graphics g, int x, int y, int color) {
        g.setColor(color);
        g.fillTriangle(x + 8, y, x + 5, y + 6, x + 11, y + 6);
        g.fillTriangle(x + 8, y + 16, x + 5, y + 10, x + 11, y + 10);
        g.fillTriangle(x, y + 8, x + 6, y + 5, x + 6, y + 11);
        g.fillTriangle(x + 16, y + 8, x + 10, y + 5, x + 10, y + 11);
        g.fillRect(x + 5, y + 5, 6, 6);
    }
    static void icon(Graphics g, int kind, int x, int y, int color) {
        g.setColor(color);
        if (kind == 1) {
            g.drawRoundRect(x + 5, y, 6, 10, 5, 5);
            g.drawArc(x + 2, y + 4, 12, 11, 180, 180);
            g.drawLine(x + 8, y + 14, x + 8, y + 18); g.drawLine(x + 4, y + 18, x + 12, y + 18);
        } else if (kind == 0) {
            g.drawLine(x + 3, y + 12, x + 12, y + 3); g.drawLine(x + 5, y + 14, x + 14, y + 5);
            g.drawLine(x + 12, y + 3, x + 14, y + 5); g.drawLine(x + 3, y + 12, x + 2, y + 15);
            g.drawLine(x + 2, y + 15, x + 5, y + 14); g.drawLine(x + 3, y + 19, x + 15, y + 19);
        } else if (kind == 2) {
            g.drawRoundRect(x + 2, y + 4, 13, 11, 3, 3); g.drawArc(x + 6, y, 5, 9, 0, 180);
            g.fillRect(x + 8, y + 8, 2, 4);
        } else {
            g.drawLine(x + 6, y + 4, x + 11, y + 9); g.drawLine(x + 11, y + 9, x + 6, y + 14);
        }
    }
}
