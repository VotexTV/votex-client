package dev.votex.client;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
import java.nio.file.Path;
import dev.votex.ui.WorkspaceLayout;
import dev.votex.ui.WorkspaceMotion;
/** Appearance controls, without exposing the launcher's geometry/animation authoring editor. */
public final class AppearanceScreen extends Screen implements VotexMenuSurface {
 private final VotexClient core;private final Screen parent;private final AvatarPreview preview=new AvatarPreview();private float scale;private int lw,lh,x,y,w,h,formWidth,fieldTop,scaleTop;private EditBox name;private boolean compact;
 private final long opened=System.nanoTime();private WorkspaceMotion.Frame displayedMotion=new WorkspaceMotion.Frame(1,0,0);
 public AppearanceScreen(VotexClient core,Screen parent){super(Component.literal("Dein Votex-Look"));this.core=core;this.parent=parent;}
 private GlassButton button(int xx,int yy,int ww,int hh,String text,Runnable run){return addRenderableWidget(new GlassButton(core,xx,yy,ww,hh,text,"",()->false,b->run.run()));}
 @Override protected void init(){String draft=name==null?LocalLooks.text("nametag","Votex Client"):name.getValue();var layout=WorkspaceLayout.measure(width,height,minecraft.getWindow().getGuiScale());scale=layout.scale();lw=layout.logicalWidth();lh=layout.logicalHeight();w=Math.min(760,lw-36);h=Math.min(470,lh-36);compact=h<440;formWidth=Math.min(332,(w-72)*3/5);fieldTop=compact?80:115;scaleTop=compact?150:254;x=(lw-w)/2;y=(lh-h)/2;button(x+20,y+20,100,30,"Zurück",this::onClose).icon("arrow");
  name=new EditBox(UiText.font(),x+32,y+fieldTop+9,formWidth-(compact?58:16),22,UiText.component("Nametag"));name.setBordered(false);name.setTextShadow(false);name.setTextColor(VotexGlassDesignSystem.ink(core.theme.text()));name.setMaxLength(48);name.setValue(draft);addRenderableWidget(name);
  var save=button(compact?x+24+formWidth-38:x+24,y+(compact?fieldTop:166),compact?38:formWidth,35,"Nametag speichern",()->Wardrobe.nametag(core,name.getValue())).icon("check");if(compact)save.iconOnly();
  button(x+24,y+scaleTop,54,32,"−",()->{Wardrobe.scale(core,Math.round(LocalLooks.scale()*100)-5);rebuildWidgets();});button(x+24+formWidth-54,y+scaleTop,54,32,"+",()->{Wardrobe.scale(core,Math.round(LocalLooks.scale()*100)+5);rebuildWidgets();});
  addRenderableWidget(new GlassButton(core,x+24,y+(compact?194:306),formWidth,compact?32:36,"Accessoires anzeigen","",()->LocalLooks.visible,b->{LocalLooks.visible=!LocalLooks.visible;core.modules.config().cosmeticsVisible=LocalLooks.visible;core.engine.saveModules(core.modules);})).toggle();
  addRenderableWidget(new GlassButton(core,x+24,y+(compact?236:352),formWidth,compact?32:36,"Cape anzeigen","",()->LocalLooks.capeVisible,b->{LocalLooks.capeVisible=!LocalLooks.capeVisible;core.modules.config().capeVisible=LocalLooks.capeVisible;core.engine.saveModules(core.modules);})).toggle();
  button(x+24,y+(compact?278:400),formWidth,compact?32:36,"Eigenes Cape importieren",this::importCape).icon("upload");
 }
 private void importCape(){try(var stack=MemoryStack.stackPush()){String path=TinyFileDialogs.tinyfd_openFileDialog("Cape-Bild auswählen",null,stack.pointers(stack.UTF8("*.png")),"PNG-Bild",false);if(path!=null)minecraft.gui.setScreen(new CapeCropScreen(core,this,Path.of(path)));}catch(Exception e){core.error("Cape-Bild konnte nicht geöffnet werden",e);}}
 @Override public boolean isPauseScreen(){return false;}
 @Override public void extractBackground(GuiGraphicsExtractor g,int mx,int my,float d){if(core.modules.config().blurMenus&&core.theme.blur())extractBlurredBackground(g);g.fill(0,0,width,height,0x85050c16);}
 @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float d){displayedMotion=WorkspaceMotion.frame(System.nanoTime(),opened,0,VotexGlassDesignSystem.motion(core),false,lw,lh,VotexGlassDesignSystem.transitionMillis(core));g.pose().pushMatrix();g.pose().scale(scale);g.pose().translate(displayedMotion.x(),displayedMotion.y());g.pose().scale(displayedMotion.scale());GlassRender.panel(g,x,y,w,h,core.theme);UiText.title(g,"Dein Look",x+140,y+26,core.theme.text(),1.6f);UiText.draw(g,"NAMETAG",x+24,y+fieldTop-22,core.theme.muted());GlassRender.round(g,x+24,y+fieldTop,formWidth,37,9,0x8c08121f);GlassRender.border(g,x+24,y+fieldTop,formWidth,37,9,name.isFocused()?0x99bbd7f0:0x46bbd7f0);UiText.draw(g,"KOSMETIK-GRÖSSE",x+24,y+scaleTop-(compact?20:30),core.theme.muted());UiText.centered(g,Math.round(LocalLooks.scale()*100)+" %",x+24+formWidth/2,y+scaleTop+10,core.theme.text());GlassRender.round(g,x+formWidth+48,y+83,w-formWidth-72,h-105,16,0x45233a53);super.extractRenderState(g,(int)Math.round(localX(mx)),(int)Math.round(localY(my)),d);g.pose().popMatrix();if(minecraft.player!=null)preview.render(g,screenX(x+formWidth+56),screenY(y+96),screenX(x+w-32),screenY(y+h-32));}
 private int screenX(double x){return (int)Math.round(displayedMotion.screenX(x)*scale);}private int screenY(double y){return (int)Math.round(displayedMotion.screenY(y)*scale);}
 private double localX(double x){return displayedMotion.localX(x/scale);}private double localY(double y){return displayedMotion.localY(y/scale);}
 private MouseButtonEvent local(MouseButtonEvent e){return new MouseButtonEvent(localX(e.x()),localY(e.y()),e.buttonInfo());}
 @Override public boolean mouseClicked(MouseButtonEvent e,boolean twice){if(preview.click(e))return true;return super.mouseClicked(local(e),twice);}@Override public boolean mouseReleased(MouseButtonEvent e){return preview.release()||super.mouseReleased(local(e));}@Override public boolean mouseDragged(MouseButtonEvent e,double dx,double dy){double factor=scale*displayedMotion.scale();return preview.drag(dx/factor,dy/factor)||super.mouseDragged(local(e),dx/factor,dy/factor);}
 @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){return preview.scroll(x,y,vertical)||super.mouseScrolled(localX(x),localY(y),horizontal,vertical);}
 @Override public void mouseMoved(double x,double y){super.mouseMoved(localX(x),localY(y));}
 @Override public void removed(){preview.release();}
 @Override public void onClose(){minecraft.gui.setScreen(parent);}
}
