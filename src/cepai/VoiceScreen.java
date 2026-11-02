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
    private String stateText = "Opening microphone…";
    private int seconds;
    private final Command stop = new Command("Stop", Command.OK, 1);
    private final Command send = new Command("Send", Command.OK, 1);
    private final Command retry = new Command("Record again", Command.SCREEN, 2);
    private final Command listen = new Command("Play recording", Command.SCREEN, 3);
    private final Command back = new Command("Discard", Command.BACK, 1);
    private final Command stopPlay = new Command("Stop playback", Command.STOP, 2);
    VoiceScreen(CepAiMIDlet owner) {
        app = owner;
        addCommand(back); setCommandListener(this);
    }
    private void setState(String value) { stateText = value; repaint(); }
    protected String heading() { return "Speak"; }
    protected String eyebrow() { return clip == null ? "15 SECONDS" : "RECORDING READY"; }
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
            String label = ready ? count + " sec • ready to send" : count + " / 15 seconds";
            GlassTheme.text(g, label, (getWidth() - GlassTheme.BOLD.stringWidth(label)) / 2, top + 56, GlassTheme.BOLD, GlassTheme.INK);
            int barWidth = Math.min(172, getWidth() - 90), barX = (getWidth() - barWidth) / 2;
            g.setColor(0x354B63); g.fillRoundRect(barX, top + 81, barWidth, 4, 4, 4);
            g.setColor(GlassTheme.MINT); g.fillRoundRect(barX, top + 81, Math.max(4, barWidth * Math.min(15, count) / 15), 4, 4, 4);
            String note = ready ? "Send to Google, or use Menu to listen first." : "Listening. Press Stop when you're done.";
            String[] lines = GlassTheme.wrap(note, GlassTheme.SMALL, getWidth() - 52);
            if (top + 97 + lines.length * (GlassTheme.SMALL.getHeight() + 2) < bottom)
                GlassTheme.lines(g, lines, 26, top + 97, GlassTheme.SMALL, GlassTheme.MUTED);
        } else {
            GlassTheme.text(g, "USE YOUR VOICE", 25, top + 13, GlassTheme.BOLD, GlassTheme.MINT);
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
        setState("Recording ready • " + Math.max(1, (clip.durationMillis + 999) / 1000) + " seconds\nSend it or listen first.");
    }
    void start() {
        if (microphone != null) { microphone.cancel(); microphone = null; }
        if (timer != null) { timer.cancel(); timer = null; }
        app.stopAudio(); clip = null; stopping = false; started = 0; seconds = 0;
        removeCommand(send); removeCommand(retry); removeCommand(listen);
        setState("Opening microphone…");
        final Microphone session = new Microphone(); microphone = session;
        new Thread(new Runnable() { public void run() {
            try {
                session.start();
                app.serial(new Runnable() { public void run() {
                    if (closing || microphone != session) { session.cancel(); return; }
                    started = System.currentTimeMillis(); addCommand(stop); setState("Recording • 0 / 15 seconds");
                    timer = new Timer(); timer.schedule(new TimerTask() { public void run() {
                        app.serial(new Runnable() { public void run() {
                            if (closing || microphone != session || stopping) return;
                            seconds = (int) ((System.currentTimeMillis() - started) / 1000);
                            setState("Recording • " + seconds + " / 15 seconds");
                            if (seconds >= 15) finish();
                        }});
                    }}, 1000, 1000);
                }});
            } catch (final Exception unavailable) {
                session.cancel();
                app.serial(new Runnable() { public void run() {
                    if (closing || microphone != session) return; microphone = null; addCommand(retry);
                    setState(unavailable instanceof SecurityException ? "Microphone permission denied. Check recording access in the phone's application settings."
                        : "Java could not record WAV audio. Try Audio file from the main menu. "
                        + "Record a short WAV using High quality in the phone's Recorder app.");
                }});
            }
        }}).start();
    }
    private void finish() {
        if (closing || stopping || microphone == null) return;
        stopping = true; removeCommand(stop); setState("Preparing recording…");
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
                    setState("Could not prepare the recording. Try a shorter recording or select a PCM WAV using Audio file.");
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
        setState("Recording interrupted. You can record again.");
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
