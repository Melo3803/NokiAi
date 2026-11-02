package cepai;

import java.util.Vector;
import javax.microedition.lcdui.*;

/** Own softkeys/menu so Nokia's native white command chrome cannot cover the design. */
abstract class GlassCanvas extends Canvas {
    protected final GlassTheme theme = new GlassTheme();
    protected final Vector actions = new Vector();
    protected boolean menuOpen;
    private int menuIndex, menuStart;
    private CommandListener listener;
    static final int TOP = 46, FOOT = 28;

    GlassCanvas() { setFullScreenMode(true); }
    // Keep commands locally; attaching native commands can consume Nokia softkey events.
    public void addCommand(Command command) {
        if (!actions.contains(command)) actions.addElement(command); repaint();
    }
    public void removeCommand(Command command) { actions.removeElement(command); repaint(); }
    public void setCommandListener(CommandListener value) { listener = value; }
    protected void fire(Command command) {
        if (command != null && listener != null) { menuOpen = false; listener.commandAction(command, this); repaint(); }
    }
    protected Command primaryCommand() {
        for (int i = 0; i < actions.size(); i++) {
            Command c = (Command) actions.elementAt(i); if (c.getCommandType() == Command.OK) return c;
        }
        return null;
    }
    protected void select() { fire(primaryCommand()); }
    protected String centerLabel() { return "Seç"; }
    protected abstract void content(Graphics g);
    protected abstract void navigate(int action);
    protected String heading() { return "noki Ai"; }
    protected String eyebrow() { return "GEMINI"; }
    protected final void paint(Graphics g) {
        theme.background(g, getWidth(), getHeight());
        content(g);
        g.setClip(0, 0, getWidth(), getHeight());
        theme.panel(g, 12, 9, 27, 27, true); GlassTheme.sparkle(g, 17, 14, GlassTheme.MINT);
        GlassTheme.text(g, heading(), 47, 10, GlassTheme.TITLE, GlassTheme.INK);
        String label = eyebrow(); int labelWidth = GlassTheme.SMALL.stringWidth(label);
        GlassTheme.text(g, label, getWidth() - labelWidth - 14, 16, GlassTheme.SMALL, GlassTheme.MUTED);
        g.setColor(0x34435C); g.drawLine(12, TOP - 3, getWidth() - 13, TOP - 3);
        if (menuOpen) paintMenu(g);
        paintFoot(g);
    }
    private void paintFoot(Graphics g) {
        int y = getHeight() - FOOT;
        g.setColor(0x101B2B); g.fillRect(0, y, getWidth(), FOOT);
        g.setColor(0x354B60); g.drawLine(0, y, getWidth(), y);
        Command primary = primaryCommand();
        String left = menuOpen ? "Seç" : (primary == null ? "" : primary.getLabel());
        GlassTheme.text(g, GlassTheme.fit(left, GlassTheme.BOLD, getWidth() / 3 - 14), 12, y + 6, GlassTheme.BOLD, GlassTheme.MINT);
        String middle = menuOpen ? (menuIndex + 1) + "/" + actions.size() : centerLabel();
        GlassTheme.text(g, middle, (getWidth() - GlassTheme.SMALL.stringWidth(middle)) / 2, y + 6, GlassTheme.SMALL, GlassTheme.MUTED);
        String right = menuOpen ? "Kapat" : "Menü";
        GlassTheme.text(g, right, getWidth() - GlassTheme.BOLD.stringWidth(right) - 12, y + 6, GlassTheme.BOLD, GlassTheme.INK);
    }
    private int menuRows() { return Math.max(1, (getHeight() - FOOT - TOP - 32) / 24); }
    private void paintMenu(Graphics g) {
        if (actions.size() == 0) return;
        menuIndex = Math.min(menuIndex, actions.size() - 1);
        int rows = Math.min(menuRows(), actions.size());
        if (menuIndex < menuStart) menuStart = menuIndex;
        if (menuIndex >= menuStart + rows) menuStart = menuIndex - rows + 1;
        menuStart = Math.min(menuStart, Math.max(0, actions.size() - rows));
        int h = rows * 24 + 28, y = getHeight() - FOOT - h - 5;
        theme.panel(g, 10, y, getWidth() - 20, h, false);
        GlassTheme.text(g, "KISAYOLLAR", 23, y + 6, GlassTheme.BOLD, GlassTheme.MINT);
        for (int i = 0; i < rows; i++) {
            int index = menuStart + i, rowY = y + 24 + i * 24;
            if (index == menuIndex) {
                g.setColor(0x34636C); g.fillRoundRect(16, rowY - 1, getWidth() - 32, 23, 7, 7);
            }
            GlassTheme.text(g, GlassTheme.fit(((Command) actions.elementAt(index)).getLabel(), GlassTheme.SMALL, getWidth() - 66),
                26, rowY + 3, GlassTheme.SMALL, GlassTheme.INK);
            if (index == menuIndex) GlassTheme.icon(g, 3, getWidth() - 42, rowY + 2, GlassTheme.MINT);
        }
    }
    protected int action(int code) { try { return getGameAction(code); } catch (IllegalArgumentException ignored) { return 0; } }
    protected void keyPressed(int code) {
        int action = action(code);
        if (code == -7 || code == KEY_POUND) { menuOpen = !menuOpen; menuIndex = menuStart = 0; repaint(); return; }
        if (menuOpen) {
            if (action == UP) menuIndex = Math.max(0, menuIndex - 1);
            else if (action == DOWN) menuIndex = Math.min(actions.size() - 1, menuIndex + 1);
            else if ((action == FIRE || code == -6) && actions.size() > 0) fire((Command) actions.elementAt(menuIndex));
            else if (code == -8 || code == 27) menuOpen = false;
            repaint(); return;
        }
        if (code == -6) fire(primaryCommand());
        else if (action == FIRE) select();
        else navigate(action);
        repaint();
    }
    protected void keyRepeated(int code) {
        int a = action(code); if (a == UP || a == DOWN || a == LEFT || a == RIGHT) keyPressed(code);
    }
    protected void pointerPressed(int x, int y) {
        if (y >= getHeight() - FOOT) {
            if (x > getWidth() * 2 / 3) keyPressed(-7);
            else if (x < getWidth() / 3) keyPressed(-6); else select();
        } else if (menuOpen) {
            int rows = Math.min(menuRows(), actions.size());
            int top = getHeight() - FOOT - rows * 24 - 28 - 5 + 24;
            int index = (y - top) / 24 + menuStart;
            if (y >= top && index >= 0 && index < actions.size() && index < menuStart + rows) fire((Command) actions.elementAt(index));
        }
    }
    protected void hideNotify() { menuOpen = false; theme.release(); }
}
