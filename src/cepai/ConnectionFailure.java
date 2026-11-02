package cepai;

import java.io.IOException;
import java.io.InterruptedIOException;
import javax.microedition.io.ConnectionNotFoundException;
import javax.microedition.pki.CertificateException;

public final class ConnectionFailure {
    public static String describe(IOException failure, String stage, String key) {
        String advice;
        if (failure instanceof CertificateException) {
            byte reason = ((CertificateException) failure).getReason();
            if (reason == CertificateException.EXPIRED || reason == CertificateException.NOT_YET_VALID
                    || reason == CertificateException.ROOT_CA_EXPIRED)
                advice = "Could not verify the certificate date. Check the phone's date and time first. "
                    + "If they are correct, the certificate store may need an update.";
            else if (reason == CertificateException.UNRECOGNIZED_ISSUER || reason == CertificateException.BROKEN_CHAIN)
                advice = "Google's certificate issuer or chain is not trusted. The phone's root certificates or TLS support may be outdated.";
            else advice = "Could not verify Google's certificate (reason " + reason + "). Check the phone's date, certificate store and TLS support.";
        } else if (failure instanceof ConnectionNotFoundException) {
            advice = "Could not open the connection. Check Wi-Fi, access point and DNS settings. HTTPS support may also be missing.";
        } else if (failure instanceof InterruptedIOException) {
            advice = "Connection timed out. Check Wi-Fi and the selected access point.";
        } else {
            advice = "Connection failed. First check Wi-Fi, the access point and the phone's date. "
                + "If the native error mentions SSL/TLS, the phone may need a TLS update.";
        }
        String nativeText = redact(failure.getMessage(), key);
        if (nativeText.length() > 180) nativeText = nativeText.substring(0, 180);
        return advice + "\n\nStage: " + stage + "\nType: " + failure.getClass().getName()
            + (nativeText.length() == 0 ? "" : "\nDevice: " + nativeText);
    }
    private static String redact(String text, String key) {
        if (text == null) return "";
        if (key != null && key.length() > 0) {
            StringBuffer safe = new StringBuffer(); int from = 0, at;
            while ((at = text.indexOf(key, from)) >= 0) {
                safe.append(text.substring(from, at)); safe.append("[key hidden]"); from = at + key.length();
            }
            safe.append(text.substring(from)); text = safe.toString();
        }
        StringBuffer clean = new StringBuffer();
        for (int i = 0; i < text.length(); i++) { char c = text.charAt(i); clean.append(c < 32 ? ' ' : c); }
        return clean.toString();
    }
}
