package cepai;

import java.io.IOException;
import java.util.*;

public final class VoiceCodecTest {
    static int count;
    static void check(boolean value, String label) { count++; if (!value) throw new AssertionError(label); }
    static byte[] pcmWav(int rate, int seconds) {
        byte[] wav = AudioClip.header(rate * seconds * 2, rate);
        for (int i=44;i<wav.length;i+=2) { short sample=(short)((i*17)%20000-10000); wav[i]=(byte)sample;wav[i+1]=(byte)(sample>>8); }
        return wav;
    }
    static String response(String mime, byte[] bytes) {
        return "{\"candidates\":[{\"finishReason\":\"STOP\",\"content\":{\"parts\":[{\"inlineData\":{\"mimeType\":"
            + Json.quote(mime) + ",\"data\":" + Json.quote(Base64.encode(bytes)) + "}}]}}]}";
    }
    public static void main(String[] args) throws Exception {
        String[] plain={"","f","fo","foo","foob","fooba","foobar"};
        String[] expected={"","Zg==","Zm8=","Zm9v","Zm9vYg==","Zm9vYmE=","Zm9vYmFy"};
        for(int i=0;i<plain.length;i++) {
            check(Base64.encode(plain[i].getBytes("UTF-8")).equals(expected[i]),"base64 RFC encode "+i);
            check(new String(Base64.decode(expected[i],100),"UTF-8").equals(plain[i]),"base64 RFC decode "+i);
        }
        Random random=new Random(7);
        for(int size=1;size<500;size+=37) {
            byte[] binary=new byte[size];random.nextBytes(binary);
            check(Arrays.equals(binary,Base64.decode(Base64.encode(binary),size)),"binary base64 roundtrip "+size);
        }
        String[] bad={"A","====","AA=A","AA?=","A===","Zg==\n"};
        for(int i=0;i<bad.length;i++){boolean rejected=false;try{Base64.decode(bad[i],100);}catch(IOException ok){rejected=true;}check(rejected,"invalid base64 "+i);}
        boolean rejected=false;try{Base64.decode("Zm9v",2);}catch(IOException ok){rejected=true;}check(rejected,"base64 size limit");
        byte[] wav=pcmWav(8000,2);AudioClip clip=AudioClip.fromWav(wav,20000);
        check(clip.durationMillis==2000&&Arrays.equals(clip.wav,wav),"8 kHz PCM is preserved");
        AudioClip reduced=AudioClip.fromWav(pcmWav(24000,2),20000);
        check(reduced.durationMillis==2000&&reduced.wav.length==32044,"24 kHz resampled to 8 kHz");
        byte[] stereo=AudioClip.header(16000*4,16000);
        stereo[22]=2;stereo[28]=0;stereo[29]=(byte)250;stereo[30]=0;stereo[31]=0;stereo[32]=4;
        reduced=AudioClip.fromWav(stereo,20000);
        check(reduced.wav.length==16044&&reduced.durationMillis==1000,"stereo PCM downmixed");
        byte[] eight=AudioClip.header(8000,8000);eight[28]=64;eight[29]=31;eight[32]=1;eight[34]=8;
        Arrays.fill(eight,44,eight.length,(byte)128);
        reduced=AudioClip.fromWav(eight,20000);
        check(reduced.wav.length==16044&&reduced.wav[44]==0&&reduced.wav[45]==0,"unsigned 8-bit PCM conversion");
        byte[][] invalid={new byte[12],Arrays.copyOf(wav,wav.length-1),pcmWav(8000,21)};
        for(int i=0;i<invalid.length;i++){rejected=false;try{AudioClip.fromWav(invalid[i],20000);}catch(IOException ok){rejected=true;}check(rejected,"WAV format/length limit "+i);}
        byte[] compressed=wav.clone();compressed[20]=17;
        rejected=false;try{AudioClip.fromWav(compressed,20000);}catch(IOException ok){rejected=true;}check(rejected,"ADPCM rejected clearly");
        rejected=false;try{AudioClip.fromPcm(new byte[3],24000,30000);}catch(IOException ok){rejected=true;}check(rejected,"truncated PCM rejected");
        AudioClip output=Speech.audio(response("audio/wav",pcmWav(24000,2)));
        check(output.wav.length==32044,"TTS WAV decoded and normalized");
        output=Speech.audio(response("audio/L16;codec=pcm;rate=24000",new byte[96000]));
        check(output.wav.length==32044,"legacy Google PCM wrapped exactly once");
        rejected=false;try{Speech.audio(response("audio/mp3",new byte[1000]));}catch(IOException ok){rejected=true;}check(rejected,"unsupported TTS mime rejected");
        rejected=false;try{Speech.audio(response("audio/wav",wav).replace("STOP","MAX_TOKENS"));}catch(IOException ok){rejected=true;}check(rejected,"partial audio never played");
        Hashtable tts=Json.object(Json.parse(Speech.request("Merhaba dünya!",Config.DEFAULT_TTS)));
        Hashtable generation=Json.object(tts.get("generationConfig"));
        check("AUDIO".equals(Json.array(generation.get("responseModalities")).elementAt(0)),"TTS requests audio");
        check("Kore".equals(Json.object(Json.object(generation.get("speechConfig")).get("voiceConfig")).get("voice")),"3.8 voice schema");
        check(Speech.request("Merhaba","gemini-3.1-flash-tts-preview").contains("prebuiltVoiceConfig"),"legacy model voice schema");
        check(Speech.spokenText(new String(new char[1000])).length()<=180,"read-aloud text bounded");
        Conversation history=new Conversation();history.add("Önceki soru","Önceki yanıt");
        Hashtable request=Json.object(Json.parse(history.requestVoice(clip),512000));
        Vector turns=Json.array(request.get("contents"));
        check(turns.size()==3,"voice retains previous text context");
        Hashtable inline=Json.object(Json.object(Json.array(Json.object(turns.elementAt(2)).get("parts")).elementAt(1)).get("inlineData"));
        check("audio/wav".equals(inline.get("mimeType")),"recording MIME is supported WAV");
        check(Arrays.equals(clip.wav,Base64.decode(Json.text(inline.get("data")),clip.wav.length)),"voice payload contains exact normalized audio");
        check(Json.object(request.get("generationConfig")).containsKey("responseJsonSchema"),"transcript and answer schema requested");
        String inner="{\"heard\":\"Nasılsın?\",\"answer\":\"İyiyim.\"}";
        String json="{\"candidates\":[{\"content\":{\"parts\":[{\"text\":"+Json.quote(inner)+"}]},\"finishReason\":\"STOP\"}]}";
        String[] result=Speech.voiceAnswer(json);
        check(result[0].equals("Nasılsın?")&&result[1].equals("İyiyim."),"transcript and answer separated");
        rejected=false;try{Speech.voiceAnswer(json.replace("Nasılsın?",""));}catch(IOException ok){rejected=true;}check(rejected,"empty speech not invented");
        System.out.println("PASS: "+count+" voice codec and Google schema checks");
    }
}
