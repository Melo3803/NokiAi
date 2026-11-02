package cepai;

import java.util.Timer;
import java.util.TimerTask;
import javax.microedition.lcdui.*;

final class VoiceScreen extends GlassCanvas implements CommandListener {
    private final CepAiMIDlet app;
    private Microphone microphone;
    private Timer timer;
    private long started;
    private boolean closing, stopping;
    private AudioClip clip;
    private String stateText = "Mikrofon açılıyor…";
    private int seconds;
    private final Command stop = new Command("Durdur", Command.OK, 1);
    private final Command send = new Command("Gönder", Command.OK, 1);
    private final Command retry = new Command("Yeniden kaydet", Command.SCREEN, 2);
    private final Command listen = new Command("Kaydı dinle", Command.SCREEN, 3);
    private final Command back = new Command("Vazgeç", Command.BACK, 1);
    private final Command stopPlay = new Command("Dinlemeyi durdur", Command.STOP, 2);
    VoiceScreen(CepAiMIDlet owner) {
        app = owner;
        addCommand(back); setCommandListener(this);
    }
    private void setState(String value) { stateText = value; repaint(); }
    protected String heading() { return "Sesle sor"; }
    protected String eyebrow() { return clip == null ? "15 SANİYE" : "KAYIT HAZIR"; }
    protected Command primaryCommand() {
        if (clip != null) return send;
        if (microphone != null && started > 0 && !stopping) return stop;
        return actions.contains(retry) ? retry : null;
    }
    protected void navigate(int action) { }
    protected void content(Graphics g) {
        int top = TOP + 7, bottom = getHeight() - FOOT - 7, height = bottom - top;
        theme.panel(g, 12, top, getWidth() - 24, height, false);
        boolean ready = clip != null, recording = microphone != null && started > 0 && !stopping;
        if (ready || recording) {
            int cx = getWidth() / 2, y = top + 11;
            g.setColor(0x365F6B); g.fillArc(cx - 19, y, 38, 38, 0, 360);
            g.setColor(0x84C8C5); g.drawArc(cx - 19, y, 38, 38, 0, 360);
            GlassTheme.icon(g, 1, cx - 8, y + 9, GlassTheme.MINT);
            int count = ready ? Math.max(1, (clip.durationMillis + 999) / 1000) : seconds;
            String label = ready ? count + " saniye • kayıt hazır" : count + " / 15 saniye";
            GlassTheme.text(g, label, (getWidth() - GlassTheme.BOLD.stringWidth(label)) / 2, top + 56, GlassTheme.BOLD, GlassTheme.INK);
            int barWidth = Math.min(172, getWidth() - 90), barX = (getWidth() - barWidth) / 2;
            g.setColor(0x354B63); g.fillRoundRect(barX, top + 81, barWidth, 4, 4, 4);
            g.setColor(GlassTheme.MINT); g.fillRoundRect(barX, top + 81, Math.max(4, barWidth * Math.min(15, count) / 15), 4, 4, 4);
            String note = ready ? "Gönder ile Google'a ilet. Dinlemek için Menü." : "Seni dinliyorum. Bitirince Durdur'a bas.";
            String[] lines = GlassTheme.wrap(note, GlassTheme.SMALL, getWidth() - 52);
            if (top + 97 + lines.length * (GlassTheme.SMALL.getHeight() + 2) < bottom)
                GlassTheme.lines(g, lines, 26, top + 97, GlassTheme.SMALL, GlassTheme.MUTED);
        } else {
            GlassTheme.text(g, "SESİNLE BAŞLA", 25, top + 13, GlassTheme.BOLD, GlassTheme.MINT);
            String[] lines = GlassTheme.wrap(stateText, GlassTheme.SMALL, getWidth() - 52);
            g.setClip(24, top + 38, getWidth() - 48, Math.max(1, height - 45));
            GlassTheme.lines(g, lines, 25, top + 39, GlassTheme.SMALL, GlassTheme.INK);
            g.setClip(0, 0, getWidth(), getHeight());
        }
    }
    void preview(AudioClip value) {
        clip = value; stopping = false;
        removeCommand(stop); addCommand(send); addCommand(retry); addCommand(listen);
        addCommand(stopPlay);
        setState("Kayıt hazır • " + Math.max(1, (clip.durationMillis + 999) / 1000) + " saniye\nGönder veya önce kaydı dinle.");
    }
    void start() {
        if (microphone != null) { microphone.cancel(); microphone = null; }
        if (timer != null) { timer.cancel(); timer = null; }
        app.stopAudio(); clip = null; stopping = false; started = 0; seconds = 0;
        removeCommand(send); removeCommand(retry); removeCommand(listen);
        setState("Mikrofon açılıyor…");
        final Microphone session = new Microphone(); microphone = session;
        new Thread(new Runnable() { public void run() {
            try {
                session.start();
                app.serial(new Runnable() { public void run() {
                    if (closing || microphone != session) { session.cancel(); return; }
                    started = System.currentTimeMillis(); addCommand(stop); setState("Kaydediliyor • 0 / 15 saniye");
                    timer = new Timer(); timer.schedule(new TimerTask() { public void run() {
                        app.serial(new Runnable() { public void run() {
                            if (closing || microphone != session || stopping) return;
                            seconds = (int) ((System.currentTimeMillis() - started) / 1000);
                            setState("Kaydediliyor • " + seconds + " / 15 saniye");
                            if (seconds >= 15) finish();
                        }});
                    }}, 1000, 1000);
                }});
            } catch (final Exception unavailable) {
                session.cancel();
                app.serial(new Runnable() { public void run() {
                    if (closing || microphone != session) return; microphone = null; addCommand(retry);
                    setState(unavailable instanceof SecurityException ? "Mikrofon izni verilmedi. Java uygulama erişimindeki kayıt iznini kontrol et."
                        : "Java mikrofonu WAV kaydı açamadı. Ana ekrandaki Ses dosyası seçeneğini kullanabilirsin. "
                        + "Telefonun Ses Kaydedici uygulamasında kaliteyi Yüksek yapıp kısa bir WAV kaydet.");
                }});
            }
        }}).start();
    }
    private void finish() {
        if (closing || stopping || microphone == null) return;
        stopping = true; removeCommand(stop); setState("Kayıt hazırlanıyor…");
        if (timer != null) { timer.cancel(); timer = null; }
        final Microphone session = microphone;
        new Thread(new Runnable() { public void run() {
            try {
                final AudioClip ready = session.finish();
                app.serial(new Runnable() { public void run() {
                    if (closing || microphone != session) return; microphone = null; preview(ready);
                }});
            } catch (Exception invalid) {
                session.cancel();
                app.serial(new Runnable() { public void run() {
                    if (closing || microphone != session) return; microphone = null; addCommand(retry);
                    setState("Kayıt hazırlanamadı. Kısa bir kayıt dene veya Ses dosyası seçeneğiyle PCM WAV seç.");
                }});
            }
        }}).start();
    }
    void close() {
        closing = true; if (timer != null) { timer.cancel(); timer = null; }
        if (microphone != null) { microphone.cancel(); microphone = null; } clip = null;
    }
    void pause() {
        if (microphone == null) return;
        if (timer != null) { timer.cancel(); timer = null; }
        microphone.cancel(); microphone = null; stopping = false; removeCommand(stop); addCommand(retry);
        setState("Kayıt kesildi. Yeniden kaydedebilirsin.");
    }
    public void commandAction(Command command, Displayable source) {
        if (closing) return;
        if (command == stop) finish();
        else if (command == retry) start();
        else if (command == listen && clip != null) app.playClip(clip);
        else if (command == stopPlay) app.stopAudio();
        else if (command == send && clip != null) app.sendVoice(this, clip);
        else if (command == back) { close(); app.voiceBack(this); }
    }
}
