package dev.votex.client;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.systems.*;
import com.mojang.blaze3d.textures.*;
import com.mojang.blaze3d.vertex.*;
import dev.votex.config.GlassConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;
import java.util.*;

/** Render-thread capture + two shared separable Gaussian passes. No GL, readback or per-widget FBO. */
public final class VotexGlassEngine {
 private static net.minecraft.resources.Identifier id(String path){return net.minecraft.resources.Identifier.fromNamespaceAndPath("votex_client",path);}
 private static final BindGroupLayout SCENE=BindGroupLayout.builder().withSampler("Sampler0").withSampler("Sampler1").build();
 public static final RenderPipeline GLASS=RenderPipeline.builder(RenderPipelines.GUI_SNIPPET).withLocation(id("glass/surface")).withVertexShader(id("core/glass")).withFragmentShader(id("core/glass")).withVertexBinding(0,DefaultVertexFormat.ENTITY).withBindGroupLayout(SCENE).build();
 private static final RenderPipeline LIGHT=RenderPipeline.builder(RenderPipelines.GUI_SNIPPET).withLocation(id("glass/interaction")).withVertexShader(id("core/glass_light")).withFragmentShader(id("core/glass_light")).withVertexBinding(0,DefaultVertexFormat.ENTITY).build();
 private static RenderPipeline blur(boolean horizontal){return RenderPipeline.builder().withLocation(id("glass/blur_"+(horizontal?"x":"y"))).withVertexShader(id("core/glass_blur")).withFragmentShader(id("core/glass_blur")).withShaderDefine(horizontal?"HORIZONTAL":"VERTICAL").withBindGroupLayout(BindGroupLayout.builder().withSampler("Sampler0").build()).withPrimitiveTopology(PrimitiveTopology.TRIANGLES).withColorTargetState(ColorTargetState.DEFAULT).withCull(false).build();}
 private static final RenderPipeline BLUR_X=blur(true),BLUR_Y=blur(false);
 private static TextureTarget scene,ping,blurred;
 private static boolean requested,ready,failed,compiled;private static int lastDivisor,idle;
 private static boolean benchmarking;private static long actualDraws,actualLightDraws;
 public static void benchmark(boolean value){benchmarking=value;}
 public static void recordDraw(RenderPipeline pipeline){if(pipeline==GLASS)actualDraws++;if(pipeline==LIGHT)actualLightDraws++;}
 private static long frames,copies,passes,allocations,surfaces,prepareNs,guiNs,gpuBlurNs=-1,gpuGuiNs=-1,started;
 private static final GpuQueryPool[] queries=new GpuQueryPool[4];private static int frameIndex;private static boolean queryUnavailable,queryActive;
 public static final dev.votex.ui.ClickRate clicks=new dev.votex.ui.ClickRate();
 private VotexGlassEngine(){}
 public static void interaction(GuiGraphicsExtractor g,int x,int y,int w,int h,int radius,dev.votex.config.ThemeConfig theme,dev.votex.ui.HoverMotion motion){
  var core=VotexClient.INSTANCE;if(!active()||w<12||h<12||w>4095||h>4095||!core.modules.config().glass.hover||core.modules.config().glass.reduceMotion||!theme.motion())return;
  float light=(float)Math.clamp(motion.near()*theme.effects().light()*.23,0,.23),reflection=(float)Math.clamp(motion.hover()*theme.effects().reflectionAmount()*.12,0,.12);
  if(light<.001&&reflection<.001)return;var pose=new Matrix3x2f(g.pose());var bounds=new ScreenRectangle(x,y,w,h).transformMaxBounds(pose);var clip=g.scissorStack.peek();if(clip!=null){bounds=bounds.intersection(clip);if(bounds==null)return;}
  int r=core.modules.config().glass.radius(w,h,radius);g.guiRenderState.addGuiElement(new LightState(pose,x,y,w,h,r,motion.x()*w,motion.y()*h,light,reflection,theme.effects().secondary(),clip,bounds));
 }
 private record LightState(Matrix3x2fc pose,int x,int y,int w,int h,int radius,float pointerX,float pointerY,float light,float reflection,int tint,ScreenRectangle scissorArea,ScreenRectangle bounds) implements GuiElementRenderState {
  public RenderPipeline pipeline(){return ready?LIGHT:RenderPipelines.GUI;}
  public TextureSetup textureSetup(){return TextureSetup.noTexture();}
  public void buildVertices(VertexConsumer v){vertex(v,x,y,0,0);vertex(v,x,y+h,0,h);vertex(v,x+w,y+h,w,h);vertex(v,x+w,y,w,0);}
  private void vertex(VertexConsumer v,float px,float py,float u,float t){v.addVertexWith2DPose(pose,px,py).setColor(ready?(tint&0xffffff)|0xff000000:0);if(ready)v.setUv(u,t).setUv1(w,h).setUv2(Math.round(pointerX*8),Math.round(pointerY*8)).setNormal(light,reflection,radius/64f);}
 }
 public static boolean active(){var core=VotexClient.INSTANCE;return core!=null&&core.modules!=null&&core.modules.config().glass.quality!=GlassConfig.Quality.OFF&&core.theme.effects().glassEffect()&&!failed;}
 public static boolean draw(GuiGraphicsExtractor g,int x,int y,int w,int h,int radius,int color,GlassConfig.Material material,double intensity){
  return draw(g,x,y,w,h,radius,color,material,intensity,false);
 }
 public static boolean lens(GuiGraphicsExtractor g,int x,int y,int size){return draw(g,x,y,size,size,size/2,0x98dceefa,GlassConfig.Material.ULTRA,1,true);}
 private static boolean draw(GuiGraphicsExtractor g,int x,int y,int w,int h,int radius,int color,GlassConfig.Material material,double intensity,boolean lens){
  if(!active()||w>4095||h>4095||w<16||h<12||color>>>24<20)return false;
  var core=VotexClient.INSTANCE;var shape=VotexGlassDesignSystem.surface(core.modules.config().glass,core.theme,material,w,h,radius,color,intensity);
  // A text-free lens needs no adaptive text backing. Keep the scene visible through it.
  if(lens&&!core.modules.config().glass.reduceTransparency)shape=new VotexGlassDesignSystem.Surface(GlassRender.alpha(shape.tint(),.16),shape.radius(),shape.packedRadiusMode()&~12288,(shape.packedIntensityBlur()&255)|((int)((shape.packedIntensityBlur()>>>8)*.35)<<8),shape.refract()*.7f,shape.reflect(),shape.thickness());
  var pose=new Matrix3x2f(g.pose());var bounds=new ScreenRectangle(x,y,w,h).transformMaxBounds(pose);var clip=g.scissorStack.peek();
  if(clip!=null){bounds=bounds.intersection(clip);if(bounds==null)return true;}
  g.guiRenderState.addGuiElement(new GlassState(pose,x,y,w,h,shape,clip,bounds));requested=true;surfaces++;return true;
 }
 private record GlassState(Matrix3x2fc pose,int x,int y,int w,int h,VotexGlassDesignSystem.Surface shape,ScreenRectangle scissorArea,ScreenRectangle bounds) implements GuiElementRenderState {
  public RenderPipeline pipeline(){return ready?GLASS:RenderPipelines.GUI;}
  public TextureSetup textureSetup(){return ready?TextureSetup.doubleTexture(scene.getColorTextureView(),sampler(),blurred.getColorTextureView(),sampler()):TextureSetup.noTexture();}
  public void buildVertices(VertexConsumer v){vertex(v,x,y,0,0);vertex(v,x,y+h,0,h);vertex(v,x+w,y+h,w,h);vertex(v,x+w,y,w,0);}
  private void vertex(VertexConsumer v,float px,float py,float u,float t){v.addVertexWith2DPose(pose,px,py).setColor(ready?shape.tint():0xed142132);if(ready)v.setUv(u,t).setUv1(w,h).setUv2(shape.packedRadiusMode(),shape.packedIntensityBlur()).setNormal(shape.refract(),shape.reflect(),shape.thickness());}
 }
 private static GpuSampler sampler(){return RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);}
 /** Called at GuiRenderer.render HEAD, after the actual world render, before any GUI draw. */
 public static void beginFrame(){
  started=System.nanoTime();ready=false;
  if(!requested||!active()){if(benchmarking){queryStart(RenderSystem.getDevice());queryMark(1);}else gpuBlurNs=gpuGuiNs=-1;prepareNs=guiNs=0;if(++idle>180&&scene!=null)releaseTargets();requested=false;return;}idle=0;
  try{
   var device=RenderSystem.getDevice();if(!compiled){if(!device.precompilePipeline(GLASS).isValid()||!device.precompilePipeline(LIGHT).isValid()||!device.precompilePipeline(BLUR_X).isValid()||!device.precompilePipeline(BLUR_Y).isValid())throw new IllegalStateException("Glass shader compilation rejected");compiled=true;}
   var target=Minecraft.getInstance().gameRenderer.mainRenderTarget();int divisor=VotexClient.INSTANCE.modules.config().glass.quality.divisor();
   if(scene==null||scene.width!=target.width||scene.height!=target.height||lastDivisor!=divisor||scene.getColorTexture().getFormat()!=target.getColorTexture().getFormat()){
    releaseTargets();lastDivisor=divisor;scene=new TextureTarget("Votex glass scene",target.width,target.height,false,target.getColorTexture().getFormat());ping=new TextureTarget("Votex glass blur x",Math.max(1,target.width/divisor),Math.max(1,target.height/divisor),false,target.getColorTexture().getFormat());blurred=new TextureTarget("Votex glass blur y",ping.width,ping.height,false,target.getColorTexture().getFormat());allocations+=3;
   }
   queryStart(device);var encoder=device.createCommandEncoder();encoder.copyTextureToTexture(target.getColorTexture(),scene.getColorTexture(),0,0,0,0,0,target.width,target.height);copies++;
   pass(encoder,BLUR_X,scene,ping);pass(encoder,BLUR_Y,ping,blurred);ready=true;frames++;queryMark(1);prepareNs=System.nanoTime()-started;
  }catch(Exception|LinkageError e){failed=true;ready=false;releaseTargets();VotexClient.LOG.warning("Liquid Glass GPU fallback: "+e.getClass().getSimpleName()+" · "+e.getMessage());}
 }
 private static void pass(CommandEncoder encoder,RenderPipeline pipeline,RenderTarget source,RenderTarget target){try(var pass=encoder.createRenderPass(()->"Votex shared Gaussian",target.getColorTextureView(),Optional.empty())){pass.setPipeline(pipeline);pass.bindTexture("Sampler0",source.getColorTextureView(),sampler());pass.draw(3,1,0,0);}passes++;}
 public static void endFrame(){if(ready||benchmarking){queryMark(2);guiNs=System.nanoTime()-started;frameIndex++;}requested=false;}
 private static void queryStart(GpuDevice device){queryActive=false;if(queryUnavailable)return;try{int index=frameIndex%4;var pool=queries[index];if(pool==null){pool=device.createTimestampQueryPool(3);queries[index]=pool;}else{var a=pool.getValue(0);var b=pool.getValue(1);var c=pool.getValue(2);if(a.isEmpty()||b.isEmpty()||c.isEmpty())return;gpuBlurNs=b.getAsLong()-a.getAsLong();gpuGuiNs=c.getAsLong()-a.getAsLong();}queryActive=true;queryMark(0);}catch(Exception|LinkageError e){queryUnavailable=true;closeQueries();}}
 private static void queryMark(int i){if(queryActive&&!queryUnavailable&&queries[frameIndex%4]!=null)try{RenderSystem.getDevice().createCommandEncoder().writeTimestamp(queries[frameIndex%4],i);}catch(Exception e){queryUnavailable=true;closeQueries();}}
 private static void closeQueries(){for(int i=0;i<queries.length;i++)if(queries[i]!=null){queries[i].close();queries[i]=null;}}
 private static void releaseTargets(){for(var target:new TextureTarget[]{scene,ping,blurred})if(target!=null)target.destroyBuffers();scene=ping=blurred=null;}
 public static void close(){benchmarking=false;ready=false;releaseTargets();closeQueries();compiled=false;failed=false;requested=false;}
 public static Map<String,Object> metrics(){return Map.ofEntries(Map.entry("actualLightDraws",actualLightDraws),Map.entry("actualGlassDraws",actualDraws),Map.entry("frames",frames),Map.entry("worldCopies",copies),Map.entry("blurPasses",passes),Map.entry("targetAllocations",allocations),Map.entry("surfaces",surfaces),Map.entry("prepareCpuNs",prepareNs),Map.entry("wholeGuiCpuNs",guiNs),Map.entry("sharedBlurGpuNs",gpuBlurNs),Map.entry("wholeGuiGpuNs",gpuGuiNs),Map.entry("failedFallback",failed),Map.entry("residentTargets",scene==null?0:3));}
}
