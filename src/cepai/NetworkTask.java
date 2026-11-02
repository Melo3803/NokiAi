package cepai;

import java.io.*;
import java.util.Timer;
import java.util.TimerTask;
import javax.microedition.io.*;

/** Talks only to Google's fixed HTTPS hostname. Never sends a key in the URL. */
final class NetworkTask implements Runnable {
    static final int CHAT = 0, MODELS = 1, PROBE = 2, VOICE = 3, TTS = 4, TTS_MODELS = 5;
    static final String HOST = "https://generativelanguage.googleapis.com/v1beta/";
    private final CepAiMIDlet app;
    final boolean listModels;
    final boolean connectionTest;
    final int operation;
    final String question;
    private final String key, model, payload;
    private final AudioClip voice;
    private final Conversation history;
    private volatile boolean cancelled, timedOut;
    private HttpConnection connection;
    private Timer timeout;

    NetworkTask(CepAiMIDlet owner, Config config, String text, String json, boolean models) {
        this(owner, config, text, json, models, false);
    }
    NetworkTask(CepAiMIDlet owner, Config config, String text, String json, boolean models, boolean probe) {
        this(owner, config, text, json, probe ? PROBE : (models ? MODELS : CHAT));
    }
    NetworkTask(CepAiMIDlet owner, Config config, String text, String json, int kind) {
        this(owner, config, text, json, kind, null, null);
    }
    NetworkTask(CepAiMIDlet owner, Config config, AudioClip clip, Conversation conversation) {
        this(owner, config, "[Sesli soru]", "", VOICE, clip, conversation);
    }
    private NetworkTask(CepAiMIDlet owner, Config config, String text, String json, int kind, AudioClip clip, Conversation conversation) {
        app = owner; key = config.key; model = kind == TTS ? config.ttsModel : config.model;
        question = text; payload = json; listModels = kind == MODELS || kind == TTS_MODELS;
        connectionTest = kind == PROBE; operation = kind; voice = clip; history = conversation;
    }
    synchronized void cancel() {
        cancelled = true;
        if (timeout != null) { timeout.cancel(); timeout = null; }
        if (connection != null) try { connection.close(); } catch (IOException ignored) { }
    }
    public void run() {
        InputStream input = null; OutputStream output = null;
        String response = null, error = null;
        String stage = "HTTPS bağlantısı açma";
        try {
            String body = voice == null ? payload : history.requestVoice(voice);
            synchronized (this) {
                if (cancelled) return;
                timeout = new Timer();
                timeout.schedule(new TimerTask() {
                    public void run() {
                        timedOut = true; NetworkTask.this.cancel();
                        if (operation == TTS) app.speechResult(NetworkTask.this, null,
                            "Seslendirme 60 saniyede tamamlanmadı. Biraz sonra tekrar dene.");
                        else app.networkResult(NetworkTask.this, null, "Bağlantı 60 saniyede tamamlanmadı. Biraz sonra tekrar dene.");
                    }
                }, 60000);
            }
            HttpConnection opened = (HttpConnection) Connector.open(HOST + (connectionTest ? "models?pageSize=1"
                : (listModels ? "models?pageSize=100" : "models/" + model + ":generateContent")), Connector.READ_WRITE, true);
            synchronized (this) {
                connection = opened;
                if (cancelled) { opened.close(); return; }
            }
            stage = "İstek hazırlama";
            opened.setRequestMethod(listModels || connectionTest ? HttpConnection.GET : HttpConnection.POST);
            if (!connectionTest) opened.setRequestProperty("x-goog-api-key", key);
            opened.setRequestProperty("Accept", "application/json");
            if (!listModels && !connectionTest) {
                byte[] bytes = body.getBytes("UTF-8");
                opened.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                opened.setRequestProperty("Content-Length", "" + bytes.length);
                stage = "Gönderim / TLS el sıkışması";
                output = opened.openOutputStream(); output.write(bytes); output.close(); output = null;
            }
            stage = "HTTP yanıtı / TLS el sıkışması";
            int status = opened.getResponseCode();
            if (connectionTest && status >= 200 && status <= 599) response = "" + status;
            else if (status != HttpConnection.HTTP_OK) error = GoogleResponse.error(status);
            else {
                stage = "Yanıt okuma";
                input = opened.openInputStream();
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] bytes = new byte[1024]; int count;
                while ((count = input.read(bytes)) != -1) {
                    if (cancelled) return;
                    if (buffer.size() + count > (operation == TTS ? Speech.MAX_JSON : 65536)) throw new IOException("response too large");
                    buffer.write(bytes, 0, count);
                }
                response = new String(buffer.toByteArray(), "UTF-8");
            }
        } catch (SecurityException denied) {
            error = "İnternet izni verilmedi. Telefonun uygulama erişim ayarlarından ağ iznini aç.";
        } catch (IOException failed) {
            error = ConnectionFailure.describe(failed, stage, key);
        } catch (Exception unexpected) {
            error = "İstek tamamlanamadı. Bağlantını ve ayarlarını kontrol edip yeniden dene.";
        } finally {
            if (input != null) try { input.close(); } catch (IOException ignored) { }
            if (output != null) try { output.close(); } catch (IOException ignored) { }
            synchronized (this) {
                if (timeout != null) { timeout.cancel(); timeout = null; }
                if (connection != null) try { connection.close(); } catch (IOException ignored) { }
                connection = null;
            }
        }
        if (!cancelled && !timedOut) {
            if (operation == TTS) {
                AudioClip audio = null;
                if (error == null) try { audio = Speech.audio(response); }
                    catch (IOException invalid) { error = invalid.getMessage(); }
                if (!cancelled) app.speechResult(this, audio, error);
            } else app.networkResult(this, response, error);
        }
    }
}
