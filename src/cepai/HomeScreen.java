package cepai;

import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;
import javax.microedition.lcdui.*;

final class HomeScreen extends GlassCanvas {
    private final Command write, talk, cancel;
    private final Vector bubbles = new Vector();
    private Config config;
    private Conversation conversation;
    private NetworkTask pending;
    private String status = "";
    private int scroll, totalHeight, layoutWidth, focus, pulse;
    private Timer animation;

    private static final class Bubble {
        String[] lines;
        boolean user, notice;
        int height, width;
    }
    HomeScreen(Command write, Command talk, Command cancel) {
        this.write = write; this.talk = talk; this.cancel = cancel;
    }
    void update(Config value, Conversation history, NetworkTask task, String notice) {
        boolean follow = scroll >= maxScroll() - 4 || pending != task;
        config = value; conversation = history; pending = task; status = notice;
        rebuild(); if (follow) scrollToEnd();
        if (isShown()) animate(); repaint();
    }
    private int viewportHeight() { return Math.max(20, getHeight() - FOOT - TOP - 12 - (pending == null ? 0 : 25)); }
    private int maxScroll() { return Math.max(0, totalHeight - viewportHeight()); }
    void scrollToEnd() { scroll = maxScroll(); repaint(); }
    private void append(String text, boolean user, boolean notice) {
        Bubble bubble = new Bubble(); bubble.user = user; bubble.notice = notice;
        bubble.lines = GlassTheme.wrap(text, GlassTheme.SMALL, Math.max(40, getWidth() - 72));
        int textWidth = 96;
        for (int i = 0; i < bubble.lines.length; i++) textWidth = Math.max(textWidth, GlassTheme.SMALL.stringWidth(bubble.lines[i]));
        bubble.width = Math.min(getWidth() - 40, textWidth + 24);
        bubble.height = 26 + GlassTheme.BOLD.getHeight() + bubble.lines.length * (GlassTheme.SMALL.getHeight() + 2);
        bubbles.addElement(bubble); totalHeight += bubble.height + 9;
    }
    private void rebuild() {
        bubbles.removeAllElements(); totalHeight = 0; layoutWidth = getWidth();
        if (conversation == null) return;
        for (int i = 0; i < conversation.size(); i++) {
            append(conversation.user(i), true, false); append(conversation.answer(i), false, false);
        }
        if (pending != null && !pending.listModels && !pending.connectionTest && pending.operation != NetworkTask.TTS)
            append(pending.question, true, false);
        if (status.length() > 0 && bubbles.size() > 0) append(status, false, true);
        if (totalHeight > 0) totalHeight -= 9;
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
    }
    protected String eyebrow() { return pending == null ? "GEMINI" : "CONNECTING"; }
    protected Command primaryCommand() { return pending == null ? write : cancel; }
    protected String centerLabel() { return pending == null ? (bubbles.size() == 0 ? "Select" : "↑  ↓") : "Wait"; }
    protected void select() { if (pending == null && bubbles.size() == 0 && focus == 1) fire(talk); else fire(primaryCommand()); }
    protected void navigate(int action) {
        if (bubbles.size() == 0) {
            if (action == LEFT || action == UP) focus = 0;
            else if (action == RIGHT || action == DOWN) focus = 1;
        } else if (action == UP) scroll = Math.max(0, scroll - (GlassTheme.SMALL.getHeight() + 2) * 2);
        else if (action == DOWN) scroll = Math.min(maxScroll(), scroll + (GlassTheme.SMALL.getHeight() + 2) * 2);
        else if (action == LEFT) scroll = Math.max(0, scroll - viewportHeight());
        else if (action == RIGHT) scroll = Math.min(maxScroll(), scroll + viewportHeight());
    }
    protected void content(Graphics g) {
        if (config == null) return;
        if (layoutWidth != getWidth()) { rebuild(); scrollToEnd(); }
        if (bubbles.size() == 0) welcome(g);
        else {
            g.setClip(0, TOP + 5, getWidth(), viewportHeight() + 1);
            int y = TOP + 6 - scroll;
            for (int i = 0; i < bubbles.size(); i++) {
                Bubble b = (Bubble) bubbles.elementAt(i);
                if (y + b.height >= TOP && y <= TOP + viewportHeight() + 6) {
                    int x = b.user ? getWidth() - b.width - 13 : 12;
                    theme.panel(g, x, y, b.width, b.height, b.user);
                    GlassTheme.text(g, b.notice ? "INFO" : (b.user ? "YOU" : "noki Ai"), x + 12, y + 9, GlassTheme.BOLD,
                        b.user || !b.notice ? GlassTheme.MINT : GlassTheme.MUTED);
                    GlassTheme.lines(g, b.lines, x + 12, y + 15 + GlassTheme.BOLD.getHeight(), GlassTheme.SMALL,
                        b.notice ? GlassTheme.MUTED : GlassTheme.INK);
                }
                y += b.height + 9;
            }
            g.setClip(0, 0, getWidth(), getHeight());
            if (maxScroll() > 0) {
                int vh = viewportHeight(), handle = Math.max(10, vh * vh / totalHeight);
                g.setColor(0x7BA6AC); g.fillRoundRect(getWidth() - 5, TOP + 6 + (vh - handle) * scroll / maxScroll(), 2, handle, 2, 2);
            }
        }
        if (pending != null) {
            int y = getHeight() - FOOT - 25;
            g.setColor(0x142539); g.fillRect(0, y, getWidth(), 25);
            for (int i = 0; i < 3; i++) {
                g.setColor(i == pulse % 3 ? GlassTheme.MINT : 0x426372); g.fillArc(13 + i * 7, y + 10, 3, 3, 0, 360);
            }
            String label = pending.operation == NetworkTask.TTS ? "Creating speech" : (pending.listModels || pending.connectionTest ? "Checking connection" : "Thinking");
            GlassTheme.text(g, label, 42, y + 4, GlassTheme.SMALL, GlassTheme.MUTED);
        }
    }
    private void welcome(Graphics g) {
        int top = TOP + 7, bottom = getHeight() - FOOT - 7 - (pending == null ? 0 : 25);
        int tileHeight = 41, heroHeight = Math.max(62, bottom - top - tileHeight - 8);
        theme.panel(g, 12, top, getWidth() - 24, heroHeight, false);
        GlassTheme.text(g, "START WITH AN IDEA", 25, top + 11, GlassTheme.BOLD, GlassTheme.MINT);
        GlassTheme.text(g, "What's on your mind?", 24, top + 30, GlassTheme.HERO, GlassTheme.INK);
        GlassTheme.sparkle(g, getWidth() - 49, top + 30, 0x98ACC9);
        String hint = status.length() > 0 ? status : (config.key.length() == 0 ? "Add your API key in Settings.\nThen start your first conversation." : "Ask a question. Explore an idea.\nType it or say it.");
        int textY = top + 33 + GlassTheme.HERO.getHeight();
        if (textY + GlassTheme.SMALL.getHeight() * 2 + 7 <= top + heroHeight)
            GlassTheme.lines(g, GlassTheme.wrap(hint, GlassTheme.SMALL, getWidth() - 57), 25, textY, GlassTheme.SMALL, GlassTheme.MUTED);
        int y = bottom - tileHeight, gap = 8, tileWidth = (getWidth() - 24 - gap) / 2;
        actionTile(g, 12, y, tileWidth, tileHeight, "Write", 0, focus == 0);
        actionTile(g, 12 + tileWidth + gap, y, tileWidth, tileHeight, "Speak", 1, focus == 1);
    }
    private void actionTile(Graphics g, int x, int y, int w, int h, String label, int icon, boolean selected) {
        theme.panel(g, x, y, w, h, selected);
        GlassTheme.icon(g, icon, x + 12, y + 10, selected ? GlassTheme.MINT : GlassTheme.MUTED);
        GlassTheme.text(g, label, x + 38, y + (h - GlassTheme.BOLD.getHeight()) / 2, GlassTheme.BOLD, GlassTheme.INK);
    }
    private synchronized void animate() {
        if (animation != null) { animation.cancel(); animation = null; }
        if (pending == null || !isShown()) return;
        animation = new Timer(); animation.schedule(new TimerTask() { public void run() { pulse++; repaint(); }}, 400, 400);
    }
    protected void showNotify() { animate(); }
    protected void hideNotify() { super.hideNotify(); synchronized (this) { if (animation != null) { animation.cancel(); animation = null; } } }
    void close() { pending = null; hideNotify(); }
}
