package cepai;

import java.io.IOException;

/** Validated, bounded 8 kHz mono PCM WAV: accepted by Google and old phone players. */
public final class AudioClip {
    public static final int MAX_INPUT = 768 * 1024;
    public final byte[] wav;
    public final int durationMillis;
    private AudioClip(byte[] bytes, int duration) { wav = bytes; durationMillis = duration; }
    public static AudioClip fromWav(byte[] input, int maxMillis) throws IOException {
        if (input == null || input.length < 44 || input.length > 1536000
                || !tag(input, 0, "RIFF") || !tag(input, 8, "WAVE"))
            throw new IOException("A PCM WAV recording is required. Set the phone's Recorder quality to High.");
        int format = 0, channels = 0, rate = 0, bits = 0, align = 0, dataAt = -1, size = 0;
        int declared = little32(input, 4);
        if (declared < 36 || declared > input.length - 8) throw new IOException("Incomplete WAV recording.");
        int end = declared + 8;
        for (int at = 12; at <= end - 8;) {
            int length = little32(input, at + 4), body = at + 8;
            if (length < 0 || length > end - body) throw new IOException("Incomplete WAV chunk.");
            if (tag(input, at, "fmt ") && length >= 16) {
                format = little16(input, body); channels = little16(input, body + 2);
                rate = little32(input, body + 4); align = little16(input, body + 12); bits = little16(input, body + 14);
            } else if (tag(input, at, "data") && dataAt == -1) { dataAt = body; size = length; }
            at = body + length + (length & 1);
        }
        if (format != 1 || (channels != 1 && channels != 2) || (bits != 8 && bits != 16)
                || rate < 8000 || rate > 48000 || align != channels * bits / 8 || dataAt < 0 || size < align)
            throw new IOException("Unsupported WAV format. Use 8/16-bit PCM audio.");
        int frames = size / align;
        long duration = (long) frames * 1000 / rate;
        if (duration > maxMillis) throw new IOException("Audio is too long. Choose a recording of up to " + (maxMillis / 1000) + " seconds.");
        if (duration < 200) throw new IOException("Recording is too short. Speak a little longer and try again.");
        int outputFrames = (int) ((long) frames * 8000 / rate);
        byte[] output = header(outputFrames * 2, 8000);
        for (int i = 0; i < outputFrames; i++) {
            int first = (int) ((long) i * rate / 8000), last = (int) ((long) (i + 1) * rate / 8000);
            if (last > frames) last = frames;
            int sum = 0, count = 0;
            for (int f = first; f < last; f++) for (int c = 0; c < channels; c++) {
                int p = dataAt + f * align + c * bits / 8;
                sum += bits == 16 ? (short) little16(input, p) : ((input[p] & 255) - 128) * 256;
                count++;
            }
            int sample = count == 0 ? 0 : sum / count;
            output[44 + i * 2] = (byte) sample; output[45 + i * 2] = (byte) (sample >> 8);
        }
        return new AudioClip(output, (int) duration);
    }
    public static AudioClip fromPcm(byte[] pcm, int rate, int maxMillis) throws IOException {
        if (pcm.length == 0 || (pcm.length & 1) != 0 || pcm.length > 1535956) throw new IOException("PCM audio is incomplete or too large.");
        byte[] wav = header(pcm.length, rate); System.arraycopy(pcm, 0, wav, 44, pcm.length);
        return fromWav(wav, maxMillis);
    }
    public static byte[] header(int bytes, int rate) {
        byte[] data = new byte[44 + bytes];
        putTag(data, 0, "RIFF"); put32(data, 4, 36 + bytes); putTag(data, 8, "WAVE");
        putTag(data, 12, "fmt "); put32(data, 16, 16); put16(data, 20, 1); put16(data, 22, 1);
        put32(data, 24, rate); put32(data, 28, rate * 2); put16(data, 32, 2); put16(data, 34, 16);
        putTag(data, 36, "data"); put32(data, 40, bytes); return data;
    }
    private static boolean tag(byte[] data, int at, String text) {
        if (at + 4 > data.length) return false;
        for (int i = 0; i < 4; i++) if ((data[at + i] & 255) != text.charAt(i)) return false;
        return true;
    }
    private static int little16(byte[] data, int at) { return (data[at] & 255) | ((data[at + 1] & 255) << 8); }
    private static int little32(byte[] data, int at) {
        return little16(data, at) | (little16(data, at + 2) << 16);
    }
    private static void put16(byte[] data, int at, int value) { data[at] = (byte) value; data[at + 1] = (byte) (value >> 8); }
    private static void put32(byte[] data, int at, int value) { put16(data, at, value); put16(data, at + 2, value >> 16); }
    private static void putTag(byte[] data, int at, String value) { for (int i = 0; i < 4; i++) data[at + i] = (byte) value.charAt(i); }
}
