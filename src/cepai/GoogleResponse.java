package cepai;

import java.io.IOException;
import java.util.Hashtable;
import java.util.Vector;

public final class GoogleResponse {
    public static String answer(String json) throws IOException {
        Hashtable root = Json.object(Json.parse(json));
        Vector candidates = Json.array(root.get("candidates"));
        if (candidates.size() == 0) throw new IOException("Google yanıt üretmedi. Sorunu farklı yazmayı dene.");
        Hashtable candidate = Json.object(candidates.elementAt(0));
        String reason = Json.text(candidate.get("finishReason"));
        if (reason.equals("SAFETY") || reason.equals("PROHIBITED_CONTENT") || reason.equals("RECITATION"))
            throw new IOException("Google bu isteğe yanıt vermedi. Sorunu farklı yazmayı dene.");
        Vector parts = Json.array(Json.object(candidate.get("content")).get("parts"));
        StringBuffer answer = new StringBuffer();
        for (int i = 0; i < parts.size(); i++) {
            Hashtable part = Json.object(parts.elementAt(i));
            if (Boolean.TRUE.equals(part.get("thought"))) continue;
            String text = Json.text(part.get("text"));
            if (text.length() > 0) answer.append(text);
        }
        if (answer.length() == 0) throw new IOException("Yanıtta gösterilebilecek metin yok.");
        String text = answer.toString();
        if (text.length() > 10000) text = text.substring(0, 10000) + "\n[Yanıt ekrana sığması için kısaltıldı.]";
        if (reason.equals("MAX_TOKENS")) text += "\n[Yanıt sınırına ulaşıldı; devamını isteyebilirsin.]";
        return text;
    }
    public static String error(int status) {
        if (status == 400) return "İstek veya API anahtarı geçersiz (400). Anahtarı ve model adını kontrol et.";
        if (status == 401 || status == 403) return "API anahtarı kabul edilmedi (" + status + "). Anahtarı, API izinlerini ve bölge desteğini kontrol et.";
        if (status == 404) return "Model bulunamadı (404). Ayarlardan hesabında açık olan model adını seç.";
        if (status == 429) return "Kota veya hız sınırı doldu (429). Biraz bekle; Google AI Studio kotanı kontrol et.";
        if (status >= 500) return "Google şu an yanıt veremiyor (" + status + "). Biraz sonra yeniden dene.";
        return "Google bağlantısı başarısız (HTTP " + status + ").";
    }
}
