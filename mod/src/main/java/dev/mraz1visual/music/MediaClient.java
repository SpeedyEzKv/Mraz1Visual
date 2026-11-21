package dev.mraz1visual.music;

import com.google.gson.*;
import dev.mraz1visual.music.player.MusicPlayer;
import dev.mraz1visual.music.player.Tags;
import net.minecraft.resources.Identifier;
import javax.imageio.ImageIO;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.concurrent.*;

/** The launcher bridge and the bundled helper use the same authenticated protocol and native service. */
public final class MediaClient {
    private record Endpoint(int port,String token) {}
    private static volatile Endpoint endpoint=initial();
    private static final boolean WINDOWS=System.getProperty("os.name","").toLowerCase(java.util.Locale.ROOT).contains("windows");
    private static final HttpClient HTTP=HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofMillis(500)).build();
    private static final Identifier COVER=Identifier.fromNamespaceAndPath("mraz1visual","music/cover");
    public static volatile String title="",artist="",app="",error="";
    public static volatile boolean playing,hasCover,canToggle,canNext,canPrev,canSeek;
    public static volatile long position,duration,polledAt;
    private static volatile long coverId=-1;
    private static Process helper;
    private static Thread thread;
    private static int localSerial=-1;
    private MediaClient() {}
    private static Endpoint initial(){try{int p=Integer.parseInt(System.getProperty("mraz1visual.media.port","0"));String t=System.getProperty("mraz1visual.media.token","");return p>0&&p<=65535&&!t.isBlank()?new Endpoint(p,t):null;}catch(Exception e){return null;}}
    public static boolean available(){return WINDOWS||endpoint!=null;}
    public static Identifier cover(){return COVER;}
    public static long positionNow(){return duration<=0?0:Math.min(duration,position+(playing?Math.max(0,System.currentTimeMillis()-polledAt):0));}
    public static synchronized void start(){if(!available()||thread!=null)return;thread=new Thread(MediaClient::loop,"Mraz1Visual media");thread.setDaemon(true);thread.start();}
    private static void clear(){title=artist=app="";playing=hasCover=canToggle=canNext=canPrev=canSeek=false;position=duration=0;coverId=-1;}
    private static void loop(){int failures=0;while(!Thread.currentThread().isInterrupted()){
        try{if(endpoint==null&&!startHelper()){error="Системный медиасервис недоступен";Thread.sleep(5000);continue;}poll();publishLocal();failures=0;error="";}
        catch(Throwable ignored){clear();error="Связь с медиасервисом потеряна";if(++failures>=3){endpoint=null;localSerial=-1;if(helper!=null){helper.destroy();helper=null;}failures=0;}}
        try{Thread.sleep(700);}catch(InterruptedException e){Thread.currentThread().interrupt();return;}
    }}
    private static boolean startHelper(){if(!WINDOWS)return false;try{
        byte[] binary;try(var input=MediaClient.class.getResourceAsStream("/assets/mraz1visual/native/windows/mraz1visual-media.exe")){if(input==null)return false;binary=input.readAllBytes();}
        String digest=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(binary));
        Path dir=net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("mraz1visual/runtime");Files.createDirectories(dir);
        Path executable=dir.resolve("media-"+digest.substring(0,16)+".exe");
        if(!Files.isRegularFile(executable)||!java.util.Arrays.equals(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(executable)),java.security.MessageDigest.getInstance("SHA-256").digest(binary)))Files.write(executable,binary);
        Process child=new ProcessBuilder(executable.toString()).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        var reader=new BufferedReader(new InputStreamReader(child.getInputStream(),java.nio.charset.StandardCharsets.UTF_8));
        String line;try{line=CompletableFuture.supplyAsync(()->{try{return reader.readLine();}catch(IOException e){return null;}}).get(5,TimeUnit.SECONDS);}catch(Exception e){child.destroy();return false;}
        if(line==null){child.destroy();return false;}JsonObject j=JsonParser.parseString(line).getAsJsonObject();int port=j.get("port").getAsInt();String token=j.get("token").getAsString();
        if(port<1||port>65535||!token.matches("[0-9a-f]{64}")){child.destroy();return false;}
        helper=child;endpoint=new Endpoint(port,token);localSerial=-1;
        Runtime.getRuntime().addShutdownHook(new Thread(()->{try{child.getOutputStream().close();}catch(IOException ignored){}child.destroy();},"Mraz media shutdown"));return true;
    }catch(Exception ignored){return false;}}
    private static HttpRequest.Builder req(String path){Endpoint e=endpoint;if(e==null)throw new IllegalStateException("Media offline");return HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+e.port+path)).header("X-Mraz1Visual-Token",e.token).timeout(Duration.ofSeconds(5));}
    private static HttpResponse<String> post(String path,String body)throws Exception{return HTTP.send(req(path).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());}
    private static void poll()throws Exception{
        var r=HTTP.send(req("/now").GET().build(),HttpResponse.BodyHandlers.ofString());if(r.statusCode()!=200)throw new IOException("Media response");JsonObject j=JsonParser.parseString(r.body()).getAsJsonObject();
        String nextTitle=j.get("title").getAsString(),nextArtist=j.get("artist").getAsString(),nextApp=j.get("app").getAsString();
        boolean changed=!title.equals(nextTitle)||!artist.equals(nextArtist)||!app.equals(nextApp);
        if(changed)hasCover=false;title=nextTitle;artist=nextArtist;app=nextApp;playing=j.get("playing").getAsBoolean();position=Math.max(0,j.get("position").getAsLong());duration=Math.max(0,j.get("duration").getAsLong());polledAt=System.currentTimeMillis();
        JsonObject caps=j.getAsJsonObject("caps");canToggle=caps.get("toggle").getAsBoolean();canNext=caps.get("next").getAsBoolean();canPrev=caps.get("prev").getAsBoolean();canSeek=caps.get("seek").getAsBoolean();
        long cid=j.get("cover_id").getAsLong();if(changed||cid!=coverId){coverId=cid;loadCover(cid,nextTitle);}
    }
    private static void loadCover(long expected,String track)throws Exception{var r=HTTP.send(req("/cover").GET().build(),HttpResponse.BodyHandlers.ofByteArray());if(r.statusCode()!=200||r.body().length==0||r.body().length>4*1024*1024){hasCover=false;return;}
        var img=ImageIO.read(new ByteArrayInputStream(r.body()));if(img==null){hasCover=false;return;}NowPlaying.upload(img,COVER,()->coverId==expected&&title.equals(track),()->hasCover=true);}
    private static void publishLocal()throws Exception{
        JsonObject j=new JsonObject();var t=MusicPlayer.current();j.addProperty("title",t==null?"":t.title);j.addProperty("artist",t==null?"":t.artist);j.addProperty("playing",MusicPlayer.playing());j.addProperty("position",MusicPlayer.position());j.addProperty("duration",MusicPlayer.length());j.addProperty("cover_id",0x100000000L+MusicPlayer.trackSerial);j.addProperty("volume",MusicPlayer.volume);
        JsonObject caps=new JsonObject();boolean active=t!=null;caps.addProperty("toggle",active);caps.addProperty("next",active&&MusicPlayer.queue().size()>1);caps.addProperty("prev",active);caps.addProperty("seek",active&&MusicPlayer.length()>0);caps.addProperty("volume",active);j.add("caps",caps);post("/local",j.toString());
        if(t!=null&&localSerial!=MusicPlayer.trackSerial){localSerial=MusicPlayer.trackSerial;try{byte[] cover=Tags.read(t.path,true).cover;if(cover!=null&&cover.length<=4*1024*1024)HTTP.send(req("/local/cover").POST(HttpRequest.BodyPublishers.ofByteArray(cover)).build(),HttpResponse.BodyHandlers.discarding());}catch(Exception ignored){}}
        var response=HTTP.send(req("/local/commands").GET().build(),HttpResponse.BodyHandlers.ofString());if(response.statusCode()!=200)return;
        for(JsonElement element:JsonParser.parseString(response.body()).getAsJsonArray()){var c=element.getAsJsonObject();var task=new CompletableFuture<Boolean>();
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);
            net.minecraft.client.Minecraft.getInstance().execute(()->{if(task.isDone()||System.nanoTime()>deadline){task.complete(false);return;}try{boolean accepted=MusicPlayer.active();if(accepted)switch(c.get("command").getAsString()){
                case "toggle"->MusicPlayer.toggle();case "next"->MusicPlayer.next();case "prev"->MusicPlayer.prev();case "seek"->MusicPlayer.seek(c.get("value").getAsLong());
                case "volume"->{float value=Math.max(0,Math.min(1,c.get("value").getAsFloat()/100));var settings=dev.mraz1visual.module.MusicPlayerModule.get();if(settings!=null)settings.setVolume(value);MusicPlayer.volume=value;}
                default->accepted=false;}task.complete(accepted);}catch(Throwable failure){task.complete(false);}});
            boolean ok;try{ok=task.get(2,TimeUnit.SECONDS);}catch(Exception ignored){task.cancel(false);ok=false;}if(ok&&c.get("command").getAsString().equals("volume"))MusicPlayer.save();
            JsonObject result=new JsonObject();result.addProperty("id",c.get("id").getAsLong());result.addProperty("ok",ok);post("/local/result",result.toString());}
    }
    public static boolean supports(String c){return switch(c){case "toggle"->canToggle;case "next"->canNext;case "prev"->canPrev;case "seek"->canSeek;default->false;};}
    public static void command(String c){if(!supports(c)||endpoint==null)return;JsonObject body=new JsonObject();body.addProperty("command",c);
        HTTP.sendAsync(req("/command").header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body.toString())).build(),HttpResponse.BodyHandlers.ofString()).thenAccept(r->{if(r.statusCode()!=200)error="Плеер отклонил команду";}).exceptionally(e->{error="Плеер недоступен";return null;});}
    public static String appName(){String a=app.toLowerCase(java.util.Locale.ROOT);if(a.contains("spotify"))return "Spotify";if(a.contains("yandex"))return a.contains("music")?"Яндекс Музыка":"Яндекс Браузер";if(a.contains("chrome"))return "Chrome";if(a.contains("msedge"))return "Edge";if(a.contains("firefox")||a.contains("zen"))return "Браузер";if(a.contains("vk"))return "VK";return app.isEmpty()?"":"Плеер";}
}
