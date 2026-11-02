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
        json.append(Json.quote("Sen noki Ai'sın. Yanıtların eski bir Nokia E63 ekranında okunuyor. "
            + "Kullanıcının dilinde, varsayılan olarak Türkçe yanıt ver. Kısa, açık ve yararlı ol. "
            + "Kullanıcı istemedikçe uzun tablolar ve karmaşık Markdown kullanma."));
        json.append("}]},\"contents\":[");
        for (int i = 0; i < users.size(); i++) {
            appendTurn(json, "user", user(i)); json.append(',');
            appendTurn(json, "model", answer(i)); json.append(',');
        }
        if (clip == null) appendTurn(json, "user", question);
        else {
            json.append("{\"role\":\"user\",\"parts\":[{\"text\":");
            json.append(Json.quote("Bu sesli soruyu dinle. Yalnızca JSON üret: heard alanına duyduğun konuşmayı, answer alanına sorunun yanıtını yaz. "
                + "Yanıtı kısa tut, yaklaşık 1-3 cümle. Konuşma anlaşılmıyorsa heard ve answer boş olsun; duyulmayan sözleri uydurma."));
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
