package dev.mraz1visual.models;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.util.Map;
import java.util.Set;

/** Placement coefficients adapted from AuraVisual CosmeticsManager (@fixedproject).
 * Original rights and individual contributors: META-INF/AURAVISUAL-NOTICE.txt. */
public final class AuraPlacement {
    private static final Map<String,float[]> WINGS=Map.ofEntries(
        Map.entry("wings_1_cddf708b",v(.05249998f,0)),Map.entry("wings_3_81b06a4b",v(.101499975f,-.0485f)),
        Map.entry("wings_7_ee401e1a",v(-.055999994f,.11074999f)),Map.entry("wings_8_ee401e1a",v(.17849997f,.094499975f)),
        Map.entry("wings_9_ee401e1a",v(-.24849999f,0)),Map.entry("wings_12_f684c6fe",v(0,.10100001f)),
        Map.entry("wings_14_19ec3eb9",v(-.1435f,0)),Map.entry("wings_22_3dfa663d",v(0,-.064750016f)),Map.entry("wings_23_28b34740",v(.048999995f,-.0485f)));
    private static final Map<String,float[]> BACK=Map.ofEntries(
        Map.entry("back_2_42b31d7f",v(-.22725f,0)),Map.entry("back_3_0c472e0e",v(-.22725f,180)),
        Map.entry("back_5_a3051e7e",v(0,88.20001f)),Map.entry("back_10_94b04f2e",v(-.24675f,180)),
        Map.entry("back_12_6d765ef2",v(-.2305f,0)),Map.entry("back_15_3d08f827",v(-.2305f,0)),
        Map.entry("back_16_997ccf8a",v(-.09075001f,0)),Map.entry("back_17_d31ee85e",v(-.2305f,0)),
        Map.entry("back_20_e91ba7a0",v(0,180)),Map.entry("back_21_61fb616b",v(-.120000005f,0)),
        Map.entry("back_23_7fef0612",v(-.25f,0)),Map.entry("back_24_0212a027",v(-.23375f,180)));
    private static final Map<String,float[]> PETS=Map.ofEntries(
        Map.entry("pets_3_4d300ebb",v(-90,0,0)),Map.entry("pets_6_d97f2e47",v(-90,180,-23.399994f)),
        Map.entry("pets_18_95f0ae96",v(0,-180,0)),Map.entry("pets_19_95f0ae96",v(0,-180,0)),
        Map.entry("pets_20_95f0ae96",v(0,-180,0)),Map.entry("pets_21_95f0ae96",v(0,-180,0)));
    private static final Map<String,float[]> MODELS=Map.ofEntries(
        Map.entry("hats_2_cf6887c0",v(0,0,-.16f,0,90,-90)),Map.entry("hats_13_e2b017aa",v(0,0,0,0,0,180)),
        Map.entry("back_4_73991370",v(-.14f,0,0,0,0,0)),Map.entry("back_5_a3051e7e",v(.14f,0,0,0,0,0)),
        Map.entry("back_11_88774abf",v(0,0,0,0,180,0)),Map.entry("back_14_82cf5272",v(-.1f,-.1f,0,0,0,0)),
        Map.entry("back_18_b9cf72a5",v(0,0,.15f,0,0,0)),Map.entry("back_19_5b7aef16",v(0,0,0,0,180,0)),
        Map.entry("back_24_0212a027",v(.05f,-.1f,0,0,0,0)),Map.entry("pets_6_d97f2e47",v(0,.1f,0,0,0,0)));
    private static final Set<String> TOP=Set.of("hats_1_f83bf9c2","hats_5_469c9b9e","hats_8_6a481e43","hats_9_5f7f08cb","hats_15_5a319037","hats_16_f37d302a","hats_17_8f15edc6","hats_20_f2a80fc7","hats_25_2b9734ca");
    private static final Set<String> OPPOSITE=Set.of("wings_3_81b06a4b","wings_4_ac93f2ee","wings_8_ee401e1a","wings_9_ee401e1a","wings_13_04875af2","wings_22_3dfa663d","wings_23_28b34740");
    private final float x,y,z,scale;
    private AuraPlacement(float x,float y,float z,float scale){this.x=x;this.y=y;this.z=z;this.scale=scale;}
    private static float[] v(float... values){return values;}
    private static float ratio(float target,float size){return size>1e-4f?target/size:1;}
    private static float rad(float degrees){return (float)Math.toRadians(degrees);}
    private static Matrix4f rotation(float[] r){return new Matrix4f().rotateZ(rad(r[2])).rotateY(rad(r[1])).rotateX(rad(r[0]));}
    private static float[] bounds(float[] b,Matrix4f matrix){
        float[] out=v(Float.MAX_VALUE,Float.MAX_VALUE,Float.MAX_VALUE,-Float.MAX_VALUE,-Float.MAX_VALUE,-Float.MAX_VALUE);
        Vector3f p=new Vector3f();
        for(int i=0;i<8;i++){
            matrix.transformPosition(p.set(b[(i&1)==0?0:3],b[(i&2)==0?1:4],b[(i&4)==0?2:5]));
            out[0]=Math.min(out[0],p.x);out[1]=Math.min(out[1],p.y);out[2]=Math.min(out[2],p.z);
            out[3]=Math.max(out[3],p.x);out[4]=Math.max(out[4],p.y);out[5]=Math.max(out[5],p.z);
        }return out;
    }
    public static AuraPlacement load(String slug,String category,JsonObject meta,Mesh mesh,boolean auraAxes){
        mesh.fitBounds(new float[mesh.bones.length*9]);
        JsonObject locations=Json.obj(meta,"locationRotation"),normal=locations==null?null:Json.obj(locations,"normal");
        float[] r=normal==null?v(0,0,0):Json.vec(normal,"rotation",3,0);
        float[] signs=normal==null?v(1,1,1):Json.vec(normal,"scale",3,1);
        for(int i=0;i<3;i++)signs[i]=Float.isFinite(signs[i])&&signs[i]!=0?Math.signum(signs[i]):1;
        // The banner lettering faces the back camera. Preserve the printed side of the mesh.
        if (slug.equals("back_18_b9cf72a5")) signs[0] *= -1;
        Matrix4f orientation=rotation(r).scale(signs[0],signs[1],signs[2]);
        float[] b=bounds(mesh.bounds,orientation);
        if(category.equals("wings")&&b[5]-b[2]>(b[3]-b[0])*1.35f){
            orientation=rotation(r).rotateY(rad(90)).scale(signs[0],signs[1],signs[2]);b=bounds(mesh.bounds,orientation);
        }
        float width=b[3]-b[0],height=b[4]-b[1],depth=b[5]-b[2],cx=(b[0]+b[3])/2,cy=(b[1]+b[4])/2,cz=(b[2]+b[5])/2;
        float s,tx,ty,tz;
        if(category.equals("hats")){
            s=Math.min(1,Math.min(ratio(1.25f,width),Math.min(ratio(1.15f,height),ratio(1.1f,depth))));
            float largest=Math.max(width,Math.max(height,depth));
            if(largest<.35f)s=Math.min(1.65f,ratio(.52f,largest));else if(largest<.55f)s=Math.min(1.3f,ratio(.62f,largest));
            float seat = slug.equals("hat_98") ? .64f : TOP.contains(slug) ? .27f : -.03f;
            // This source contains only a very shallow brim; embedding it hides almost the entire texture.
            if (slug.equals("hats_5_469c9b9e")) seat=.49f;
            tx=-cx*s;ty=seat-b[1]*s;tz=-cz*s;
        }else if(category.equals("wings")){
            s=Math.min(1,Math.min(ratio(2.8f,width),ratio(1.75f,height)));float targetY=-.32f,targetZ=.14f;
            if(slug.equals("wings_8_ee401e1a"))targetY-=.06f;
            if(slug.equals("wings_3_81b06a4b"))targetY+=.16f;
            if(slug.equals("wings_4_ac93f2ee"))targetY+=.21f;
            if(slug.equals("wings_13_04875af2")||slug.equals("wings_23_28b34740"))targetY+=.08f;
            if(slug.equals("wings_9_ee401e1a")){targetY+=.11f;targetZ+=.14f;}
            float[] a=WINGS.getOrDefault(slug,v(0,0));tx=-cx*s;ty=targetY-cy*s+a[0];tz=targetZ-b[2]*s+a[1];
        }else if(category.equals("back")){
            s=Math.min(1,Math.min(ratio(1.18f,width),Math.min(ratio(1.48f,height),ratio(1.05f,depth))));
            tx=-cx*s;ty=-.3f-cy*s;tz=.1f-b[2]*s+BACK.getOrDefault(slug,v(0,0))[0];
        }else{s=Math.min(.9f,Math.min(ratio(.72f,width),Math.min(ratio(.76f,height),ratio(.72f,depth))));tx=-cx*s;ty=-b[1]*s;tz=-cz*s;}
        float[] adjust=MODELS.getOrDefault(slug,v(0,0,0,0,0,0)),extra=PETS.getOrDefault(slug,v(0,0,0));
        float[] fixed=v(adjust[3]+(category.equals("pets")?extra[0]:0),adjust[4]+(category.equals("pets")?extra[1]:0),adjust[5]+(category.equals("pets")?extra[2]:0)+(category.equals("back")?BACK.getOrDefault(slug,v(0,0))[1]:0));
        Matrix4f root=new Matrix4f().translate(cx,cy,cz).mul(rotation(fixed));
        if(category.equals("wings") && auraAxes)root.scale(OPPOSITE.contains(slug)?-1:1,OPPOSITE.contains(slug)?1:-1,1);
        mesh.rootTransform=root.translate(-cx,-cy,-cz).mul(orientation);
        mesh.fitBounds(new float[mesh.bones.length*9]);
        return new AuraPlacement(tx+adjust[0],ty+adjust[1],tz+adjust[2],s);
    }
    public void apply(PoseStack ps,float userScale){ps.scale(userScale,userScale,userScale);ps.translate(x,y,z);ps.scale(scale,scale,scale);}
}
