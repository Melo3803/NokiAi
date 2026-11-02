package cepai;

import java.io.*;
import javax.microedition.media.*;
import javax.microedition.media.control.RecordControl;

public class Microphone {
    private volatile boolean cancelled;
    private Player player;
    private RecordControl record;
    private final Buffer output = new Buffer();
    protected Player open(String locator) throws IOException, MediaException { return Manager.createPlayer(locator); }
    public void start() throws IOException, MediaException {
        String[] locators = { "capture://audio?encoding=pcm&rate=8000&bits=16&channels=1",
            "capture://audio?encoding=wav&rate=8000&bits=16&channels=1", "capture://audio?encoding=pcm", "capture://audio" };
        for (int i = 0; i < locators.length; i++) {
            if (cancelled) throw new IOException("Kayıt iptal edildi.");
            Player opened = null;
            try {
                opened = open(locators[i]); opened.realize();
                RecordControl control = (RecordControl) opened.getControl("RecordControl");
                if (control == null) throw new MediaException("No RecordControl");
                String type = control.getContentType(); type = type == null ? "" : type.toLowerCase();
                if (type.indexOf("wav") < 0 && type.indexOf("pcm") < 0) throw new MediaException("WAV recording unsupported");
                synchronized (this) {
                    if (cancelled) { opened.close(); throw new IOException("Kayıt iptal edildi."); }
                    player = opened; record = control;
                }
                try { control.setRecordSizeLimit(AudioClip.MAX_INPUT); } catch (MediaException unsupportedLimit) { }
                control.setRecordStream(output); control.startRecord(); opened.start();
                if (cancelled) { opened.close(); throw new IOException("Kayıt iptal edildi."); }
                return;
            } catch (SecurityException denied) {
                if (opened != null) opened.close(); throw denied;
            } catch (Exception unavailable) {
                if (opened != null) try { opened.close(); } catch (Exception ignored) { }
                synchronized (this) { player = null; record = null; }
                output.reset();
            }
        }
        throw new MediaException("Java mikrofonu WAV kaydı açamadı. Ses dosyası seçeneğini kullanabilirsin.");
    }
    public AudioClip finish() throws IOException {
        Player source; RecordControl control;
        synchronized (this) { source = player; control = record; player = null; record = null; }
        if (cancelled || control == null) throw new IOException("Kayıt hazır değil.");
        try { control.stopRecord(); control.commit(); return AudioClip.fromWav(output.bytes(), 16000); }
        finally { if (source != null) source.close(); output.reset(); }
    }
    public void cancel() {
        Player source;
        synchronized (this) { cancelled = true; source = player; player = null; record = null; }
        if (source != null) try { source.close(); } catch (Exception ignored) { }
        output.reset();
    }
    private static final class Buffer extends OutputStream {
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        public synchronized void write(int value) throws IOException {
            if (bytes.size() >= AudioClip.MAX_INPUT) throw new IOException("Kayıt boyutu doldu."); bytes.write(value);
        }
        public synchronized void write(byte[] data, int offset, int length) throws IOException {
            if (length > AudioClip.MAX_INPUT - bytes.size()) throw new IOException("Kayıt boyutu doldu."); bytes.write(data, offset, length);
        }
        synchronized byte[] bytes() { return bytes.toByteArray(); }
        synchronized void reset() { bytes.reset(); }
    }
}
