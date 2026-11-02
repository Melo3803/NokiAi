package cepai;

import java.io.IOException;
import java.util.Hashtable;
import java.util.Vector;

public final class Speech {
    public static final int MAX_JSON = 2 * 1024 * 1024;
    public static String spokenText(String answer) {
        String text = answer.trim();
        if (text.length() > 180) { int end = text.lastIndexOf(' ', 180); text = text.substring(0, end > 100 ? end : 180); }
        return text;
    }
    public static String request(String answer, String model) {
        StringBuffer out = new StringBuffer("{\"contents\":[{\"role\":\"user\",\"parts\":[{\"text\":");
        out.append(Json.quote(spokenText(answer)));
        if (model.startsWith("gemini-3.8-")) out.append(",\"speechMetadata\":{\"style\":\"Read naturally and clearly in the language of the text.\"}");
        out.append("}]}],\"generationConfig\":{\"responseModalities\":[\"AUDIO\"],\"speechConfig\":{\"voiceConfig\":");
        out.append(model.startsWith("gemini-3.8-") ? "{\"voice\":\"Kore\"}" : "{\"prebuiltVoiceConfig\":{\"voiceName\":\"Kore\"}}");
        out.append("}}}"); return out.toString();
    }
    public static String[] voiceAnswer(String json) throws IOException {
        Hashtable object = Json.object(Json.parse(GoogleResponse.answer(json)));
        String heard = Json.text(object.get("heard")).trim(), answer = Json.text(object.get("answer")).trim();
        if (heard.length() == 0 || answer.length() == 0) throw new IOException("Speech was not understood. Try a shorter, clearer recording.");
        if (heard.length() > 2048 || answer.length() > 10000) throw new IOException("Invalid voice reply size.");
        return new String[] { heard, answer };
    }
    public static AudioClip audio(String json) throws IOException {
        Hashtable root = Json.object(Json.parse(json, MAX_JSON)); Vector candidates = Json.array(root.get("candidates"));
        if (candidates.size() == 0) throw new IOException("Google returned no audio. Check the TTS model and quota.");
        Hashtable candidate = Json.object(candidates.elementAt(0));
        if ("MAX_TOKENS".equals(candidate.get("finishReason"))) throw new IOException("Audio reply was incomplete. Try reading a shorter reply aloud.");
        Vector parts = Json.array(Json.object(candidate.get("content")).get("parts"));
        for (int i = 0; i < parts.size(); i++) {
            Hashtable part = Json.object(parts.elementAt(i));
            if (Boolean.TRUE.equals(part.get("thought"))) continue;
            Hashtable inline = Json.object(part.get("inlineData"));
            String mime = Json.text(inline.get("mimeType")).toLowerCase();
            String base64 = Json.text(inline.get("data")); if (base64.length() == 0) continue;
            byte[] data = Base64.decode(base64, 1536000);
            if (mime.startsWith("audio/wav") || mime.startsWith("audio/x-wav")) return AudioClip.fromWav(data, 30000);
            if (mime.startsWith("audio/l16") || mime.startsWith("audio/pcm")) {
                int rate = 24000, at = mime.indexOf("rate=");
                if (at >= 0) {
                    int end = mime.indexOf(';', at); String value = mime.substring(at + 5, end < 0 ? mime.length() : end).trim();
                    try { rate = Integer.parseInt(value); } catch (NumberFormatException invalid) { throw new IOException("Invalid audio sample rate."); }
                }
                return AudioClip.fromPcm(data, rate, 30000);
            }
            throw new IOException("Google returned an unsupported audio format.");
        }
        throw new IOException("No audio found in the reply. Check the TTS model and API access.");
    }
}
