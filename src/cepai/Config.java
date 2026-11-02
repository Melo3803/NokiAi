package cepai;

import java.io.IOException;
import java.util.Hashtable;

public final class Config {
    public static final String DEFAULT_MODEL = "gemini-3.5-flash-lite";
    public static final String DEFAULT_TTS = "gemini-3.8-flash-lite-tts";
    public String key = "", model = DEFAULT_MODEL;
    public String ttsModel = DEFAULT_TTS;
    public boolean remember = true;
    public boolean voiceReplies = true;

    public static boolean validKey(String key) {
        if (key == null || key.length() < 20 || key.length() > 200) return false;
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (!((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                || (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '.'
                || c == '=' || c == '+' || c == '/')) return false;
        }
        return true;
    }
    public static boolean validModel(String model) {
        if (model == null || !model.startsWith("gemini-") || model.length() > 80) return false;
        for (int i = 0; i < model.length(); i++) {
            char c = model.charAt(i);
            if (!((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-' || c == '.')) return false;
        }
        return true;
    }
    /** QR contents are data only: no URL is ever opened or downloaded. */
    public static Config fromQr(String payload) throws IOException {
        if (payload == null || payload.length() > 2048) throw new IOException("QR içeriği çok uzun.");
        String text = payload.trim(); Config result = new Config();
        if (text.startsWith("{")) {
            Hashtable map = Json.object(Json.parse(text));
            result.key = Json.text(map.get("apiKey")).trim();
            String model = Json.text(map.get("model")).trim();
            if (model.length() > 0) {
                if (!validModel(model)) throw new IOException("QR model adı geçersiz.");
                result.model = model;
            }
        } else {
            if (text.startsWith("gemini:")) text = text.substring(7).trim();
            result.key = text;
        }
        if (!validKey(result.key)) throw new IOException("QR içinde geçerli bir API anahtarı bulunamadı.");
        return result;
    }
}
