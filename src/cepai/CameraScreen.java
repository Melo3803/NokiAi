package cepai;

import javax.microedition.lcdui.*;
import javax.microedition.media.*;
import javax.microedition.media.control.VideoControl;

final class CameraScreen extends Canvas implements CommandListener {
    private final CepAiMIDlet app;
    private Player player;
    private VideoControl video;
    private volatile boolean closed, busy;
    private String status = "Kamera açılıyor…";
    private final Command scan = new Command("Tara", Command.OK, 1);
    private final Command back = new Command("Geri", Command.BACK, 2);
    CameraScreen(CepAiMIDlet owner) {
        app = owner; addCommand(scan); addCommand(back); setCommandListener(this);
    }
    void start() {
        new Thread(new Runnable() { public void run() {
            Player opened = null;
            try {
                try { opened = Manager.createPlayer("capture://image"); }
                catch (Exception tryVideo) { opened = Manager.createPlayer("capture://video"); }
                opened.realize();
                synchronized (CameraScreen.this) {
                    if (closed) { opened.close(); return; }
                    player = opened;
                    video = (VideoControl) player.getControl("VideoControl");
                    if (video == null) throw new MediaException("No video control");
                    video.initDisplayMode(VideoControl.USE_DIRECT_VIDEO, CameraScreen.this);
                    video.setDisplayLocation(8, 27);
                    try { video.setDisplaySize(getWidth() - 16, getHeight() - 53); }
                    catch (MediaException fixedPreview) { }
                    video.setVisible(true); player.start();
                    status = "QR'ı göster • Orta tuş: tara";
                }
                repaint();
            } catch (Exception unavailable) {
                if (opened != null) try { opened.close(); } catch (Exception ignored) { }
                close();
                app.cameraError(CameraScreen.this, "Kamera açılamadı. Kamera iznini kontrol et. "
                    + "Ayarlar > QR dosyası ile hafıza kartındaki QR görselini de okuyabilirsin.");
            }
        }}).start();
    }
    synchronized void close() {
        closed = true;
        if (player != null) { try { player.close(); } catch (Exception ignored) { } player = null; }
        video = null;
    }
    private synchronized void scan() {
        if (closed || busy || video == null) return;
        busy = true; status = "Okunuyor…"; repaint();
        new Thread(new Runnable() { public void run() {
            try {
                VideoControl capture;
                synchronized (CameraScreen.this) { capture = video; }
                if (capture == null) return;
                byte[] bytes;
                try { bytes = capture.getSnapshot("encoding=jpeg&width=640&height=480"); }
                catch (MediaException defaultSize) { bytes = capture.getSnapshot(null); }
                if (bytes == null || bytes.length == 0 || bytes.length > 2000000) throw new MediaException("No snapshot");
                String decoded = QrDecoder.decode(Image.createImage(bytes, 0, bytes.length));
                if (!closed) { close(); app.cameraResult(CameraScreen.this, decoded); }
            } catch (Exception unreadable) {
                if (!closed) {
                    busy = false; status = "Okunamadı • tekrar tara"; repaint();
                    app.cameraRetry(CameraScreen.this);
                }
            }
        }}).start();
    }
    protected void paint(Graphics g) {
        g.setColor(0x101923); g.fillRect(0, 0, getWidth(), getHeight());
        g.setColor(0x70DEB4); g.setFont(Font.getDefaultFont());
        g.drawString("API ANAHTARI • QR", getWidth() / 2, 5, Graphics.TOP | Graphics.HCENTER);
        g.setColor(0xFFFFFF); g.drawString(status, getWidth() / 2, getHeight() - 22, Graphics.TOP | Graphics.HCENTER);
    }
    protected void keyPressed(int key) {
        if (key == ' ' || key == 's' || key == 'S') scan();
        else try { if (getGameAction(key) == FIRE) scan(); } catch (IllegalArgumentException ignored) { }
    }
    public void commandAction(Command command, Displayable source) {
        if (command == scan) scan(); else if (command == back) { close(); app.cameraBack(this); }
    }
}
