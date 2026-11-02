import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import javax.imageio.ImageIO;
import javax.microedition.lcdui.*;
import javax.microedition.midlet.MIDlet;
import org.microemu.*;
import org.microemu.app.Headless;
import org.microemu.device.impl.Rectangle;
import org.microemu.device.j2se.J2SEDeviceDisplay;
import org.microemu.device.j2se.J2SEMutableImage;

public final class EmulatorTest {
    static final String KEY="AIza"+"01234567890123456789012345678901234";
    static int count;
    static MIDlet app;
    static DisplayAccess da;
    static void check(boolean ok,String name){count++;if(!ok)throw new AssertionError(name);}
    static Field find(Object object,String name)throws Exception{
        Class type=object.getClass();
        while(type!=null){try{Field f=type.getDeclaredField(name);f.setAccessible(true);return f;}catch(NoSuchFieldException missing){type=type.getSuperclass();}}
        throw new NoSuchFieldException(name);
    }
    static Object field(Object o,String n)throws Exception{return find(o,n).get(o);}
    static void set(Object o,String n,Object v)throws Exception{find(o,n).set(o,v);}
    static Object call(Object o,String n,Class[] types,Object[] args)throws Exception{
        Class type=o.getClass();
        while(type!=null){try{Method m=type.getDeclaredMethod(n,types);m.setAccessible(true);return m.invoke(o,args);}catch(NoSuchMethodException missing){type=type.getSuperclass();}}
        throw new NoSuchMethodException(n);
    }
    static Object call(Object o,String n)throws Exception{return call(o,n,new Class[0],new Object[0]);}
    static void settle()throws Exception{Thread.sleep(100);}
    static void command(String name,Displayable from)throws Exception{
        call(app,"commandAction",new Class[]{Command.class,Displayable.class},new Object[]{field(app,name),from});settle();
    }
    static void dismiss()throws Exception{
        da.commandAction(new Command("OK",Command.OK,0),da.getCurrent());settle();
    }
    static void awaitJob()throws Exception{
        long deadline=System.currentTimeMillis()+5000;
        while(field(app,"job")!=null&&System.currentTimeMillis()<deadline)Thread.sleep(20);
        settle();check(field(app,"job")==null,"request finishes within test deadline");
    }
    static void capture(File target)throws Exception{
        J2SEMutableImage image=new J2SEMutableImage(320,240);da.paint(image.getGraphics());
        BufferedImage png=new BufferedImage(320,240,BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics g=png.getGraphics();g.drawImage(image.getImage(),0,0,null);g.dispose();ImageIO.write(png,"png",target);
    }
    static void question(String text)throws Exception{
        command("write",(Displayable)field(app,"home"));
        TextBox compose=(TextBox)field(app,"compose");compose.setString(text);command("send",compose);
    }
    public static void main(String[] args){int code=1;
        try{
            MockHttps.install();
            Headless headless=new Headless();org.microemu.app.Common emulator=(org.microemu.app.Common)field(headless,"emulator");
            emulator.initParams(new ArrayList<String>(Arrays.asList("--rms","memory",args[0])),
                new org.microemu.app.util.DeviceEntry("E63 test",null,"org/microemu/device/default/device.xml",true,false),
                org.microemu.device.j2se.J2SEDevice.class);
            J2SEDeviceDisplay device=(J2SEDeviceDisplay)emulator.getDevice().getDeviceDisplay();
            device.setDisplayRectangle(new Rectangle(0,0,320,240));device.setDisplayPaintable(new Rectangle(0,0,320,220));
            emulator.initMIDlet(true);settle();app=MIDletBridge.getCurrentMIDlet();
            check(app!=null&&app.getClass().getName().equals("cepai.CepAiMIDlet"),"distributed MIDlet boots");
            da=MIDletBridge.getMIDletAccess(app).getDisplayAccess();
            File output=new File(args[1]);output.mkdirs();File fixtures=new File(args[2]);
            Object home=field(app,"home");
            check(home instanceof Canvas,"home uses custom Canvas");
            check(((Displayable)home).getWidth()==320,"glass home fits E63 width");
            capture(new File(output,"01-home.png"));
            call(home,"keyPressed",new Class[]{Integer.TYPE},new Object[]{Integer.valueOf(-7)});
            check(field(home,"menuOpen").equals(Boolean.TRUE),"right softkey opens custom menu");
            capture(new File(output,"05-menu.png"));
            call(home,"keyPressed",new Class[]{Integer.TYPE},new Object[]{Integer.valueOf(-7)});
            check(field(home,"menuOpen").equals(Boolean.FALSE),"right softkey closes custom menu");
            MockHttps.nextStatus=403;
            command("probe",da.getCurrent());awaitJob();
            check(da.getCurrent() instanceof Alert,"keyless HTTPS probe reports result");
            check(((Alert)da.getCurrent()).getTitle().equals("HTTPS çalışıyor"),"HTTP 403 still proves HTTPS channel");
            check(MockHttps.last.getRequestProperty("x-goog-api-key")==null,"probe sends no API key");
            dismiss();MockHttps.nextStatus=200;
            Class failures=app.getClass().getClassLoader().loadClass("cepai.ConnectionFailure");
            Method describe=failures.getMethod("describe",new Class[]{IOException.class,String.class,String.class});
            String detail=(String)describe.invoke(null,new Object[]{new IOException("native\r\n"+KEY),"HTTP yanıtı",KEY});
            check(!detail.contains(KEY)&&detail.contains("anahtar gizlendi"),"native diagnostics redact API key");
            detail=(String)describe.invoke(null,new Object[]{new javax.microedition.pki.CertificateException(null,javax.microedition.pki.CertificateException.EXPIRED),"HTTPS",KEY});
            check(detail.contains("tarih"),"certificate date failure distinguished");
            Class decoder=app.getClass().getClassLoader().loadClass("cepai.QrDecoder");
            Method decode=decoder.getMethod("decode",new Class[]{Image.class});
            String[] images={"qr-0.png","qr-rotated.png","qr-camera.jpg","qr-1.png"};
            for(int i=0;i<images.length;i++){
                InputStream stream=new FileInputStream(new File(fixtures,images[i]));Image image;
                try{image=Image.createImage(stream);}finally{stream.close();}
                String decoded=(String)decode.invoke(null,new Object[]{image});
                check(i==3?decoded.startsWith("{\"apiKey\":"):decoded.equals(KEY),"real QR decode "+images[i]);
            }
            boolean rejected=false;
            try{decode.invoke(null,new Object[]{Image.createImage(320,240)});}catch(InvocationTargetException expected){rejected=true;}
            check(rejected,"blank image fails without inventing a key");
            command("write",da.getCurrent());check(da.getCurrent().getClass().getName().equals("cepai.SettingsScreen"),"missing key opens glass settings");
            Displayable settings=(Displayable)field(app,"settingsForm");Object config=field(app,"config");
            call(app,"importQr",new Class[]{String.class},new Object[]{KEY});settle();
            check(((TextField)field(app,"keyField")).getString().equals(KEY),"QR populates key field");
            check(((String)field(config,"key")).length()==0,"QR import waits for Save");dismiss();
            check((((TextField)field(app,"keyField")).getConstraints()&TextField.PASSWORD)!=0,"key field masked");
            capture(new File(output,"02-settings.png"));
            call(settings,"select");settle();
            check(da.getCurrent() instanceof TextBox,"glass settings opens native QWERTY editor");
            TextBox editor=(TextBox)da.getCurrent();
            check((editor.getConstraints()&TextField.PASSWORD)!=0,"native key editor masks secrets");
            editor.setString("temporary-unsaved-value");
            call(settings,"commandAction",new Class[]{Command.class,Displayable.class},new Object[]{field(settings,"back"),editor});settle();
            check(da.getCurrent()==settings&&((TextField)field(app,"keyField")).getString().equals(KEY),"canceling editor preserves key draft");
            set(settings,"selected",Integer.valueOf(2));call(settings,"select");
            check(!((ChoiceGroup)field(app,"rememberField")).isSelected(0),"glass toggle changes remember draft");
            call(settings,"select");set(settings,"selected",Integer.valueOf(0));
            command("save",settings);check(((String)field(config,"key")).equals(KEY),"Save applies key");
            Object store=field(app,"store"),saved=call(store,"load");
            check(((String)field(saved,"key")).equals(KEY),"RMS roundtrip key");
            command("settings",da.getCurrent());settings=(Displayable)field(app,"settingsForm");
            ((ChoiceGroup)field(app,"rememberField")).setSelectedIndex(0,false);command("save",settings);
            check(((String)field(call(store,"load"),"key")).length()==0,"do not remember omits key from disk");
            check(((String)field(config,"key")).equals(KEY),"session key still available");

            MockHttps.nextBody="{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Merhaba! Bu bir emülatör test yanıtıdır.\"}]},\"finishReason\":\"STOP\"}]}";
            question("Türkçe bir yanıt yaz.");awaitJob();
            Object conversation=field(app,"conversation");
            check(((Integer)call(conversation,"size")).intValue()==1,"successful API response enters context");
            MockHttps sent=MockHttps.last;
            check(sent.getURL().getHost().equals("generativelanguage.googleapis.com"),"Google is the only destination");
            check(sent.getURL().getProtocol().equals("https"),"HTTPS required");
            check(!sent.getURL().toString().contains(KEY),"key absent from URL");
            check(KEY.equals(sent.getRequestProperty("x-goog-api-key")),"key sent in Google header");
            String request=sent.sent.toString("UTF-8");
            check(request.contains("Türkçe")&&!request.contains(KEY),"UTF-8 body; no key in prompt");
            check(((String)field(app,"draft")).length()==0,"successful question clears draft");
            capture(new File(output,"03-chat-synthetic.png"));

            MockHttps.nextStatus=429;question("Taslak korunmalı.");awaitJob();
            check(da.getCurrent() instanceof Alert,"HTTP error shown; current="+da.getCurrent().getClass().getName()+", status="+MockHttps.last.status);
            check(((String)field(app,"draft")).equals("Taslak korunmalı."),"failed request preserves draft");
            check(((Integer)call(conversation,"size")).intValue()==1,"failed request does not enter history");dismiss();
            MockHttps.nextStatus=200;MockHttps.blockNext=true;question("İptal testi.");
            Object blocked=field(app,"job");check(blocked!=null,"pending request exists");
            command("cancel",da.getCurrent());check(field(app,"job")==null,"cancel releases UI immediately");
            Thread.sleep(150);
            check(((Integer)call(conversation,"size")).intValue()==1,"canceled request cannot update history");
            MockHttps.blockNext=false;

            set(config,"voiceReplies",Boolean.FALSE);
            Class clipType=app.getClass().getClassLoader().loadClass("cepai.AudioClip");
            byte[] wave=(byte[])clipType.getMethod("header",new Class[]{Integer.TYPE,Integer.TYPE}).invoke(null,new Object[]{Integer.valueOf(16000),Integer.valueOf(8000)});
            Object clip=clipType.getMethod("fromWav",new Class[]{byte[].class,Integer.TYPE}).invoke(null,new Object[]{wave,Integer.valueOf(20000)});
            Class browserType=app.getClass().getClassLoader().loadClass("cepai.FileBrowser");
            Constructor browserConstructor=browserType.getDeclaredConstructor(new Class[]{app.getClass(),Boolean.TYPE});browserConstructor.setAccessible(true);
            Object browser=browserConstructor.newInstance(new Object[]{app,Boolean.TRUE});set(app,"files",browser);
            MockHttps beforePreview=MockHttps.last;
            call(app,"voiceFileResult",new Class[]{browserType,clipType},new Object[]{browser,clip});settle();
            Object voice=field(app,"voiceScreen");
            check(da.getCurrent()==voice&&MockHttps.last==beforePreview,"audio preview never uploads before Send");
            capture(new File(output,"04-voice-ready-synthetic.png"));
            Class jsonType=app.getClass().getClassLoader().loadClass("cepai.Json");
            String voiceText="{\"heard\":\"Saat kaç?\",\"answer\":\"Bu bir sesli test yanıtıdır.\"}";
            String quoted=(String)jsonType.getMethod("quote",new Class[]{String.class}).invoke(null,new Object[]{voiceText});
            MockHttps.nextBody="{\"candidates\":[{\"content\":{\"parts\":[{\"text\":"+quoted+"}]},\"finishReason\":\"STOP\"}]}";
            call(voice,"commandAction",new Class[]{Command.class,Displayable.class},new Object[]{field(voice,"send"),voice});awaitJob();
            check(((Integer)call(conversation,"size")).intValue()==2,"voice reply added once");
            check(call(conversation,"user",new Class[]{Integer.TYPE},new Object[]{Integer.valueOf(1)}).equals("Saat kaç?"),"actual transcript stored in conversation");
            String voiceRequest=MockHttps.last.sent.toString("UTF-8");
            check(voiceRequest.contains("inlineData")&&voiceRequest.contains("audio/wav")&&voiceRequest.contains("responseJsonSchema"),"voice sends WAV and transcript schema");
            check(!voiceRequest.contains(KEY),"voice body has no API key");
            check(field(app,"voiceScreen")==null,"successful send releases recorded clip");

            MockHttps.nextStatus=429;command("readAnswer",da.getCurrent());awaitJob();
            check(da.getCurrent() instanceof Alert&&da.getCurrent().getTitle().equals("Seslendirme"),"TTS quota failure explained separately");
            check(MockHttps.last.getURL().getPath().contains("tts:generateContent"),"read-aloud uses dedicated TTS model");
            check(MockHttps.last.sent.toString("UTF-8").contains("responseModalities"),"TTS audio request built");
            check(((Integer)call(conversation,"size")).intValue()==2,"TTS failure preserves conversation");dismiss();

            browser=browserConstructor.newInstance(new Object[]{app,Boolean.TRUE});set(app,"files",browser);
            call(app,"voiceFileResult",new Class[]{browserType,clipType},new Object[]{browser,clip});settle();voice=field(app,"voiceScreen");
            call(voice,"commandAction",new Class[]{Command.class,Displayable.class},new Object[]{field(voice,"send"),voice});awaitJob();dismiss();
            check(da.getCurrent()==voice&&field(voice,"clip")!=null,"failed voice request keeps clip for manual retry");
            call(voice,"commandAction",new Class[]{Command.class,Displayable.class},new Object[]{field(voice,"back"),voice});settle();
            check(field(app,"voiceScreen")==null,"discard releases unsent recording");MockHttps.nextStatus=200;

            command("settings",da.getCurrent());settings=(Displayable)field(app,"settingsForm");
            MockHttps.nextBody="{\"models\":[{\"name\":\"models/gemini-3.5-flash-lite\",\"supportedGenerationMethods\":[\"generateContent\"]},{\"name\":\"models/gemini-3.1-flash-image\",\"supportedGenerationMethods\":[\"generateContent\"]}]}";
            command("models",settings);awaitJob();
            check(da.getCurrent() instanceof javax.microedition.lcdui.List,"model discovery opens selector");
            javax.microedition.lcdui.List models=(javax.microedition.lcdui.List)field(app,"modelList");
            check(models.size()==1,"image-only model filtered");
            call(app,"commandAction",new Class[]{Command.class,Displayable.class},new Object[]{javax.microedition.lcdui.List.SELECT_COMMAND,models});settle();
            check(((TextField)field(app,"modelField")).getString().equals("gemini-3.5-flash-lite"),"model selection applied");
            MockHttps.nextBody="{\"models\":[{\"name\":\"models/gemini-3.8-flash-lite-tts\",\"supportedGenerationMethods\":[\"generateContent\"]},{\"name\":\"models/gemini-3.5-flash-lite\",\"supportedGenerationMethods\":[\"generateContent\"]}]}";
            command("ttsModels",settings);awaitJob();models=(javax.microedition.lcdui.List)field(app,"modelList");
            check(models.size()==1&&models.getString(0).contains("tts"),"TTS model selector filters text models");
            call(app,"commandAction",new Class[]{Command.class,Displayable.class},new Object[]{javax.microedition.lcdui.List.SELECT_COMMAND,models});settle();
            check(((TextField)field(app,"ttsField")).getString().contains("tts"),"TTS model selection applied");
            command("back",da.getCurrent());
            StringBuffer longText=new StringBuffer();for(int i=0;i<90;i++)longText.append("Satır ").append(i).append(": Bu uzun bir sentetik arayüz kaydırma testidir.\n");
            call(conversation,"add",new Class[]{String.class,String.class},new Object[]{"Uzun yanıt testi",longText.toString()});
            call(app,"renderHome");settle();
            check(((Integer)field(home,"scroll")).intValue()>0,"long conversation follows newest answer");
            int bottom=((Integer)field(home,"scroll")).intValue();
            Canvas canvas=(Canvas)home;
            call(home,"keyPressed",new Class[]{Integer.TYPE},new Object[]{Integer.valueOf(canvas.getKeyCode(Canvas.UP))});
            check(((Integer)field(home,"scroll")).intValue()<bottom,"direction key scrolls long response");
            capture(new File(output,"06-long-chat-synthetic.png"));
            check(call(conversation,"answer",new Class[]{Integer.TYPE},new Object[]{Integer.valueOf(2)}).equals(longText.toString()),"glass layout retains full response text");
            command("clear",da.getCurrent());command("yes",da.getCurrent());
            check(((Integer)call(conversation,"size")).intValue()==0,"new chat clears history");
            check(((String)field(config,"key")).equals(KEY),"new chat keeps API key");

            ByteArrayOutputStream legacyBytes=new ByteArrayOutputStream();DataOutputStream legacy=new DataOutputStream(legacyBytes);
            legacy.writeInt(1);legacy.writeUTF(KEY);legacy.writeUTF("gemini-3.5-flash-lite");legacy.writeBoolean(true);legacy.flush();
            javax.microedition.rms.RecordStore rms=javax.microedition.rms.RecordStore.openRecordStore("CepAI1",true);
            byte[] legacyData=legacyBytes.toByteArray();rms.setRecord(1,legacyData,0,legacyData.length);rms.closeRecordStore();
            Object migrated=call(store,"load");
            check(field(migrated,"key").equals(KEY),"upgrading legacy settings preserves API key");
            check(field(migrated,"ttsModel").equals("gemini-3.8-flash-lite-tts"),"legacy settings gain default TTS model");
            call(store,"save",new Class[]{config.getClass()},new Object[]{config});
            check(field(call(store,"load"),"ttsModel").equals(field(config,"ttsModel")),"new speech settings persist");
            MIDletBridge.getMIDletAccess(app).destroyApp(true);
            check(field(app,"job")==null,"exit cancels outstanding job");
            System.out.println("PASS: "+count+" packaged MIDlet / QR / mock HTTPS checks (no live API key used)");code=0;
        }catch(Throwable failure){failure.printStackTrace();}
        System.exit(code);
    }
}
