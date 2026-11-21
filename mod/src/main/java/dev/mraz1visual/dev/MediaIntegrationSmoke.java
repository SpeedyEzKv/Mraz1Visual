package dev.mraz1visual.dev;
import dev.mraz1visual.music.MediaClient;
import dev.mraz1visual.music.player.*;
import com.google.gson.*;
import java.nio.file.Path;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.concurrent.*;
import javax.sound.sampled.*;

/** Exercises real decoded silent PCM and acknowledged local-player commands, never external players. */
final class MediaIntegrationSmoke {
    static CompletableFuture<String> result;
    static void begin(Path dir){result=CompletableFuture.supplyAsync(()->{
        float volume=MusicPlayer.volume;boolean ownTrack=false;
        try{
            var format=new AudioFormat(44100,16,2,true,false);Path wav=dir.resolve("silent-media-fixture.wav");
            try(var audio=new AudioInputStream(new java.io.ByteArrayInputStream(new byte[44100*4*20]),format,44100*20L)){AudioSystem.write(audio,AudioFileFormat.Type.WAVE,wav.toFile());}
            var track=new Track(wav,"Mraz native media fixture","Local verification","");Path secondWav=dir.resolve("silent-media-fixture-next.wav");
            java.nio.file.Files.copy(wav,secondWav,java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            var second=new Track(secondWav,"Second native media fixture","Local verification","");
            MusicPlayer.playList(java.util.List.of(track,second),0);ownTrack=true;
            long end=System.nanoTime()+15_000_000_000L;Object endpoint=null;var f=MediaClient.class.getDeclaredField("endpoint");f.setAccessible(true);
            while(System.nanoTime()<end){endpoint=f.get(null);if(endpoint!=null&&MusicPlayer.position()>800&&MusicPlayer.playing())break;Thread.sleep(100);}
            if(endpoint==null||MusicPlayer.position()<800)throw new IllegalStateException("Local decoder or native helper did not start");
            var portMethod=endpoint.getClass().getDeclaredMethod("port");portMethod.setAccessible(true);var tokenMethod=endpoint.getClass().getDeclaredMethod("token");tokenMethod.setAccessible(true);
            int port=(Integer)portMethod.invoke(endpoint);String token=(String)tokenMethod.invoke(endpoint);
            var http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
            class Bridge {
                HttpResponse<String> request(String path,String json)throws Exception{var req=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).header("X-Mraz1Visual-Token",token).timeout(Duration.ofSeconds(6));if(json!=null)req.header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(json));return http.send(req.build(),HttpResponse.BodyHandlers.ofString());}
            }
            var bridge=new Bridge();boolean source=false;
            while(System.nanoTime()<end){var n=JsonParser.parseString(bridge.request("/now",null).body()).getAsJsonObject();for(var s:n.getAsJsonArray("sources"))if(s.getAsJsonObject().get("id").getAsString().equals("local"))source=true;if(source)break;Thread.sleep(100);}
            if(!source||bridge.request("/select","{\"id\":\"local\"}").statusCode()!=200)throw new IllegalStateException("Local source not published");Thread.sleep(500);
            for(String transport:new String[]{"next","prev"}){
                Track expected=transport.equals("next")?second:track;
                if(bridge.request("/command","{\"command\":\""+transport+"\"}").statusCode()!=200)throw new IllegalStateException("Transport refused: "+transport);
                long deadline=System.nanoTime()+3_000_000_000L;
                while(MusicPlayer.current()!=expected&&System.nanoTime()<deadline)Thread.sleep(25);
                if(MusicPlayer.current()!=expected)throw new IllegalStateException("Transport left wrong track selected: "+transport);
            }
            for(String command:new String[]{"{\"command\":\"volume\",\"value\":38}","{\"command\":\"seek\",\"value\":4000}","{\"command\":\"toggle\"}"})if(bridge.request("/command",command).statusCode()!=200)throw new IllegalStateException("Local command was not acknowledged");
            if(Math.abs(MusicPlayer.volume-.38f)>.001||MusicPlayer.playing())throw new IllegalStateException("Local player did not execute volume/pause commands");
            if(bridge.request("/command","{\"command\":\"seek\",\"value\":2500}").statusCode()!=200)throw new IllegalStateException("Paused seek rejected");
            long pausedDeadline=System.nanoTime()+2_000_000_000L;while(MusicPlayer.position()!=2500&&System.nanoTime()<pausedDeadline)Thread.sleep(25);
            if(MusicPlayer.position()!=2500||MusicPlayer.playing())throw new IllegalStateException("Paused seek did not update actual position without resuming playback");
            var blocked=new CountDownLatch(1);var released=new CountDownLatch(1);
            net.minecraft.client.Minecraft.getInstance().execute(()->{blocked.countDown();try{Thread.sleep(4500);}catch(InterruptedException e){Thread.currentThread().interrupt();}finally{released.countDown();}});
            if(!blocked.await(3,TimeUnit.SECONDS))throw new IllegalStateException("Main-thread stall fixture did not start");
            int expired=bridge.request("/command","{\"command\":\"volume\",\"value\":17}").statusCode();
            if(!released.await(6,TimeUnit.SECONDS)||expired==200||Math.abs(MusicPlayer.volume-.38f)>.001)throw new IllegalStateException("Expired media command executed after the main thread resumed");
            if(MusicPlayer.signalLevel>.00001)throw new IllegalStateException("Silent PCM produced a fabricated level");
            bridge.request("/select","{\"id\":\"auto\"}");
            return "PASS actual Java decoder, silent PCM, native local source, next/previous, volume/seek/pause, paused seek and cancelled late commands; external sessions untouched";
        }catch(Exception e){return "FAIL local media integration: "+e.getClass().getSimpleName()+": "+e.getMessage();}
        finally{if(ownTrack)MusicPlayer.stop();net.minecraft.client.Minecraft.getInstance().execute(()->{var settings=dev.mraz1visual.module.MusicPlayerModule.get();if(settings!=null)settings.setVolume(volume);MusicPlayer.volume=volume;});}
    });}
}
