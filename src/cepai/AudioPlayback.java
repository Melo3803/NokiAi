package cepai;

import java.io.*;
import javax.microedition.media.*;
import javax.microedition.media.control.VolumeControl;

public class AudioPlayback {
    public interface Listener { void finished(String error); }
    private volatile int generation;
    private volatile boolean active;
    private Player player;
    protected Player open(byte[] wav) throws IOException, MediaException {
        return Manager.createPlayer(new ByteArrayInputStream(wav), "audio/x-wav");
    }
    public boolean isActive() { return active; }
    public void play(final AudioClip clip, final Listener listener) {
        stop(); final int token = generation; active = true;
        new Thread(new Runnable() { public void run() {
            Player opened = null;
            try {
                opened = open(clip.wav); opened.realize();
                synchronized (AudioPlayback.this) {
                    if (generation != token) { opened.close(); return; }
                    player = opened;
                }
                opened.addPlayerListener(new PlayerListener() { public void playerUpdate(Player source, String event, Object data) {
                    if (event.equals(PlayerListener.END_OF_MEDIA)) complete(token, listener, null);
                    else if (event.equals(PlayerListener.ERROR)) complete(token, listener, "Ses oynatılamadı. Metin yanıtı ekranda duruyor.");
                }});
                VolumeControl volume = (VolumeControl) opened.getControl("VolumeControl");
                if (volume != null) volume.setLevel(75);
                if (generation != token) { opened.close(); return; }
                opened.start();
            } catch (Exception unsupported) {
                if (opened != null) try { opened.close(); } catch (Exception ignored) { }
                complete(token, listener, "Telefon WAV sesini oynatamadı. Metin yanıtını okuyabilirsin.");
            }
        }}).start();
    }
    private void complete(int token, Listener listener, String error) {
        Player source;
        synchronized (this) {
            if (generation != token || !active) return;
            generation++; active = false; source = player; player = null;
        }
        if (source != null) try { source.close(); } catch (Exception ignored) { }
        listener.finished(error);
    }
    public void stop() {
        Player source;
        synchronized (this) { generation++; active = false; source = player; player = null; }
        if (source != null) try { source.close(); } catch (Exception ignored) { }
    }
}
