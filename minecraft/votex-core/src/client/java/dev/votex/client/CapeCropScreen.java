package dev.votex.client;
import dev.votex.appearance.CapeAtlas;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.platform.NativeImage;
import java.nio.file.*;
import java.awt.image.BufferedImage;
import java.awt.RenderingHints;
import javax.imageio.ImageIO;
import java.util.UUID;
import dev.votex.ui.WorkspaceLayout;
import dev.votex.ui.WorkspaceMotion;

/** Crop a portrait motif or import a finished Minecraft atlas; the source stays untouched. */
public final class CapeCropScreen extends Screen implements VotexMenuSurface {
 private final VotexClient core;private final Screen parent;private final BufferedImage source;private final Identifier texture;
 private float uiScale;private int lw,lh,x,y,w,h,fx,fy,fw,fh;private double zoom=1,cx=.5,cy=.5;private boolean dragging,readyAtlas;private String error="";
 private final long opened=System.nanoTime();private WorkspaceMotion.Frame displayedMotion=new WorkspaceMotion.Frame(1,0,0);
 public CapeCropScreen(VotexClient core,Screen parent,Path file)throws Exception{super(Component.literal("Cape zuschneiden"));this.core=core;this.parent=parent;if(Files.size(file)>16_000_000)throw new IllegalArgumentException("Das PNG darf höchstens 16 MB groß sein");
  try(var input=ImageIO.createImageInputStream(file.toFile())){var readers=ImageIO.getImageReaders(input);if(!readers.hasNext())throw new IllegalArgumentException("Das Bild ist kein PNG");var reader=readers.next();try{reader.setInput(input);int width=reader.getWidth(0),height=reader.getHeight(0);if(!reader.getFormatName().equalsIgnoreCase("png")||width<2||height<2||width>4096||height>4096)throw new IllegalArgumentException("PNG-Größe muss zwischen 2 und 4096 Pixeln liegen");source=reader.read(0);}finally{reader.dispose();}}
  byte[] bytes=Files.readAllBytes(file);NativeImage image=NativeImage.read(bytes);texture=Identifier.fromNamespaceAndPath("votex_client","cape-crop/"+UUID.randomUUID());net.minecraft.client.Minecraft.getInstance().getTextureManager().register(texture,new DynamicTexture(()->"Votex cape crop",image));
 }
 @Override public boolean isPauseScreen(){return false;}
 @Override protected void init(){var layout=WorkspaceLayout.measure(width,height,minecraft.getWindow().getGuiScale());uiScale=layout.scale();lw=layout.logicalWidth();lh=layout.logicalHeight();w=Math.min(760,lw-36);h=Math.min(510,lh-36);x=(lw-w)/2;y=(lh-h)/2;double aspect=readyAtlas?2:10d/16;fh=Math.min(h-224,(int)((w-64)/aspect));fw=(int)Math.round(fh*aspect);fx=x+(w-fw)/2;fy=y+128;
  button(x+24,y+20,100,30,"Zurück",this::onClose);button(x+32,y+68,(w-76)/2,32,"Bildmotiv",()->{readyAtlas=false;zoom=1;rebuildWidgets();});button(x+44+(w-76)/2,y+68,(w-76)/2,32,"Fertige Cape-Textur",()->{readyAtlas=true;zoom=1;rebuildWidgets();});button(x+w-200,y+h-54,176,33,"Cape übernehmen",this::save).primary();button(x+24,y+h-54,54,33,"−",()->zoom=Math.clamp(zoom/1.15,1,8));button(x+86,y+h-54,54,33,"+",()->zoom=Math.clamp(zoom*1.15,1,8));
 }
 private GlassButton button(int xx,int yy,int ww,int hh,String title,Runnable action){return addRenderableWidget(new GlassButton(core,xx,yy,ww,hh,title,"",()->false,b->action.run()));}
 private CapeAtlas.Crop crop(){return CapeAtlas.crop(source.getWidth(),source.getHeight(),readyAtlas?2:10d/16,zoom,cx,cy);} private double cropW(){return crop().width();}
 private double left(){return crop().x();}private double upper(){return crop().y();}
 private void save(){try{if(!Wardrobe.canImportCape())throw new IllegalStateException("Die Cape-Bibliothek enthält bereits 128 Bilder");BufferedImage cape=CapeAtlas.create(source,crop(),readyAtlas);var out=new java.io.ByteArrayOutputStream();if(!ImageIO.write(cape,"png",out))throw new IllegalStateException("PNG-Encoder fehlt");String id="ingame-"+UUID.randomUUID();Path target=core.directory.resolve("assets/cape-"+id+".png");Files.createDirectories(target.getParent());Path temp=target.resolveSibling(target.getFileName()+".tmp");try{Files.write(temp,out.toByteArray(),StandardOpenOption.CREATE_NEW);try{Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){Files.move(temp,target);}}finally{Files.deleteIfExists(temp);}Wardrobe.importedCape(core,id);onClose();}catch(Exception e){error="Cape konnte nicht gespeichert werden: "+e.getMessage();}}
 @Override public void extractBackground(GuiGraphicsExtractor g,int mx,int my,float d){if(core.modules.config().blurMenus&&core.theme.blur())extractBlurredBackground(g);g.fill(0,0,width,height,0x48050b14);}
 @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float d){displayedMotion=WorkspaceMotion.frame(System.nanoTime(),opened,0,VotexGlassDesignSystem.motion(core),false,lw,lh,VotexGlassDesignSystem.transitionMillis(core));g.pose().pushMatrix();g.pose().scale(uiScale);g.pose().translate(displayedMotion.x(),displayedMotion.y());g.pose().scale(displayedMotion.scale());GlassRender.panel(g,x,y,w,h,core.theme);UiText.title(g,"Dein Cape",x+140,y+27,core.theme.text(),1.6f);UiText.draw(g,UiText.fit("Bild ziehen · Mausrad zum Vergrößern · Ausgabe 64 × 32 Pixel",w-64),x+32,y+108,core.theme.muted());
  for(int row=0;row<(fh+15)/16;row++)for(int col=0;col<(fw+15)/16;col++)g.fill(fx+col*16,fy+row*16,Math.min(fx+fw,fx+(col+1)*16),Math.min(fy+fh,fy+(row+1)*16),(row+col)%2==0?0xff162233:0xff233247);
  g.enableScissor(fx,fy,fx+fw,fy+fh);g.pose().pushMatrix();float factor=(float)(fw/cropW());g.pose().translate(fx-(float)left()*factor,fy-(float)upper()*factor);g.pose().scale(factor);g.blit(RenderPipelines.GUI_TEXTURED,texture,0,0,0,0,source.getWidth(),source.getHeight(),source.getWidth(),source.getHeight());g.pose().popMatrix();g.disableScissor();GlassRender.border(g,fx,fy,fw,fh,8,0xff8cb8ec);UiText.draw(g,UiText.fit(error.isEmpty()?"Dein Bild wird lokal importiert. Mit verbundenem Votex-Relay sehen es Mitspieler.":error,w-64),x+32,y+h-81,error.isEmpty()?core.theme.muted():0xffffa0ae);super.extractRenderState(g,(int)Math.round(localX(mx)),(int)Math.round(localY(my)),d);g.pose().popMatrix();}
 private double localX(double x){return displayedMotion.localX(x/uiScale);}private double localY(double y){return displayedMotion.localY(y/uiScale);}
 private MouseButtonEvent local(MouseButtonEvent e){return new MouseButtonEvent(localX(e.x()),localY(e.y()),e.buttonInfo());}
 private boolean inCrop(double x,double y){return x>=fx&&x<fx+fw&&y>=fy&&y<fy+fh;}
 @Override public boolean mouseClicked(MouseButtonEvent e,boolean twice){var point=local(e);if(super.mouseClicked(point,twice))return true;if(e.button()==0&&inCrop(point.x(),point.y())){dragging=true;return true;}return false;}
 @Override public boolean mouseDragged(MouseButtonEvent e,double dx,double dy){double factor=uiScale*displayedMotion.scale();if(dragging){cx=Math.clamp(cx-dx/factor*cropW()/fw/source.getWidth(),0,1);cy=Math.clamp(cy-dy/factor*cropW()/fw/source.getHeight(),0,1);return true;}return super.mouseDragged(local(e),dx/factor,dy/factor);}
 @Override public boolean mouseReleased(MouseButtonEvent e){boolean was=dragging;dragging=false;return super.mouseReleased(local(e))||was;}
 @Override public boolean mouseScrolled(double mx,double my,double horizontal,double vertical){double x=localX(mx),y=localY(my);if(!inCrop(x,y))return super.mouseScrolled(x,y,horizontal,vertical);zoom=Math.clamp(zoom*Math.pow(1.15,vertical),1,8);return true;}
 @Override public void mouseMoved(double x,double y){super.mouseMoved(localX(x),localY(y));}
 @Override public void removed(){minecraft.getTextureManager().release(texture);source.flush();}
 @Override public void onClose(){minecraft.gui.setScreen(parent);}
}
