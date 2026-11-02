package cepai;

import java.util.Hashtable;
import java.util.Vector;
import javax.microedition.lcdui.*;
import javax.microedition.midlet.MIDlet;

public final class CepAiMIDlet extends MIDlet implements CommandListener {
    private Display display;
    private boolean closed;
    private Config config;
    private final SettingsStore store = new SettingsStore();
    private final Conversation conversation = new Conversation();
    private HomeScreen home;
    private SettingsScreen settingsForm;
    private TextBox compose;
    private TextField keyField, modelField, ttsField;
    private ChoiceGroup rememberField, voiceReplyField;
    private String draft = "", status = "";
    private NetworkTask job;
    private CameraScreen camera;
    private FileBrowser files;
    private List modelList;
    private Alert confirmation;
    private VoiceScreen voiceScreen;
    private final AudioPlayback playback = new AudioPlayback();
    private int audioGeneration;
    private boolean modelListSpeech;
    private AudioClip lastSpeech;
    private String lastSpeechText = "", lastSpeechModel = "";
    private final Command write = new Command("Yaz", Command.OK, 1);
    private final Command talk = new Command("Konuş", Command.SCREEN, 1);
    private final Command audioFile = new Command("Ses dosyası", Command.SCREEN, 2);
    private final Command readAnswer = new Command("Yanıtı dinle", Command.SCREEN, 3);
    private final Command stopSound = new Command("Sesi durdur", Command.STOP, 2);
    private final Command ttsModels = new Command("Ses modelleri", Command.SCREEN, 5);
    private final Command send = new Command("Gönder", Command.OK, 1);
    private final Command settings = new Command("Ayarlar", Command.SCREEN, 2);
    private final Command clear = new Command("Yeni sohbet", Command.SCREEN, 3);
    private final Command help = new Command("Yardım", Command.HELP, 5);
    private final Command tls = new Command("TLS bilgisi", Command.SCREEN, 6);
    private final Command probe = new Command("HTTPS testi (anahtarsız)", Command.SCREEN, 4);
    private final Command exit = new Command("Çıkış", Command.EXIT, 9);
    private final Command back = new Command("Geri", Command.BACK, 1);
    private final Command save = new Command("Kaydet", Command.OK, 1);
    private final Command scan = new Command("Kamerayla QR", Command.SCREEN, 2);
    private final Command qrFile = new Command("QR dosyası", Command.SCREEN, 3);
    private final Command models = new Command("Bağlantı testi / Modeller", Command.SCREEN, 4);
    private final Command cancel = new Command("İsteği iptal et", Command.STOP, 1);
    private final Command yes = new Command("Evet", Command.OK, 1);

    protected synchronized void startApp() {
        if (closed) return;
        if (display == null) {
            display = Display.getDisplay(this); config = store.load();
            home = new HomeScreen(write, talk, cancel);
            home.addCommand(write); home.addCommand(talk); home.addCommand(audioFile); home.addCommand(settings); home.addCommand(clear);
            home.addCommand(help); home.addCommand(tls); home.addCommand(probe); home.addCommand(exit);
            home.setCommandListener(this);
            status = store.failed ? "Ayarlar okunamadı. Yeniden kaydedebilirsin." : "";
            renderHome();
        }
    }
    protected synchronized void pauseApp() {
        if (camera != null) { camera.close(); camera = null; display.setCurrent(settingsForm); }
        stopAudio(); if (voiceScreen != null) voiceScreen.pause();
    }
    protected synchronized void destroyApp(boolean unconditional) {
        closed = true;
        if (job != null) { job.cancel(); job = null; }
        if (camera != null) { camera.close(); camera = null; }
        if (files != null) { files.close(); files = null; }
        if (voiceScreen != null) { voiceScreen.close(); voiceScreen = null; }
        stopAudio(); lastSpeech = null; if (home != null) home.close();
        // Only settings are persisted. Conversation and drafts stay in memory.
    }
    void serial(final Runnable action) {
        if (display == null) return;
        display.callSerially(new Runnable() { public void run() {
            synchronized (CepAiMIDlet.this) { if (!closed) action.run(); }
        }});
    }
    synchronized void showScreen(Displayable screen) { if (!closed) display.setCurrent(screen); }
    private void renderHome() {
        renderHome(true);
    }
    private void renderHome(boolean show) {
        if (conversation.size() > 0) home.addCommand(readAnswer); else home.removeCommand(readAnswer);
        if (job != null) home.addCommand(cancel); else home.removeCommand(cancel);
        home.update(config, conversation, job, status);
        if (show) display.setCurrent(home);
    }
    private void openCompose() {
        if (job != null) { message("İstek sürüyor", "Önce yanıtı bekle veya isteği iptal et.", home); return; }
        if (!Config.validKey(config.key)) { openSettings(); return; }
        compose = new TextBox("Gemini'ye sor", draft, 2048, TextField.ANY);
        compose.addCommand(send); compose.addCommand(back); compose.setCommandListener(this);
        display.setCurrent(compose);
    }
    private void openSettings() {
        if (job != null) { message("İstek sürüyor", "Önce yanıtı bekle veya isteği iptal et.", home); return; }
        keyField = new TextField("Google API anahtarı", config.key, 200, TextField.PASSWORD | TextField.NON_PREDICTIVE);
        modelField = new TextField("Model", config.model, 80, TextField.NON_PREDICTIVE);
        ttsField = new TextField("Seslendirme (TTS) modeli", config.ttsModel, 80, TextField.NON_PREDICTIVE);
        voiceReplyField = new ChoiceGroup("Sesli kullanım", Choice.MULTIPLE);
        voiceReplyField.append("Sesli soruya cevabı otomatik oku", null);
        voiceReplyField.setSelectedIndex(0, config.voiceReplies);
        rememberField = new ChoiceGroup("Anahtar saklama", Choice.MULTIPLE);
        rememberField.append("Anahtarı bu telefonda hatırla", null);
        rememberField.setSelectedIndex(0, config.remember);
        settingsForm = new SettingsScreen(this, keyField, modelField, ttsField, rememberField, voiceReplyField);
        settingsForm.addCommand(save); settingsForm.addCommand(scan); settingsForm.addCommand(qrFile);
        settingsForm.addCommand(models); settingsForm.addCommand(ttsModels); settingsForm.addCommand(probe); settingsForm.addCommand(back); settingsForm.setCommandListener(settingsForm);
        display.setCurrent(settingsForm);
    }
    private void message(String title, String text, Displayable next) {
        Alert alert = new Alert(title, text, null, null); alert.setTimeout(Alert.FOREVER);
        display.setCurrent(alert, next);
    }
    private void importQr(String text) {
        try {
            Config scanned = Config.fromQr(text);
            keyField.setString(scanned.key);
            if (text.trim().startsWith("{") && Json.object(Json.parse(text.trim())).get("model") != null)
                modelField.setString(scanned.model);
            message("Anahtar okundu", "Anahtar ayarlara aktarıldı. Saklamak için Kaydet'e bas.", settingsForm);
        } catch (Exception invalid) {
            message("QR uygun değil", "QR yalnızca Google API anahtarını veya desteklenen anahtar JSON'unu içermeli. "
                + "Web adresleri açılmaz. Yeniden tara veya elle gir.", settingsForm);
        }
    }
    private void sendQuestion() {
        String text = compose.getString().trim(); draft = compose.getString();
        if (text.length() == 0) { message("Sorunu yaz", "Göndermeden önce bir soru yaz.", compose); return; }
        if (job != null) return;
        job = new NetworkTask(this, config, text, conversation.request(text), false);
        status = ""; renderHome(); new Thread(job).start();
    }
    private void listModels() {
        listModels(false);
    }
    private void listModels(boolean speech) {
        if (job != null) return;
        String key = keyField.getString().trim();
        if (!Config.validKey(key)) { message("Anahtar gerekli", "Önce API anahtarını gir veya QR ile al.", settingsForm); return; }
        Config test = new Config(); test.key = key;
        job = new NetworkTask(this, test, "", "", speech ? NetworkTask.TTS_MODELS : NetworkTask.MODELS);
        status = ""; renderHome(); new Thread(job).start();
    }
    private void probeConnection() {
        if (job != null) return;
        job = new NetworkTask(this, new Config(), "", "", false, true);
        status = ""; renderHome(); new Thread(job).start();
    }
    void networkResult(final NetworkTask task, final String json, final String error) {
        serial(new Runnable() { public void run() {
            if (job != task) return; // Canceled, expired or obsolete requests cannot change the screen.
            job = null;
            if (error != null) { status = task.connectionTest ? "HTTPS testi başarısız. Ayrıntı hata ekranında."
                    : (task.operation == NetworkTask.VOICE ? "Sesli istek tamamlanamadı. Kayıt yeniden gönderilebilir."
                        : "İstek tamamlanamadı. Sorun taslağın duruyor."); renderHome(false);
                message("Bağlantı", error, task.listModels ? settingsForm
                    : (task.operation == NetworkTask.VOICE && voiceScreen != null ? (Displayable) voiceScreen : home)); return; }
            if (task.connectionTest) {
                status = "Google HTTPS bağlantısı çalışıyor (HTTP " + json + ")."; renderHome(false);
                message("HTTPS çalışıyor", "Google'a güvenli bağlantı kuruldu (HTTP " + json
                    + "). Bu testte API anahtarı gönderilmediği için 400/401/403 yanıtı normaldir. "
                    + "Güvenli kanal çalışıyor; sohbet hatasında anahtar/model/kota kontrol edilmeli.", home);
                return;
            }
            try {
                if (task.listModels) { showModels(json, task.operation == NetworkTask.TTS_MODELS); return; }
                String answer, question;
                if (task.operation == NetworkTask.VOICE) {
                    String[] parts = Speech.voiceAnswer(json); question = parts[0]; answer = parts[1];
                    if (voiceScreen != null) { voiceScreen.close(); voiceScreen = null; }
                } else { question = task.question; answer = GoogleResponse.answer(json); draft = ""; }
                conversation.add(question, answer); lastSpeech = null; status = ""; renderHome();
                home.scrollToEnd();
                if (task.operation == NetworkTask.VOICE && config.voiceReplies) startSpeech(answer);
            } catch (Exception invalid) {
                status = "Yanıt okunamadı. Sorun taslağın duruyor."; renderHome(false);
                message("Yanıt alınamadı", task.operation == NetworkTask.VOICE
                    ? "Konuşma anlaşılamadı veya yanıt tamamlanmadı. Kaydı yeniden gönder ya da daha net bir kayıt dene."
                    : "Google metin yanıtı üretmedi veya yanıt tamamlanmadı. Sorunu farklı yazmayı dene.", task.listModels ? settingsForm
                        : (task.operation == NetworkTask.VOICE && voiceScreen != null ? (Displayable) voiceScreen : home));
            }
        }});
    }
    private void showModels(String json, boolean speech) throws Exception {
        Hashtable root = Json.object(Json.parse(json)); Vector available = Json.array(root.get("models"));
        modelListSpeech = speech;
        modelList = new List(speech ? "Seslendirme modeli seç" : "Bağlandı • model seç", List.IMPLICIT);
        for (int i = 0; i < available.size(); i++) {
            Hashtable info = Json.object(available.elementAt(i)); String name = Json.text(info.get("name"));
            if (name.startsWith("models/")) name = name.substring(7);
            Vector methods = Json.array(info.get("supportedGenerationMethods")); boolean generate = false;
            for (int j = 0; j < methods.size(); j++) if ("generateContent".equals(methods.elementAt(j))) generate = true;
            if (generate && Config.validModel(name) && (speech ? name.indexOf("tts") >= 0
                    : (name.indexOf("image") < 0 && name.indexOf("tts") < 0 && name.indexOf("live") < 0
                        && name.indexOf("robotics") < 0))) modelList.append(name, null);
        }
        if (modelList.size() == 0) {
            message("Google'a bağlandı", speech ? "Bu listede TTS modeli bulunamadı. Hesabında açık olan seslendirme modelini elle yazabilirsin."
                : "Bu listede metin modeli bulunamadı. AI Studio'daki model adını elle yazabilirsin.", settingsForm); return;
        }
        modelList.addCommand(back); modelList.setCommandListener(this); display.setCurrent(modelList);
    }
    private void showHelp() {
        Form form = new Form("noki Ai • yardım");
        form.append("1. Ayarlar'dan API anahtarını gir. Kamerayla QR veya QR dosyası kullanabilirsin.\n"
            + "2. Bağlantı testi / Modeller ile Google'a erişimi kontrol et; model seçip Kaydet'e bas.\n"
            + "3. Ana ekrandaki Yaz ile sorunu gönder.\n\n"
            + "QR kodu anahtarın kendisini metin olarak içermeli. QR okuma tamamen telefonda yapılır; "
            + "kamera görüntüsü Google'a gönderilmez. Paket içindeki anahtar-qr.html yerel QR oluşturur.\n\n"
            + "İstekler yalnızca generativelanguage.googleapis.com adresine HTTPS ile gider. "
            + "Son 5 soru-cevap, sonraki soruya bağlam olarak eklenir. Yeni sohbet bu bağlamı siler.\n\n"
            + "Yanıt beklerken Menü > İsteği iptal et kullanılabilir. Başarısız istekte taslağın korunur. "
            + "Google API kotası ve hesap ücretlendirmesi geçerlidir.\n\n"
            + "HTTPS testi (anahtarsız), API anahtarı göndermeden güvenli kanalı denetler. Hata ekranındaki "
            + "Aşama / Tür / Cihaz satırları sorunu ayırmaya yardımcı olur.\n\n"
            + "Konuş: 15 saniyeye kadar kayıt yap, Durdur ve Gönder. Duyulan cümle ve yanıt ekranda gösterilir. "
            + "Kayıt Google'a gönderilir; telefona kaydedilmez. Sesli cevabı Ayarlar'dan kapatabilirsin.\n\n"
            + "Java mikrofonu WAV desteklemiyorsa telefonun Ses Kaydedici'sinde kaliteyi Yüksek seç, "
            + "kısa WAV kaydet ve Ses dosyası ile seç.\n\n"
            + "Yanıtı dinle: cevabın ilk yaklaşık 180 karakterini Google TTS ile seslendirir. "
            + "Ek API isteği ve model erişimi gerekir. Sesi durdur ile oynatmayı kesebilirsin.\n\n"
            + "Sürüm 1.2.0 • MIDP 2.0 / CLDC 1.1");
        form.addCommand(back); form.addCommand(tls); form.addCommand(probe); form.setCommandListener(this); display.setCurrent(form);
    }
    private void showTls() {
        Form form = new Form("E63 • TLS bağlantısı");
        form.append("Google, güncel HTTPS/TLS desteği ister. E63'ün orijinal HTTPS bileşeni bağlantı kuramayabilir.\n\n"
            + "Telefonunda TLS 1.2 desteği varsa doğrudan bağlanabilirsin. Yoksa nnproject'in S60v3 / EKA2 "
            + "için telefon içi TLS yaması gerekebilir: https://nnproject.cc/tls/\n\n"
            + "Bu, telefonun sistem dosyalarını etkileyen ayrı bir kurulumdur. Uygulama bunu otomatik kurmaz. "
            + "Bilgisayarda çalışan köprü veya aracı sunucu kullanılmaz.\n\n"
            + "Telefonun tarih ve saatini de doğru ayarla. TLS hatasında sertifika doğrulaması atlanmaz.");
        form.addCommand(back); form.setCommandListener(this); display.setCurrent(form);
    }
    void cameraResult(final CameraScreen source, final String text) {
        serial(new Runnable() { public void run() { if (camera != source) return; camera = null; importQr(text); }});
    }
    void cameraError(final CameraScreen source, final String text) {
        serial(new Runnable() { public void run() { if (camera != source) return; camera = null;
            message("Kamera", text, settingsForm); }});
    }
    void cameraRetry(final CameraScreen source) {
        serial(new Runnable() { public void run() { if (camera == source)
            message("QR okunamadı", "Kodu büyük ve net göster. E63 sabit odaklıdır; çok yaklaştırma. "
                + "Olmazsa QR PNG dosyasını telefona aktar ve QR dosyası'nı kullan.", source); }});
    }
    synchronized void cameraBack(CameraScreen source) {
        if (camera == source) { camera = null; display.setCurrent(settingsForm); }
    }
    void fileStatus(final FileBrowser source, final String text) {
        serial(new Runnable() { public void run() { if (files != source) return;
            Form progress = new Form(source.audio ? "Ses dosyası" : "QR dosyası"); progress.append(text);
            progress.addCommand(back); progress.setCommandListener(CepAiMIDlet.this); display.setCurrent(progress);
        }});
    }
    void fileDisplay(FileBrowser source, Displayable screen) { if (files == source) display.setCurrent(screen); }
    void fileError(final FileBrowser source, final String text) {
        serial(new Runnable() { public void run() { if (files != source) return;
            files.close(); files = null; message(source.audio ? "Ses dosyası" : "QR dosyası", text, source.audio ? (Displayable) home : settingsForm); }});
    }
    void fileResult(final FileBrowser source, final String text) {
        serial(new Runnable() { public void run() { if (files != source) return;
            files.close(); files = null; importQr(text); }});
    }
    synchronized void fileBack(FileBrowser source) {
        if (files == source) { files = null; if (source.audio) renderHome(); else display.setCurrent(settingsForm); }
    }
    private void openVoice() {
        if (job != null) { message("İstek sürüyor", "Önce yanıtı bekle veya isteği iptal et.", home); return; }
        if (!Config.validKey(config.key)) { openSettings(); return; }
        stopAudio(); if (voiceScreen != null) voiceScreen.close();
        voiceScreen = new VoiceScreen(this); display.setCurrent(voiceScreen); voiceScreen.start();
    }
    private void openAudioFile() {
        if (job != null) return;
        if (!Config.validKey(config.key)) { openSettings(); return; }
        stopAudio(); files = new FileBrowser(this, true); files.start();
    }
    void voiceFileResult(final FileBrowser source, final AudioClip clip) {
        serial(new Runnable() { public void run() {
            if (files != source) return; files.close(); files = null;
            if (voiceScreen != null) voiceScreen.close(); voiceScreen = new VoiceScreen(CepAiMIDlet.this);
            voiceScreen.preview(clip); display.setCurrent(voiceScreen);
        }});
    }
    synchronized void sendVoice(VoiceScreen source, AudioClip clip) {
        if (voiceScreen != source || job != null || closed) return;
        stopAudio(); job = new NetworkTask(this, config, clip, conversation);
        status = ""; renderHome(); new Thread(job).start();
    }
    synchronized void voiceBack(VoiceScreen source) {
        if (voiceScreen != source) return; voiceScreen = null; stopAudio(); renderHome();
    }
    private void startSpeech(String answer) {
        if (job != null || answer.length() == 0) return;
        if (!Config.validKey(config.key)) { openSettings(); return; }
        stopAudio();
        if (lastSpeech != null && lastSpeechText.equals(answer) && lastSpeechModel.equals(config.ttsModel)) { playClip(lastSpeech); return; }
        job = new NetworkTask(this, config, answer, Speech.request(answer, config.ttsModel), NetworkTask.TTS);
        status = "Cevabın ilk yaklaşık 180 karakteri seslendiriliyor."; renderHome(); new Thread(job).start();
    }
    void speechResult(final NetworkTask task, final AudioClip audio, final String error) {
        serial(new Runnable() { public void run() {
            if (job != task) return; job = null;
            if (error != null) {
                status = "Metin yanıtı hazır; seslendirme tamamlanamadı."; renderHome(false);
                message("Seslendirme", error + "\nMetin yanıtın korunuyor. Ayarlar > Ses modelleri ile hesabında açık bir TTS modeli seçebilirsin.", home);
            } else {
                lastSpeech = audio; lastSpeechText = task.question; lastSpeechModel = config.ttsModel;
                status = "Yanıtın ilk bölümü sesli okunuyor."; renderHome(); playClip(audio);
            }
        }});
    }
    synchronized void playClip(AudioClip clip) {
        stopAudio(); final int token = audioGeneration; home.addCommand(stopSound);
        playback.play(clip, new AudioPlayback.Listener() { public void finished(final String error) {
            serial(new Runnable() { public void run() {
                if (audioGeneration != token) return; home.removeCommand(stopSound);
                if (error != null) {
                    Displayable current = display.getCurrent();
                    message("Ses oynatma", error, current == null || current instanceof Alert ? home : current);
                }
            }});
        }});
    }
    synchronized void stopAudio() { audioGeneration++; playback.stop(); if (home != null) home.removeCommand(stopSound); }
    public synchronized void commandAction(Command command, Displayable from) {
        if (closed) return;
        if (command == exit) { destroyApp(true); notifyDestroyed(); }
        else if (command == write) openCompose();
        else if (command == talk) openVoice();
        else if (command == audioFile) openAudioFile();
        else if (command == stopSound) stopAudio();
        else if (command == readAnswer && conversation.size() > 0) startSpeech(conversation.answer(conversation.size() - 1));
        else if (command == settings) openSettings();
        else if (command == send && from == compose) sendQuestion();
        else if (command == help) showHelp();
        else if (command == tls) showTls();
        else if (command == probe) probeConnection();
        else if (command == cancel && job != null) {
            NetworkTask old = job; job = null; old.cancel();
            status = "İstek iptal edildi."; renderHome(false);
            if (old.operation == NetworkTask.VOICE && voiceScreen != null) display.setCurrent(voiceScreen); else display.setCurrent(home);
        } else if (command == clear && job == null) {
            confirmation = new Alert("Yeni sohbet?", "Bu oturumdaki soru ve yanıtlar silinecek. API anahtarı korunur.", null, null);
            confirmation.addCommand(yes); confirmation.addCommand(back); confirmation.setCommandListener(this);
            display.setCurrent(confirmation);
        } else if (from == confirmation) {
            if (command == yes) { stopAudio(); conversation.clear(); draft = ""; status = ""; lastSpeech = null; } renderHome();
        } else if (command == save && from == settingsForm) {
            String key = keyField.getString().trim(), model = modelField.getString().trim(), ttsModel = ttsField.getString().trim();
            if ((key.length() > 0 && !Config.validKey(key)) || !Config.validModel(model) || !Config.validModel(ttsModel)) {
                message("Ayarları kontrol et", "API anahtarında boşluk veya web adresi olmamalı. "
                    + "Model adı gemini- ile başlamalı. Google AI Studio'daki değerleri kullan.", settingsForm); return;
            }
            config.key = key; config.model = model; config.remember = rememberField.isSelected(0);
            config.ttsModel = ttsModel; config.voiceReplies = voiceReplyField.isSelected(0);
            store.save(config); status = store.failed ? "Ayarlar diske kaydedilemedi; bu oturumda kullanılabilir."
                : (key.length() == 0 ? "API anahtarı kaldırıldı." : "Ayarlar kaydedildi. Yaz ile başlayabilirsin.");
            renderHome();
        } else if (command == scan && from == settingsForm) {
            camera = new CameraScreen(this); display.setCurrent(camera); camera.start();
        } else if (command == qrFile && from == settingsForm) {
            files = new FileBrowser(this); files.start();
        } else if (command == models && from == settingsForm) listModels();
        else if (command == ttsModels && from == settingsForm) listModels(true);
        else if (from == modelList && command == List.SELECT_COMMAND) {
            int selected = modelList.getSelectedIndex();
            if (selected >= 0) { if (modelListSpeech) ttsField.setString(modelList.getString(selected));
                else modelField.setString(modelList.getString(selected)); } display.setCurrent(settingsForm);
        } else if (command == back) {
            if (from == compose) draft = compose.getString();
            if (files != null) { boolean audio = files.audio; files.close(); files = null;
                if (audio) renderHome(); else display.setCurrent(settingsForm); }
            else if (from == modelList) display.setCurrent(settingsForm);
            else renderHome();
        }
    }
}
