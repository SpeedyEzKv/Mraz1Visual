package dev.mraz1visual.render.post;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.GlTexture;
import dev.mraz1visual.core.ModuleManager;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.*;

/** Temporal world composition at the world/HUD boundary; history never contains GUI. */
public final class WorldMotionBlur {
    private static int width,height,program,vao,sourceFbo,sceneTex,sceneFbo,historyTex,historyFbo;
    private static boolean previous,failed;
    private static Object level;
    private static long lastNs;
    private static int frames,errors;
    private static final String FRAGMENT="""
        #version 330 core
        uniform sampler2D uScene;
        uniform sampler2D uHistory;
        uniform float uWeight;
        in vec2 uv;
        out vec4 fragColor;
        void main(){fragColor=vec4(mix(texture(uScene,uv).rgb,texture(uHistory,uv).rgb,uWeight),1.0);}
        """;
    private WorldMotionBlur(){}
    public static void reset(){previous=false;lastNs=0;}
    public static int frames(){return frames;}
    public static int errors(){return errors;}
    public static void render(float strength){
        var mc=Minecraft.getInstance();
        if(failed||mc.level==null||mc.screen!=null){reset();return;}
        var main=mc.getMainRenderTarget();
        if(!(main.getColorTexture() instanceof GlTexture color)||main.width<1||main.height<1)return;
        int read=GlStateManager.getFrameBuffer(GL30.GL_READ_FRAMEBUFFER),draw=GlStateManager.getFrameBuffer(GL30.GL_DRAW_FRAMEBUFFER);
        try{
            if(program==0){program=PostFx.program(PostFx.read("fullscreen.vsh"),FRAGMENT);vao=GlStateManager._glGenVertexArrays();sourceFbo=GlStateManager.glGenFramebuffers();}
            if(width!=main.width||height!=main.height){
                releaseTextures();width=main.width;height=main.height;
                int[] scene=PostFx.texture(width,height,GL11.GL_RGBA8,GL11.GL_UNSIGNED_BYTE),history=PostFx.texture(width,height,GL11.GL_RGBA8,GL11.GL_UNSIGNED_BYTE);
                sceneTex=scene[0];sceneFbo=scene[1];historyTex=history[0];historyFbo=history[1];reset();
            }
            if(level!=mc.level){level=mc.level;reset();}
            long now=System.nanoTime();float dt=lastNs==0?1f/60:Math.min(.25f,(now-lastNs)/1e9f);lastNs=now;
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER,sourceFbo);
            GlStateManager._glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,color.glId(),0);
            GlStateManager._disableScissorTest();
            if(previous){
                blit(sourceFbo,sceneFbo);
                GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,sourceFbo);
                GlStateManager._viewport(0,0,width,height);GlStateManager._disableDepthTest();GlStateManager._depthMask(false);GlStateManager._disableBlend();GlStateManager._disableCull();
                GL30.glBindVertexArray(vao);GL20.glUseProgram(program);
                for(int i=0;i<2;i++)GL33.glBindSampler(i,0);
                GlStateManager._activeTexture(GL13.GL_TEXTURE0);GlStateManager._bindTexture(sceneTex);
                GlStateManager._activeTexture(GL13.GL_TEXTURE1);GlStateManager._bindTexture(historyTex);
                GL20.glUniform1i(GL20.glGetUniformLocation(program,"uScene"),0);GL20.glUniform1i(GL20.glGetUniformLocation(program,"uHistory"),1);
                GL20.glUniform1f(GL20.glGetUniformLocation(program,"uWeight"),(float)Math.pow(Math.min(.95f,strength),dt*60));
                GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);
            }
            blit(sourceFbo,historyFbo);previous=true;frames++;
        }catch(Throwable error){errors++;failed=true;reset();ModuleManager.LOG.error("World motion blur failed",error);}
        finally{
            for(int i=1;i>=0;i--){GlStateManager._activeTexture(GL13.GL_TEXTURE0+i);GlStateManager._bindTexture(0);}
            GL20.glUseProgram(0);GlProgramCache.forget();GL30.glBindVertexArray(0);
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,read);GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,draw);
            GlStateManager._viewport(0,0,main.width,main.height);
        }
    }
    private static void blit(int from,int to){GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,from);GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,to);GlStateManager._glBlitFrameBuffer(0,0,width,height,0,0,width,height,GL11.GL_COLOR_BUFFER_BIT,GL11.GL_NEAREST);}
    private static void releaseTextures(){if(sceneTex!=0){GlStateManager._deleteTexture(sceneTex);GlStateManager._glDeleteFramebuffers(sceneFbo);}if(historyTex!=0){GlStateManager._deleteTexture(historyTex);GlStateManager._glDeleteFramebuffers(historyFbo);}sceneTex=sceneFbo=historyTex=historyFbo=0;}
}
