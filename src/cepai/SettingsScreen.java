package cepai;

import javax.microedition.lcdui.*;

/** Custom settings list; native editors retain reliable E63 QWERTY/password input. */
final class SettingsScreen extends GlassCanvas implements CommandListener {
    private final CepAiMIDlet app;
    private final TextField key, model, tts;
    private final ChoiceGroup remember, voice;
    private int selected, first;
    private TextBox editor;
    private TextField editing;
    private final Command done = new Command("Done", Command.OK, 1);
    private final Command back = new Command("Back", Command.BACK, 1);

    SettingsScreen(CepAiMIDlet app, TextField key, TextField model, TextField tts, ChoiceGroup remember, ChoiceGroup voice) {
        this.app = app; this.key = key; this.model = model; this.tts = tts; this.remember = remember; this.voice = voice;
        setCommandListener(this);
    }
    protected String heading() { return "Settings"; }
    protected String eyebrow() { return "noki Ai"; }
    protected String centerLabel() { return "Edit"; }
    private int rows() { return Math.max(1, (getHeight() - FOOT - TOP - 10) / 44); }
    private String title(int index) {
        switch (index) {
            case 0: return "Google API key";
            case 1: return "Chat model";
            case 2: return "Remember key";
            case 3: return "Voice model";
            default: return "Voice replies";
        }
    }
    private String value(int index) {
        switch (index) {
            case 0: return key.getString().length() == 0 ? "Select to add • QR: Menu" : "Key added • select to change";
            case 1: return model.getString();
            case 2: return remember.isSelected(0) ? "On • stored without encryption" : "Off • this session only";
            case 3: return tts.getString();
            default: return voice.isSelected(0) ? "On • uses a separate TTS request" : "Off • text only";
        }
    }
    protected void content(Graphics g) {
        int visible = rows();
        if (selected < first) first = selected;
        if (selected >= first + visible) first = selected - visible + 1;
        for (int i = first; i < Math.min(5, first + visible); i++) {
            int y = TOP + 6 + (i - first) * 44;
            theme.panel(g, 12, y, getWidth() - 24, 39, i == selected);
            GlassTheme.text(g, title(i), 24, y + 5, GlassTheme.BOLD, i == selected ? GlassTheme.MINT : GlassTheme.INK);
            GlassTheme.text(g, GlassTheme.fit(value(i), GlassTheme.SMALL, getWidth() - 78), 24, y + 6 + GlassTheme.BOLD.getHeight(), GlassTheme.SMALL, GlassTheme.MUTED);
            GlassTheme.icon(g, 3, getWidth() - 43, y + 9, i == selected ? GlassTheme.MINT : GlassTheme.MUTED);
        }
        int y = TOP + 6 + visible * 44;
        if (y + GlassTheme.SMALL.getHeight() < getHeight() - FOOT)
            GlassTheme.text(g, (selected + 1) + "/5  •  ↑ ↓ move  •  QR and models: Menu", 15, y + 1, GlassTheme.SMALL, GlassTheme.MUTED);
    }
    protected void navigate(int action) {
        if (action == UP) selected = Math.max(0, selected - 1);
        else if (action == DOWN) selected = Math.min(4, selected + 1);
        else if (action == LEFT || action == RIGHT) { if (selected == 2 || selected == 4) select(); }
    }
    protected void select() {
        if (selected == 2) { remember.setSelectedIndex(0, !remember.isSelected(0)); repaint(); return; }
        if (selected == 4) { voice.setSelectedIndex(0, !voice.isSelected(0)); repaint(); return; }
        editing = selected == 0 ? key : (selected == 1 ? model : tts);
        editor = new TextBox(title(selected), editing.getString(), editing.getMaxSize(), editing.getConstraints());
        editor.addCommand(done); editor.addCommand(back); editor.setCommandListener(this); app.showScreen(editor);
    }
    public void commandAction(Command command, Displayable from) {
        if (from == editor) {
            if (command == done) editing.setString(editor.getString());
            editor = null; editing = null; app.showScreen(this);
        } else app.commandAction(command, from);
    }
}
