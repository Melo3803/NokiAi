package cepai;

import java.io.*;
import javax.microedition.media.*;
import javax.microedition.media.control.RecordControl;

public final class MediaLifecycleTest {
    static int count;
    static void check(boolean ok,String label){count++;if(!ok)throw new AssertionError(label);}
    static final class FakePlayer implements Player,RecordControl {
        final byte[] fixture;boolean closed,started,recording,autoEnd;String type="audio/x-wav";
        OutputStream output;PlayerListener listener;
        FakePlayer(byte[] bytes){fixture=bytes;}
        public void realize(){}public void prefetch(){}
        public void start(){started=true;if(autoEnd&&listener!=null)listener.playerUpdate(this,PlayerListener.END_OF_MEDIA,null);}
        public void stop(){started=false;}public void deallocate(){}
        public void close(){closed=true;started=false;}
        public long setMediaTime(long value){return value;}public long getMediaTime(){return 0;}
        public int getState(){return closed?CLOSED:(started?STARTED:REALIZED);}public long getDuration(){return 1000000;}
        public String getContentType(){return type;}public void setLoopCount(int loops){}
        public void addPlayerListener(PlayerListener value){listener=value;}public void removePlayerListener(PlayerListener value){listener=null;}
        public Control getControl(String name){return name.equals("RecordControl")?this:null;}
        public Control[] getControls(){return new Control[]{this};}
        public void setRecordStream(OutputStream value){output=value;}public void setRecordLocation(String path){}
        public void startRecord(){recording=true;}public void stopRecord(){recording=false;}
        public void commit()throws IOException{output.write(fixture);}public int setRecordSizeLimit(int limit){return limit;}
        public void reset(){}
    }
    static final class Recorder extends Microphone {
        FakePlayer player;int opens;boolean denied;boolean amrFirst;
        protected Player open(String locator){
            opens++;if(denied)throw new SecurityException("denied");
            player=new FakePlayer(VoiceCodecTest.pcmWav(8000,1));if(amrFirst&&opens==1)player.type="audio/amr";return player;
        }
    }
    static final class Speaker extends AudioPlayback {
        volatile FakePlayer player;boolean auto=true,fail;
        protected Player open(byte[] wav)throws MediaException{
            if(fail)throw new MediaException("unsupported");player=new FakePlayer(wav);player.autoEnd=auto;return player;
        }
    }
    public static void main(String[] args)throws Exception{
        Recorder microphone=new Recorder();microphone.start();FakePlayer recording=microphone.player;
        check(recording.started&&recording.recording,"recording requires explicit start");
        AudioClip result=microphone.finish();check(result.durationMillis==1000&&recording.closed,"finish commits and releases microphone");
        microphone=new Recorder();microphone.amrFirst=true;microphone.start();check(microphone.opens==2,"AMR falls through to WAV capture");microphone.cancel();
        check(microphone.player.closed,"cancel releases microphone");
        boolean rejected=false;try{microphone.finish();}catch(IOException ok){rejected=true;}check(rejected,"canceled recording not returned");
        microphone=new Recorder();microphone.denied=true;rejected=false;
        try{microphone.start();}catch(SecurityException ok){rejected=true;}check(rejected&&microphone.opens==1,"permission denial is not retried");
        final FakePlayer oversized=new FakePlayer(new byte[AudioClip.MAX_INPUT+1]);
        Microphone bounded=new Microphone(){protected Player open(String locator){return oversized;}};bounded.start();
        rejected=false;try{bounded.finish();}catch(IOException ok){rejected=true;}
        check(rejected&&oversized.closed,"record size limit still closes microphone");
        AudioClip clip=AudioClip.fromWav(VoiceCodecTest.pcmWav(8000,1),20000);
        final int[] callbacks={0};final String[] errors={null};
        AudioPlayback.Listener listener=new AudioPlayback.Listener(){public void finished(String error){errors[0]=error;callbacks[0]++;}};
        Speaker speaker=new Speaker();speaker.play(clip,listener);
        long deadline=System.currentTimeMillis()+2000;while(callbacks[0]==0&&System.currentTimeMillis()<deadline)Thread.sleep(10);
        check(callbacks[0]==1&&!speaker.isActive()&&speaker.player.closed,"end of media closes player once");
        callbacks[0]=0;speaker=new Speaker();speaker.auto=false;speaker.play(clip,listener);
        deadline=System.currentTimeMillis()+2000;while((speaker.player==null||!speaker.player.started)&&System.currentTimeMillis()<deadline)Thread.sleep(10);
        FakePlayer old=speaker.player;speaker.stop();old.listener.playerUpdate(old,PlayerListener.END_OF_MEDIA,null);
        check(callbacks[0]==0&&old.closed&&!speaker.isActive(),"late playback event after cancel ignored");
        speaker=new Speaker();speaker.fail=true;speaker.play(clip,listener);
        deadline=System.currentTimeMillis()+2000;while(callbacks[0]==0&&System.currentTimeMillis()<deadline)Thread.sleep(10);
        check(callbacks[0]==1&&errors[0]!=null&&!speaker.isActive(),"unsupported playback releases state with error");
        System.out.println("PASS: "+count+" microphone / playback lifecycle checks (synthetic devices)");
    }
}
