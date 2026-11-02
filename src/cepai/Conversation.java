package cepai;

import java.util.Vector;

/** Holds completed pairs only; failed requests never poison the next request. */
public final class Conversation {
    private final Vector users = new Vector(), answers = new Vector();
    public synchronized void add(String user, String answer) {
        users.addElement(user); answers.addElement(answer);
        while (users.size() > 5 || (characters() > 14000 && users.size() > 1)) {
            users.removeElementAt(0); answers.removeElementAt(0);
        }
    }
    public synchronized void clear() { users.removeAllElements(); answers.removeAllElements(); }
    public synchronized int size() { return users.size(); }
    public synchronized String user(int index) { return (String) users.elementAt(index); }
    public synchronized String answer(int index) { return (String) answers.elementAt(index); }
    private int characters() {
        int size = 0;
        for (int i = 0; i < users.size(); i++) size += user(i).length() + answer(i).length();
        return size;
    }
    public synchronized String request(String question) {
        return request(question, null);
    }
    public synchronized String requestVoice(AudioClip clip) { return request("", clip); }
    private String request(String question, AudioClip clip) {
        StringBuffer json = new StringBuffer("{\"systemInstruction\":{\"parts\":[{\"text\":");
        json.append(Json.quote("You are noki Ai. Your replies are read on a small Nokia E63 screen. "
            + "Reply in the user's language, defaulting to English if unclear. Be concise, clear and helpful. "
            + "Avoid long tables and complex Markdown unless requested."));
        json.append("}]},\"contents\":[");
        for (int i = 0; i < users.size(); i++) {
            appendTurn(json, "user", user(i)); json.append(',');
            appendTurn(json, "model", answer(i)); json.append(',');
        }
        if (clip == null) appendTurn(json, "user", question);
        else {
            json.append("{\"role\":\"user\",\"parts\":[{\"text\":");
            json.append(Json.quote("Listen to this voice question. Return only JSON: put the actual transcript in heard and the reply in answer. "
                + "Keep the reply brief, about 1-3 sentences, in the speaker's language. If speech is unclear, leave heard and answer empty; never invent speech."));
            json.append("},{\"inlineData\":{\"mimeType\":\"audio/wav\",\"data\":");
            json.append(Json.quote(Base64.encode(clip.wav))); json.append("}}]}");
        }
        json.append("],\"generationConfig\":{\"maxOutputTokens\":1536");
        if (clip != null) json.append(",\"responseMimeType\":\"application/json\",\"responseJsonSchema\":{\"type\":\"object\",\"properties\":{\"heard\":{\"type\":\"string\"},\"answer\":{\"type\":\"string\"}},\"required\":[\"heard\",\"answer\"]}");
        json.append("}}");
        return json.toString();
    }
    private static void appendTurn(StringBuffer out, String role, String text) {
        out.append("{\"role\":\""); out.append(role); out.append("\",\"parts\":[{\"text\":");
        out.append(Json.quote(text)); out.append("}]}");
    }
}
