package cepai;

import java.io.*;
import java.util.Enumeration;
import java.util.Vector;
import javax.microedition.io.Connector;
import javax.microedition.io.file.*;
import javax.microedition.lcdui.*;

final class FileBrowser implements CommandListener {
    private final CepAiMIDlet app;
    final boolean audio;
    private String directory = "";
    private final Vector paths = new Vector();
    private List list;
    private volatile boolean closed;
    private final Command back = new Command("Geri", Command.BACK, 1);
    FileBrowser(CepAiMIDlet owner) { this(owner, false); }
    FileBrowser(CepAiMIDlet owner, boolean sound) { app = owner; audio = sound; }
    void start() { showDirectory(); }
    void close() { closed = true; }
    private void showDirectory() {
        final String target = directory;
        app.fileStatus(this, "Klasör okunuyor…");
        new Thread(new Runnable() { public void run() {
            FileConnection connection = null;
            try {
                final List next = new List(target.length() == 0 ? (audio ? "Ses dosyası • sürücüler" : "QR dosyası • sürücüler")
                    : (audio ? "Kısa WAV kaydı seç" : "QR PNG/JPG seç"), List.IMPLICIT);
                final Vector nextPaths = new Vector();
                if (target.length() == 0) {
                    Enumeration roots = FileSystemRegistry.listRoots();
                    while (roots.hasMoreElements()) {
                        String root = (String) roots.nextElement(); next.append(root, null); nextPaths.addElement(root);
                    }
                } else {
                    next.append(".. / üst klasör", null); nextPaths.addElement("..");
                    connection = (FileConnection) Connector.open("file:///" + target, Connector.READ);
                    Enumeration entries = connection.list();
                    while (entries.hasMoreElements() && nextPaths.size() < 250) {
                        String name = (String) entries.nextElement(), lower = name.toLowerCase();
                        if (name.endsWith("/") || (audio ? lower.endsWith(".wav")
                                : (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")))) {
                            next.append(name, null); nextPaths.addElement(name);
                        }
                    }
                }
                next.addCommand(back); next.setCommandListener(FileBrowser.this);
                app.serial(new Runnable() { public void run() {
                    if (closed) return;
                    list = next; paths.removeAllElements();
                    for (int i = 0; i < nextPaths.size(); i++) paths.addElement(nextPaths.elementAt(i));
                    app.fileDisplay(FileBrowser.this, list);
                }});
            } catch (Exception unavailable) {
                app.fileError(FileBrowser.this, "Klasör açılamadı. Dosya okuma iznini kontrol et.");
            } finally { if (connection != null) try { connection.close(); } catch (IOException ignored) { } }
        }}).start();
    }
    private void read(final String path) {
        app.fileStatus(this, audio ? "Ses dosyası okunuyor…" : "QR görseli okunuyor…");
        new Thread(new Runnable() { public void run() {
            FileConnection file = null; InputStream input = null;
            try {
                file = (FileConnection) Connector.open("file:///" + path, Connector.READ);
                int limit = audio ? AudioClip.MAX_INPUT : 2000000;
                if (file.fileSize() > limit) throw new IOException("large");
                input = file.openInputStream(); ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                byte[] block = new byte[2048]; int n;
                while ((n = input.read(block)) != -1) {
                    if (closed) return;
                    if (bytes.size() + n > limit) throw new IOException("large");
                    bytes.write(block, 0, n);
                }
                byte[] data = bytes.toByteArray();
                if (audio) {
                    AudioClip clip = AudioClip.fromWav(data, 20000);
                    if (!closed) app.voiceFileResult(FileBrowser.this, clip);
                } else {
                    String decoded = QrDecoder.decode(Image.createImage(data, 0, data.length));
                    if (!closed) app.fileResult(FileBrowser.this, decoded);
                }
            } catch (Exception unreadable) {
                if (!closed) app.fileError(FileBrowser.this, audio
                    ? "Ses okunamadı. En fazla 20 saniye / 768 KiB PCM WAV ve dosya okuma izni gerekli. Ses Kaydedici kalitesini Yüksek seç."
                    : "QR okunamadı. Net bir PNG/JPG ve dosya okuma izni gerekiyor.");
            } finally {
                if (input != null) try { input.close(); } catch (IOException ignored) { }
                if (file != null) try { file.close(); } catch (IOException ignored) { }
            }
        }}).start();
    }
    public void commandAction(Command command, Displayable source) {
        if (command == back) { closed = true; app.fileBack(this); return; }
        if (command != List.SELECT_COMMAND || list == null || list.size() == 0) return;
        int index = list.getSelectedIndex(); if (index < 0 || index >= paths.size()) return;
        String entry = (String) paths.elementAt(index);
        if (entry.equals("..")) {
            int slash = directory.lastIndexOf('/', directory.length() - 2);
            directory = slash < 0 ? "" : directory.substring(0, slash + 1); showDirectory();
        } else if (entry.endsWith("/")) { directory += entry; showDirectory(); }
        else read(directory + entry);
    }
}
