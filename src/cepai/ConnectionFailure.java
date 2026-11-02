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
                advice = "Sertifika tarihi doğrulanamadı. Önce telefonun tarih ve saatini kontrol et. "
                    + "Tarih doğruysa sertifika deposunun güncellenmesi gerekebilir.";
            else if (reason == CertificateException.UNRECOGNIZED_ISSUER || reason == CertificateException.BROKEN_CHAIN)
                advice = "Google sertifikasının yayıncısı/zinciri tanınmadı. Telefonun kök sertifika veya TLS desteği eski olabilir.";
            else advice = "Google sertifikası doğrulanamadı (neden " + reason + "). Telefon tarihi, sertifika deposu ve TLS desteğini kontrol et.";
        } else if (failure instanceof ConnectionNotFoundException) {
            advice = "Bağlantı açılamadı. Wi-Fi/erişim noktası ve DNS ayarlarını kontrol et; HTTPS desteği de eksik olabilir.";
        } else if (failure instanceof InterruptedIOException) {
            advice = "Bağlantı zaman aşımına uğradı. Wi-Fi ve seçili erişim noktasını kontrol et.";
        } else {
            advice = "Bağlantı tamamlanamadı. Önce Wi-Fi/erişim noktası ve telefon tarihini kontrol et. "
                + "Native hata SSL/TLS ile ilgiliyse telefon içi TLS güncellemesi gerekebilir.";
        }
        String nativeText = redact(failure.getMessage(), key);
        if (nativeText.length() > 180) nativeText = nativeText.substring(0, 180);
        return advice + "\n\nAşama: " + stage + "\nTür: " + failure.getClass().getName()
            + (nativeText.length() == 0 ? "" : "\nCihaz: " + nativeText);
    }
    private static String redact(String text, String key) {
        if (text == null) return "";
        if (key != null && key.length() > 0) {
            StringBuffer safe = new StringBuffer(); int from = 0, at;
            while ((at = text.indexOf(key, from)) >= 0) {
                safe.append(text.substring(from, at)); safe.append("[anahtar gizlendi]"); from = at + key.length();
            }
            safe.append(text.substring(from)); text = safe.toString();
        }
        StringBuffer clean = new StringBuffer();
        for (int i = 0; i < text.length(); i++) { char c = text.charAt(i); clean.append(c < 32 ? ' ' : c); }
        return clean.toString();
    }
}
