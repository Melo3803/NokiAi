import java.io.*;
import java.net.*;
import java.security.cert.Certificate;
import javax.net.ssl.HttpsURLConnection;

/** Test-only HTTPS endpoint. No traffic or credentials leave the test JVM. */
public final class MockHttps extends HttpsURLConnection {
    static volatile String nextBody = "{}";
    static volatile int nextStatus = 200;
    static volatile boolean blockNext;
    static volatile MockHttps last;
    final ByteArrayOutputStream sent = new ByteArrayOutputStream();
    final java.util.Map<String,String> headers = new java.util.HashMap<String,String>();
    final int status;
    final String body;
    final boolean blocked;
    volatile boolean closed;
    MockHttps(URL url) { super(url); status=nextStatus; body=nextBody; blocked=blockNext; last=this; }
    public static void install() {
        URL.setURLStreamHandlerFactory(new URLStreamHandlerFactory() {
            public URLStreamHandler createURLStreamHandler(String protocol) {
                if (!protocol.equals("https")) return null;
                return new URLStreamHandler() { protected URLConnection openConnection(URL url) { return new MockHttps(url); } };
            }
        });
    }
    public int getResponseCode() throws IOException {
        while (blocked && !closed) try { Thread.sleep(20); } catch (InterruptedException e) { throw new IOException("interrupted"); }
        if (closed) throw new IOException("closed");
        return status;
    }
    public InputStream getInputStream() throws IOException { return new ByteArrayInputStream(body.getBytes("UTF-8")); }
    public OutputStream getOutputStream() { return sent; }
    public String getContentType() { return "application/json"; }
    public void setRequestProperty(String name, String value) { headers.put(name,value); }
    public String getRequestProperty(String name) { return headers.get(name); }
    public int getContentLength() { return body.length(); }
    public void connect() { connected=true; }
    public void disconnect() { closed=true; }
    public boolean usingProxy() { return false; }
    public String getCipherSuite() { return "TEST_ONLY"; }
    public Certificate[] getLocalCertificates() { return null; }
    public Certificate[] getServerCertificates() { return new Certificate[0]; }
}
