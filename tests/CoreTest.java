package cepai;

import java.io.IOException;
import java.util.Hashtable;
import java.util.Vector;

public final class CoreTest {
    static int count;
    static final String KEY = "AIza" + "01234567890123456789012345678901234";
    static void check(boolean condition, String name) {
        count++; if (!condition) throw new AssertionError(name);
    }
    public static void main(String[] args) throws Exception {
        String text = "Türkçe: İ ı ş ğ ü ö ç\n\t\"\\\u0001😀";
        check(text.equals(Json.parse(Json.quote(text))), "Unicode and escaping roundtrip");
        Hashtable map = Json.object(Json.parse("{\"text\":\"\\u0130\\u015f\",\"ok\":true,\"no\":false,\"n\":null,\"a\":[1,-2.5e+3,null]}"));
        check("İş".equals(map.get("text")), "Unicode escape decoding");
        check(Boolean.TRUE.equals(map.get("ok")) && Boolean.FALSE.equals(map.get("no")), "Booleans");
        check(Json.array(map.get("a")).size() == 3, "Arrays with null elements");
        String[] invalid = {"", "{", "[", "true false", "[1,]", "{\"x\":}", "\"\\uXX00\"", "\"\\x\"", "01", "-", "1.", "1e", "{\"x\" 1}", "\"\n\""};
        for (int i = 0; i < invalid.length; i++) {
            boolean rejected = false; try { Json.parse(invalid[i]); } catch (IOException expected) { rejected = true; }
            check(rejected, "Invalid JSON " + i);
        }
        String deep = "0"; for (int i = 0; i < 30; i++) deep = "[" + deep + "]";
        boolean rejected = false; try { Json.parse(deep); } catch (IOException expected) { rejected = true; }
        check(rejected, "Depth limit");
        rejected = false; try { Json.parse(new String(new char[65537])); } catch (IOException expected) { rejected = true; }
        check(rejected, "Input size limit");
        check(Config.validKey(KEY), "Plausible key accepted");
        check(!Config.validKey("https://example.com/" + KEY), "URL rejected as key");
        check(!Config.validKey(KEY + "\r\nx: y"), "Header injection rejected");
        check(!Config.validKey("short"), "Short key rejected");
        check(Config.validModel("gemini-3.5-flash-lite"), "Model name accepted");
        check(!Config.validModel("gemini-../foo?key=x"), "Model URL injection rejected");
        check(Config.fromQr(" \n" + KEY + "\n").key.equals(KEY), "Plain key QR");
        check(Config.fromQr("gemini:" + KEY).key.equals(KEY), "Prefixed key QR");
        Config scanned = Config.fromQr("{\"apiKey\":" + Json.quote(KEY) + ",\"model\":\"gemini-3.5-flash-lite\"}");
        check(scanned.key.equals(KEY) && scanned.model.equals(Config.DEFAULT_MODEL), "Configuration QR");
        String[] badQr = {"https://example.com/?key=" + KEY, "WIFI:T:WPA;S:test;;", "{}", "{\"apiKey\":\"abc\"}", "{\"apiKey\":" + Json.quote(KEY) + ",\"model\":\"../bad\"}"};
        for (int i = 0; i < badQr.length; i++) {
            rejected = false; try { Config.fromQr(badQr[i]); } catch (IOException expected) { rejected = true; }
            check(rejected, "Untrusted QR " + i);
        }
        Conversation history = new Conversation();
        for (int i = 0; i < 8; i++) history.add("Soru " + i, "Yanıt " + i);
        check(history.size() == 5 && history.user(0).equals("Soru 3"), "History count bounded");
        Hashtable request = Json.object(Json.parse(history.request("Yeni soru \"Türkçe\"")));
        Vector turns = Json.array(request.get("contents"));
        check(turns.size() == 11, "Five pairs plus current user");
        for (int i = 0; i < turns.size(); i++) check((i % 2 == 0 ? "user" : "model").equals(Json.object(turns.elementAt(i)).get("role")), "Turn role " + i);
        check(Json.array(Json.object(turns.elementAt(10)).get("parts")).size() == 1, "One current user part");
        check(!history.request("test").contains(KEY), "API key never enters request body");
        check(history.size() == 5, "Building failed request never alters history");
        history.add("large", new String(new char[13980]));
        check(history.size() <= 2, "History memory bounded");
        history.clear(); check(history.size() == 0, "New chat clears context");
        String response = "{\"candidates\":[{\"content\":{\"parts\":[{\"thought\":true,\"text\":\"hidden\"},{\"text\":\"Merhaba \"},{\"text\":\"dünya!\"}]},\"finishReason\":\"STOP\"}]}";
        check(GoogleResponse.answer(response).equals("Merhaba dünya!"), "Only visible text parts shown");
        check(GoogleResponse.answer(response.replace("STOP", "MAX_TOKENS")).contains("Reply limit"), "Truncation explained");
        rejected = false; try { GoogleResponse.answer(response.replace("STOP", "SAFETY")); } catch (IOException expected) { rejected = true; }
        check(rejected, "Blocked answer handled");
        rejected = false; try { GoogleResponse.answer("{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}"); } catch (IOException expected) { rejected = true; }
        check(rejected, "Missing candidates handled");
        check(GoogleResponse.error(429).contains("Quota"), "Quota error");
        check(GoogleResponse.error(403).contains("API key"), "Authentication error");
        check(GoogleResponse.error(404).contains("Model"), "Missing model error");
        check(GoogleResponse.error(503).contains("Google"), "Service error");
        System.out.println("PASS: " + count + " core checks");
    }
}
