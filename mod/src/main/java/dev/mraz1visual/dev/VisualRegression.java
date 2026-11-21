package dev.mraz1visual.dev;

import dev.mraz1visual.models.ModelPrefs;
import java.nio.file.*;
import java.util.function.BiConsumer;

final class VisualRegression {
    private static final java.util.concurrent.atomic.AtomicBoolean coverCurrent=new java.util.concurrent.atomic.AtomicBoolean(),coverPublished=new java.util.concurrent.atomic.AtomicBoolean();
    static void beginCoverRace()throws Exception{
        coverCurrent.set(true);coverPublished.set(false);
        var upload=dev.mraz1visual.music.NowPlaying.class.getDeclaredMethod("upload",java.awt.image.BufferedImage.class,net.minecraft.resources.Identifier.class,java.util.function.BooleanSupplier.class,Runnable.class);upload.setAccessible(true);
        java.util.concurrent.CompletableFuture.runAsync(()->{try{upload.invoke(null,new java.awt.image.BufferedImage(2,2,java.awt.image.BufferedImage.TYPE_INT_ARGB),net.minecraft.resources.Identifier.fromNamespaceAndPath("mraz1visual","music/audit-stale-cover"),(java.util.function.BooleanSupplier)coverCurrent::get,(Runnable)()->coverPublished.set(true));}catch(Exception e){throw new IllegalStateException(e);}}).get(5,java.util.concurrent.TimeUnit.SECONDS);
        coverCurrent.set(false);
    }
    static void verifyCoverRace(BiConsumer<Boolean,String> check){check.accept(!coverPublished.get(),"late cover decoding cannot publish artwork after the track has changed");}
    private static boolean aspectEnabled;private static int aspectSetting;
    static void beginAspect(){var a=dev.mraz1visual.Mraz1Visual.MODULES.find("aspect_ratio");aspectEnabled=a.isEnabled();var setting=(dev.mraz1visual.setting.EnumSetting)a.settings().stream().filter(s->s.id.equals("ratio")).findFirst().orElseThrow();aspectSetting=setting.get();setting.set(0);a.setEnabled(true);}
    static void verifyAspect(net.minecraft.client.Minecraft mc,BiConsumer<Boolean,String> check){
        org.joml.Matrix4f projection;
        //? if >=26.1 {
        /*var camera=mc.gameRenderer.getMainCamera();projection=camera.getViewRotationProjectionMatrix(new org.joml.Matrix4f()).mul(camera.getViewRotationMatrix(new org.joml.Matrix4f()).invert());
        *///?} else {
        projection=mc.gameRenderer.getProjectionMatrix(mc.options.fov().get().floatValue());
        //?}
        check.accept(Math.abs(Math.abs(projection.m11()/projection.m00())-4f/3)<.01f,"Aspect Ratio changes the real world projection to 4:3");
        var a=dev.mraz1visual.Mraz1Visual.MODULES.find("aspect_ratio");((dev.mraz1visual.setting.EnumSetting)a.settings().stream().filter(s->s.id.equals("ratio")).findFirst().orElseThrow()).set(aspectSetting);a.setEnabled(aspectEnabled);
    }
    static void modelPreferences(BiConsumer<Boolean,String> check)throws Exception{
        Path file=dev.mraz1visual.util.TextFiles.DIR.resolve("models.json");byte[] original=Files.exists(file)?Files.readAllBytes(file):null;
        try{
            Files.createDirectories(file.getParent());
            ModelPrefs.weapons.put("audit-removed","old");ModelPrefs.favorites.add("audit-removed");ModelPrefs.poses.put("audit-removed",new float[3][]);
            Files.writeString(file,"{\"weapons\":{},\"favorites\":[],\"poses\":{}}");ModelPrefs.load();
            check.accept(ModelPrefs.weapons.isEmpty()&&ModelPrefs.favorites.isEmpty()&&ModelPrefs.poses.isEmpty(),"cosmetic preference reload removes deleted skins, favorites and poses");
            Files.writeString(file,"{\"poses\":{\"audit-finite\":[[0,0,\"NaN\",0,0,0,-2]]}}");ModelPrefs.load();var pose=ModelPrefs.poses.get("audit-finite")[0];
            check.accept(Float.isFinite(pose[2])&&pose[6]==1,"invalid cosmetic pose numbers cannot create non-finite render matrices");
        }finally{if(original!=null)Files.write(file,original);else Files.deleteIfExists(file);ModelPrefs.weapons.clear();ModelPrefs.favorites.clear();ModelPrefs.poses.clear();ModelPrefs.load();}
    }
}
