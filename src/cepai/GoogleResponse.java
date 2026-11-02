package cepai;

import java.io.IOException;
import java.util.Hashtable;
import java.util.Vector;

public final class GoogleResponse {
    public static String answer(String json) throws IOException {
        Hashtable root = Json.object(Json.parse(json));
        Vector candidates = Json.array(root.get("candidates"));
        if (candidates.size() == 0) throw new IOException("Google returned no reply. Try rephrasing your question.");
        Hashtable candidate = Json.object(candidates.elementAt(0));
        String reason = Json.text(candidate.get("finishReason"));
        if (reason.equals("SAFETY") || reason.equals("PROHIBITED_CONTENT") || reason.equals("RECITATION"))
            throw new IOException("Google did not answer this request. Try rephrasing your question.");
        Vector parts = Json.array(Json.object(candidate.get("content")).get("parts"));
        StringBuffer answer = new StringBuffer();
        for (int i = 0; i < parts.size(); i++) {
            Hashtable part = Json.object(parts.elementAt(i));
            if (Boolean.TRUE.equals(part.get("thought"))) continue;
            String text = Json.text(part.get("text"));
            if (text.length() > 0) answer.append(text);
        }
        if (answer.length() == 0) throw new IOException("The reply contains no displayable text.");
        String text = answer.toString();
        if (text.length() > 10000) text = text.substring(0, 10000) + "\n[Reply shortened to fit the screen.]";
        if (reason.equals("MAX_TOKENS")) text += "\n[Reply limit reached; you can ask to continue.]";
        return text;
    }
    public static String error(int status) {
        if (status == 400) return "Invalid request or API key (400). Check the key and model name.";
        if (status == 401 || status == 403) return "API key was rejected (" + status + "). Check the key, API permissions and regional availability.";
        if (status == 404) return "Model not found (404). Select a model available to your account in Settings.";
        if (status == 429) return "Quota or rate limit reached (429). Wait a little and check your quota in Google AI Studio.";
        if (status >= 500) return "Google is temporarily unavailable (" + status + "). Please try again later.";
        return "Google connection failed (HTTP " + status + ").";
    }
}
