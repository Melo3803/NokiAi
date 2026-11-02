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
    private final Command write = new Command("Write", Command.OK, 1);
    private final Command talk = new Command("Speak", Command.SCREEN, 1);
    private final Command audioFile = new Command("Audio file", Command.SCREEN, 2);
    private final Command readAnswer = new Command("Read aloud", Command.SCREEN, 3);
    private final Command stopSound = new Command("Stop audio", Command.STOP, 2);
    private final Command ttsModels = new Command("Voice models", Command.SCREEN, 5);
    private final Command send = new Command("Send", Command.OK, 1);
    private final Command settings = new Command("Settings", Command.SCREEN, 2);
    private final Command clear = new Command("New chat", Command.SCREEN, 3);
    private final Command help = new Command("Help", Command.HELP, 5);
    private final Command tls = new Command("TLS info", Command.SCREEN, 6);
    private final Command probe = new Command("HTTPS test (no key)", Command.SCREEN, 4);
    private final Command exit = new Command("Exit", Command.EXIT, 9);
    private final Command back = new Command("Back", Command.BACK, 1);
    private final Command save = new Command("Save", Command.OK, 1);
    private final Command scan = new Command("Scan QR", Command.SCREEN, 2);
    private final Command qrFile = new Command("QR file", Command.SCREEN, 3);
    private final Command models = new Command("Chat models", Command.SCREEN, 4);
    private final Command cancel = new Command("Cancel request", Command.STOP, 1);
    private final Command yes = new Command("Yes", Command.OK, 1);

    protected synchronized void startApp() {
        if (closed) return;
        if (display == null) {
            display = Display.getDisplay(this); config = store.load();
            home = new HomeScreen(write, talk, cancel);
            home.addCommand(write); home.addCommand(talk); home.addCommand(audioFile); home.addCommand(settings); home.addCommand(clear);
            home.addCommand(help); home.addCommand(tls); home.addCommand(probe); home.addCommand(exit);
            home.setCommandListener(this);
            status = store.failed ? "Could not load settings. Please save them again." : "";
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
        if (job != null) { message("Request in progress", "Wait for the reply or cancel the current request.", home); return; }
        if (!Config.validKey(config.key)) { openSettings(); return; }
        compose = new TextBox("Ask Gemini", draft, 2048, TextField.ANY);
        compose.addCommand(send); compose.addCommand(back); compose.setCommandListener(this);
        display.setCurrent(compose);
    }
    private void openSettings() {
        if (job != null) { message("Request in progress", "Wait for the reply or cancel the current request.", home); return; }
        keyField = new TextField("Google API key", config.key, 200, TextField.PASSWORD | TextField.NON_PREDICTIVE);
        modelField = new TextField("Model", config.model, 80, TextField.NON_PREDICTIVE);
        ttsField = new TextField("Voice (TTS) model", config.ttsModel, 80, TextField.NON_PREDICTIVE);
        voiceReplyField = new ChoiceGroup("Voice settings", Choice.MULTIPLE);
        voiceReplyField.append("Read replies to voice questions automatically", null);
        voiceReplyField.setSelectedIndex(0, config.voiceReplies);
        rememberField = new ChoiceGroup("Key storage", Choice.MULTIPLE);
        rememberField.append("Remember the API key on this phone", null);
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
            message("Key scanned", "The key was added to Settings. Press Save to keep it.", settingsForm);
        } catch (Exception invalid) {
            message("Invalid QR", "The QR must contain a Google API key or supported key JSON. "
                + "Web addresses are not opened. Scan again or enter the key manually.", settingsForm);
        }
    }
    private void sendQuestion() {
        String text = compose.getString().trim(); draft = compose.getString();
        if (text.length() == 0) { message("Write a question", "Write a question before sending.", compose); return; }
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
        if (!Config.validKey(key)) { message("API key required", "Enter your API key or scan its QR first.", settingsForm); return; }
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
            if (error != null) { status = task.connectionTest ? "HTTPS test failed. See the error message for details."
                    : (task.operation == NetworkTask.VOICE ? "Voice request failed. You can send the recording again."
                        : "Request failed. Your draft is still available."); renderHome(false);
                message("Connection", error, task.listModels ? settingsForm
                    : (task.operation == NetworkTask.VOICE && voiceScreen != null ? (Displayable) voiceScreen : home)); return; }
            if (task.connectionTest) {
                status = "Google HTTPS connection works (HTTP " + json + ")."; renderHome(false);
                message("HTTPS works", "Securely connected to Google (HTTP " + json
                    + "). A 400/401/403 response is normal because this test sends no API key. "
                    + "The secure connection works. For chat errors, check your key, model and quota.", home);
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
                status = "Could not read the reply. Your draft is still available."; renderHome(false);
                message("No reply", task.operation == NetworkTask.VOICE
                    ? "Speech was not understood or the reply was incomplete. Resend the recording or try a clearer one."
                    : "Google returned no text or an incomplete reply. Try rephrasing your question.", task.listModels ? settingsForm
                        : (task.operation == NetworkTask.VOICE && voiceScreen != null ? (Displayable) voiceScreen : home));
            }
        }});
    }
    private void showModels(String json, boolean speech) throws Exception {
        Hashtable root = Json.object(Json.parse(json)); Vector available = Json.array(root.get("models"));
        modelListSpeech = speech;
        modelList = new List(speech ? "Choose a voice model" : "Connected • choose a model", List.IMPLICIT);
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
            message("Connected to Google", speech ? "No TTS model was found in this list. You can enter a voice model available to your account manually."
                : "No chat model was found in this list. You can enter the model name from AI Studio manually.", settingsForm); return;
        }
        modelList.addCommand(back); modelList.setCommandListener(this); display.setCurrent(modelList);
    }
    private void showHelp() {
        Form form = new Form("noki Ai • Help");
        form.append("1. Menu > Settings: enter your Google API key, or use Menu > Scan QR / QR file. Press Save.\n\n"
            + "2. Write: type a question and Send. Use the arrows to scroll replies; left/right scroll a page.\n\n"
            + "3. Speak: record for up to 15 seconds, then Stop and Send. Menu > Play recording lets you listen first.\n\n"
            + "If Java cannot record WAV, record a short WAV in the phone's Recorder using High quality, then choose Audio file.\n\n"
            + "Read aloud uses a separate Google TTS request and reads the first approximately 180 characters. "
            + "Choose an available TTS model in Settings > Menu > Voice models. Google quotas and pricing apply.\n\n"
            + "The phone connects directly to Google over HTTPS. Audio is uploaded only when you press Send. "
            + "QR images are decoded on the phone. The last 5 question/reply pairs stay in memory as context.\n\n"
            + "New chat clears the conversation. Remember key saves your API key without extra encryption.\n\n"
            + "Menu opens with the right softkey or #. The center key selects or edits. "
            + "HTTPS test checks the secure connection without sending an API key.\n\n"
            + "Version 1.2.1 • MIDP 2.0 / CLDC 1.1");
        form.addCommand(back); form.addCommand(tls); form.addCommand(probe); form.setCommandListener(this); display.setCurrent(form);
    }
    private void showTls() {
        Form form = new Form("HTTPS / TLS");
        form.append("Google requires modern HTTPS/TLS support. Original E63 firmware may be unable to connect.\n\n"
            + "For compatible Symbian phones, see https://nnproject.cc/tls/ for a separate system TLS update and its requirements. "
            + "This app does not install phone patches or use a PC bridge.\n\n"
            + "Check the phone's date and time. Certificate verification is never disabled. "
            + "Use HTTPS test to check the connection; Stage / Type / Device help identify errors.");
        form.addCommand(back); form.setCommandListener(this); display.setCurrent(form);
    }
    void cameraResult(final CameraScreen source, final String text) {
        serial(new Runnable() { public void run() { if (camera != source) return; camera = null; importQr(text); }});
    }
    void cameraError(final CameraScreen source, final String text) {
        serial(new Runnable() { public void run() { if (camera != source) return; camera = null;
            message("Camera", text, settingsForm); }});
    }
    void cameraRetry(final CameraScreen source) {
        serial(new Runnable() { public void run() { if (camera == source)
            message("QR not recognized", "Show a large, clear QR. The E63 has fixed focus, so do not hold it too close. "
                + "If needed, copy the QR PNG to your phone and use QR file.", source); }});
    }
    synchronized void cameraBack(CameraScreen source) {
        if (camera == source) { camera = null; display.setCurrent(settingsForm); }
    }
    void fileStatus(final FileBrowser source, final String text) {
        serial(new Runnable() { public void run() { if (files != source) return;
            Form progress = new Form(source.audio ? "Audio file" : "QR file"); progress.append(text);
            progress.addCommand(back); progress.setCommandListener(CepAiMIDlet.this); display.setCurrent(progress);
        }});
    }
    void fileDisplay(FileBrowser source, Displayable screen) { if (files == source) display.setCurrent(screen); }
    void fileError(final FileBrowser source, final String text) {
        serial(new Runnable() { public void run() { if (files != source) return;
            files.close(); files = null; message(source.audio ? "Audio file" : "QR file", text, source.audio ? (Displayable) home : settingsForm); }});
    }
    void fileResult(final FileBrowser source, final String text) {
        serial(new Runnable() { public void run() { if (files != source) return;
            files.close(); files = null; importQr(text); }});
    }
    synchronized void fileBack(FileBrowser source) {
        if (files == source) { files = null; if (source.audio) renderHome(); else display.setCurrent(settingsForm); }
    }
    private void openVoice() {
        if (job != null) { message("Request in progress", "Wait for the reply or cancel the current request.", home); return; }
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
        status = "Reading the first approximately 180 characters aloud."; renderHome(); new Thread(job).start();
    }
    void speechResult(final NetworkTask task, final AudioClip audio, final String error) {
        serial(new Runnable() { public void run() {
            if (job != task) return; job = null;
            if (error != null) {
                status = "The text reply is ready, but speech generation failed."; renderHome(false);
                message("Text to speech", error + "\nYour text reply is still available. Choose an available TTS model in Settings > Menu > Voice models.", home);
            } else {
                lastSpeech = audio; lastSpeechText = task.question; lastSpeechModel = config.ttsModel;
                status = "Playing the first part of the reply."; renderHome(); playClip(audio);
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
                    message("Audio playback", error, current == null || current instanceof Alert ? home : current);
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
            status = "Request canceled."; renderHome(false);
            if (old.operation == NetworkTask.VOICE && voiceScreen != null) display.setCurrent(voiceScreen); else display.setCurrent(home);
        } else if (command == clear && job == null) {
            confirmation = new Alert("New chat?", "Clear this session's questions and replies? Your API key will be kept.", null, null);
            confirmation.addCommand(yes); confirmation.addCommand(back); confirmation.setCommandListener(this);
            display.setCurrent(confirmation);
        } else if (from == confirmation) {
            if (command == yes) { stopAudio(); conversation.clear(); draft = ""; status = ""; lastSpeech = null; } renderHome();
        } else if (command == save && from == settingsForm) {
            String key = keyField.getString().trim(), model = modelField.getString().trim(), ttsModel = ttsField.getString().trim();
            if ((key.length() > 0 && !Config.validKey(key)) || !Config.validModel(model) || !Config.validModel(ttsModel)) {
                message("Check settings", "The API key must not contain spaces or a web address. "
                    + "Model names must start with gemini-. Use the values from Google AI Studio.", settingsForm); return;
            }
            config.key = key; config.model = model; config.remember = rememberField.isSelected(0);
            config.ttsModel = ttsModel; config.voiceReplies = voiceReplyField.isSelected(0);
            store.save(config); status = store.failed ? "Could not save settings to storage. They will work for this session."
                : (key.length() == 0 ? "API key removed." : "Settings saved. Select Write to start.");
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
