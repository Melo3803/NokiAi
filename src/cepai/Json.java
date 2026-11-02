package cepai;

import java.io.IOException;
import java.util.Hashtable;
import java.util.Vector;

/** Bounded JSON reader for CLDC; no Java SE or reflection dependencies. */
public final class Json {
    private final String source;
    private int at;
    private Json(String text) { source = text; }
    public static Object parse(String text) throws IOException {
        return parse(text, 65536);
    }
    public static Object parse(String text, int limit) throws IOException {
        if (text == null || limit < 1 || limit > 2 * 1024 * 1024 || text.length() > limit) throw new IOException("JSON boyutu uygun değil.");
        Json p = new Json(text);
        Object value = p.value(0); p.space();
        if (p.at != text.length()) throw new IOException("Geçersiz JSON sonu.");
        return value;
    }
    private void space() {
        while (at < source.length() && (source.charAt(at) == ' ' || source.charAt(at) == '\t'
            || source.charAt(at) == '\r' || source.charAt(at) == '\n')) at++;
    }
    private Object value(int depth) throws IOException {
        if (depth > 24) throw new IOException("JSON çok derin.");
        space();
        if (at >= source.length()) throw new IOException("Eksik JSON.");
        char c = source.charAt(at);
        if (c == '"') return string();
        if (c == '{') {
            at++; Hashtable map = new Hashtable(); space();
            if (take('}')) return map;
            do {
                space();
                if (at >= source.length() || source.charAt(at) != '"') throw new IOException("JSON alanı eksik.");
                String key = string(); space(); require(':');
                Object item = value(depth + 1);
                if (item != null) map.put(key, item);
                space(); if (take('}')) return map; require(',');
            } while (true);
        }
        if (c == '[') {
            at++; Vector list = new Vector(); space();
            if (take(']')) return list;
            do {
                list.addElement(value(depth + 1)); space();
                if (take(']')) return list; require(',');
            } while (true);
        }
        if (literal("true")) return Boolean.TRUE;
        if (literal("false")) return Boolean.FALSE;
        if (literal("null")) return null;
        int start = at;
        if (take('-') && at >= source.length()) throw new IOException("Geçersiz sayı.");
        if (!take('0')) {
            if (at >= source.length() || source.charAt(at) < '1' || source.charAt(at) > '9') throw new IOException("Geçersiz JSON.");
            digits();
        }
        if (take('.')) { int before = at; digits(); if (at == before) throw new IOException("Geçersiz sayı."); }
        if (take('e') || take('E')) {
            if (!take('+')) take('-'); int before = at; digits();
            if (at == before) throw new IOException("Geçersiz üs.");
        }
        return source.substring(start, at);
    }
    private void digits() { while (at < source.length() && source.charAt(at) >= '0' && source.charAt(at) <= '9') at++; }
    private boolean literal(String text) {
        if (!source.startsWith(text, at)) return false;
        at += text.length(); return true;
    }
    private boolean take(char c) {
        if (at < source.length() && source.charAt(at) == c) { at++; return true; } return false;
    }
    private void require(char c) throws IOException { if (!take(c)) throw new IOException("JSON ayırıcı eksik."); }
    private String string() throws IOException {
        require('"'); StringBuffer out = new StringBuffer();
        while (at < source.length()) {
            char c = source.charAt(at++);
            if (c == '"') return out.toString();
            if (c < 32) throw new IOException("JSON kontrol karakteri.");
            if (c == '\\') {
                if (at >= source.length()) throw new IOException("JSON kaçışı eksik.");
                c = source.charAt(at++);
                if (c == 'n') c = '\n'; else if (c == 'r') c = '\r'; else if (c == 't') c = '\t';
                else if (c == 'b') c = '\b'; else if (c == 'f') c = '\f';
                else if (c == 'u') {
                    if (at + 4 > source.length()) throw new IOException("Unicode kaçışı eksik.");
                    int code = 0;
                    for (int i = 0; i < 4; i++) {
                        char d = source.charAt(at++);
                        int n = "0123456789abcdef".indexOf(Character.toLowerCase(d));
                        if (n < 0) throw new IOException("Geçersiz Unicode kaçışı.");
                        code = code * 16 + n;
                    }
                    c = (char) code;
                } else if (c != '"' && c != '\\' && c != '/') throw new IOException("Geçersiz JSON kaçışı.");
            }
            out.append(c);
        }
        throw new IOException("JSON metni tamamlanmadı.");
    }
    public static String quote(String value) {
        StringBuffer out = new StringBuffer("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"' || c == '\\') { out.append('\\'); out.append(c); }
            else if (c == '\n') out.append("\\n"); else if (c == '\r') out.append("\\r");
            else if (c == '\t') out.append("\\t");
            else if (c < 32) { out.append("\\u00"); out.append("0123456789abcdef".charAt(c / 16)); out.append("0123456789abcdef".charAt(c % 16)); }
            else out.append(c);
        }
        out.append('"'); return out.toString();
    }
    public static Hashtable object(Object value) { return value instanceof Hashtable ? (Hashtable) value : new Hashtable(); }
    public static Vector array(Object value) { return value instanceof Vector ? (Vector) value : new Vector(); }
    public static String text(Object value) { return value instanceof String ? (String) value : ""; }
}
