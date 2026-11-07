package dev.votex.client;
import dev.votex.ui.ChatLayout;
import dev.votex.ui.WorkspaceMotion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
/** Local actions only. Reply prepares an unsent /msg; message objects and signatures remain untouched. */
public final class ChatContextScreen extends Screen implements VotexMenuSurface {
 private final VotexClient core;private final GuiMessage message;private final String draft;private final Screen parent;private final double anchorX,anchorY;
 private ChatLayout.Context layout;private int left,top;private float scale=1;private String name;private final long opened=System.nanoTime();private WorkspaceMotion.Frame displayedMotion=new WorkspaceMotion.Frame(1,0,0);
 public ChatContextScreen(VotexClient core,GuiMessage message,String draft,int x,int y){this(core,message,draft,x,y,null);}
 public ChatContextScreen(VotexClient core,GuiMessage message,String draft,int x,int y,Screen parent){super(Component.literal("Nachricht"));this.core=core;this.message=message;this.draft=draft;this.parent=parent;var window=Minecraft.getInstance().getWindow();anchorX=x/(double)window.getGuiScaledWidth();anchorY=y/(double)window.getGuiScaledHeight();}
 @Override public boolean isPauseScreen(){return false;}
 private boolean motion(){return VotexGlassDesignSystem.motion(core);}
 @Override protected void init(){
  if(parent!=null)parent.resize(width,height); // Density can change even when GUI coordinate dimensions are identical.
  layout=ChatLayout.context(width,height,minecraft.getWindow().getGuiScale(),anchorX*width,anchorY*height);scale=layout.scale();left=layout.left();top=layout.top();name=ChatTools.sender(message);int y=top+63,w=layout.width()-24;
  addRenderableWidget(new GlassButton(core,left+12,y,w,32,"Nachricht kopieren","",()->false,b->{minecraft.keyboardHandler.setClipboard(message.content().getString());onClose();}));
  var copyName=addRenderableWidget(new GlassButton(core,left+12,y+38,w,32,"Name kopieren","",()->false,b->{minecraft.keyboardHandler.setClipboard(name);onClose();}));copyName.active=!name.isEmpty();
  var reply=addRenderableWidget(new GlassButton(core,left+12,y+76,w,32,name.isEmpty()?"Kein Spielername erkannt":"An "+name+" schreiben","",()->false,b->minecraft.gui.setScreen(new ChatScreen("/msg "+name+" ",false))));reply.active=!name.isEmpty();
  addRenderableWidget(new GlassButton(core,left+12,y+114,w,32,"Verlauf durchsuchen","",()->false,b->ChatTools.openSearch(draft)).icon("search"));
  addRenderableWidget(new GlassButton(core,left+layout.width()-37,top+14,23,23,"Zurück","",()->false,b->onClose()).icon("close").iconOnly().navigation());
 }
 private double localX(double x){return displayedMotion.localX(x/scale);}private double localY(double y){return displayedMotion.localY(y/scale);}
 private MouseButtonEvent local(MouseButtonEvent e){return new MouseButtonEvent(localX(e.x()),localY(e.y()),e.buttonInfo());}
 @Override public void extractBackground(GuiGraphicsExtractor g,int x,int y,float d){if(parent!=null)parent.extractBackground(g,x,y,d);else if(core.modules.config().blurMenus&&core.theme.blur())extractBlurredBackground(g);g.fill(0,0,width,height,0x38060b12);}
 @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float d){
  if(parent!=null)parent.extractRenderState(g,-1,-1,d);displayedMotion=WorkspaceMotion.frame(System.nanoTime(),opened,0,motion(),false,layout.viewportWidth(),layout.viewportHeight(),VotexGlassDesignSystem.transitionMillis(core));int mx=(int)Math.round(localX(x)),my=(int)Math.round(localY(y));
  g.nextStratum();g.pose().pushMatrix();g.pose().scale(scale);g.pose().translate(displayedMotion.x(),displayedMotion.y());g.pose().scale(displayedMotion.scale());GlassRender.popover(g,left,top,layout.width(),layout.height(),core.theme);UiText.title(g,"Nachricht",left+16,top+16,core.theme.text(),1.4f);UiText.draw(g,UiText.fit(name.isEmpty()?"Aktionen für diese Nachricht":"Aktionen · "+name,layout.width()-32),left+16,top+40,core.theme.muted());super.extractRenderState(g,mx,my,d);g.pose().popMatrix();
 }
 @Override public boolean mouseClicked(MouseButtonEvent e,boolean twice){var point=local(e);if(!layout.contains(point.x(),point.y())&&(e.button()==0||e.button()==1)){onClose();return true;}return super.mouseClicked(point,twice);}
 @Override public boolean mouseReleased(MouseButtonEvent e){return super.mouseReleased(local(e));}
 @Override public boolean mouseDragged(MouseButtonEvent e,double dx,double dy){return super.mouseDragged(local(e),dx/(scale*displayedMotion.scale()),dy/(scale*displayedMotion.scale()));}
 @Override public void mouseMoved(double x,double y){super.mouseMoved(localX(x),localY(y));}
 @Override public boolean keyPressed(KeyEvent e){if(e.key()==GLFW.GLFW_KEY_F&&e.hasControlDown()){ChatTools.openSearch(draft);return true;}return super.keyPressed(e);}
 @Override public void onClose(){minecraft.gui.setScreen(parent==null?new ChatScreen(draft,false):parent);}
}
