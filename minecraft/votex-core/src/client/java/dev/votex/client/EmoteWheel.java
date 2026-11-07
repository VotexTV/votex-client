package dev.votex.client;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import dev.votex.ui.EmoteWheelLayout;
import dev.votex.ui.HoverMotion;
/** Eight real skinned/posed previews, independent of the world player's live pose. */
public final class EmoteWheel extends Screen implements VotexMenuSurface {
 private final VotexClient core;private int selected=-1;private final long opened=System.nanoTime();
 private final String[] labels=new String[8];private final int[][] cards=new int[8][4];private float uiScale=1;private int logicalWidth,logicalHeight;private EmoteWheelLayout layout;
 private final AvatarPreview[] previews=java.util.stream.IntStream.range(0,8).mapToObj(i->new AvatarPreview()).toArray(AvatarPreview[]::new);private final AvatarPreview centerPreview=new AvatarPreview();
 private final HoverMotion[] hover=java.util.stream.IntStream.range(0,8).mapToObj(i->new HoverMotion()).toArray(HoverMotion[]::new);private long sampled=System.nanoTime();
 public EmoteWheel(VotexClient core){super(Component.literal("Votex Emotes"));this.core=core;for(int i=0;i<8;i++)labels[i]=EmotePlayer.label(LocalLooks.wheel[i]);}
 @Override protected void init(){layout=EmoteWheelLayout.measure(width,height,minecraft.getWindow().getGuiScale());uiScale=layout.scale();logicalWidth=layout.width();logicalHeight=layout.height();}
 @Override public boolean isPauseScreen(){return false;}
 @Override public void extractBackground(GuiGraphicsExtractor g,int x,int y,float d){g.fill(0,0,width,height,0x14050a14);}
 @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float d){
  int px=mx,py=my;selected=layout.selected(mx,my);mx=Math.round(mx/uiScale);my=Math.round(my/uiScale);int cx=layout.centerX(),cy=layout.centerY(),cardW=layout.cardWidth(),cardH=layout.cardHeight();UiTooltip.begin();
  long now=System.nanoTime();double dt=Math.min(.1,(now-sampled)/1e9);sampled=now;float enter=core.modules.config().glass.motion(core.theme,core.modules.config().menuAnimations)?(float)(1-Math.pow(1-Math.min(1,(now-opened)/(VotexGlassDesignSystem.transitionMillis(core)*.8*1_000_000d)),4)):1;
  g.pose().pushMatrix();g.pose().scale(uiScale);
  for(int i=0;i<8;i++){int left=layout.x(i,enter),top=layout.y(i,enter),x=left+cardW/2;boolean active=i==selected;cards[i][0]=left;cards[i][1]=top;cards[i][2]=cardW;cards[i][3]=cardH;
   hover[i].step(dt,active,true,mx,my,left,top,cardW,cardH,core.theme,VotexGlassDesignSystem.motion(core));GlassRender.round(g,left,top,cardW,cardH,14,GlassRender.surface(GlassRender.blend(0xa2142236,GlassRender.alpha(core.theme.accent(),.7),hover[i].hover()),core.theme));GlassRender.border(g,left,top,cardW,cardH,14,active?GlassRender.alpha(core.theme.accent(),.9):0x45b8d1ed);GlassRender.interaction(g,left,top,cardW,cardH,14,core.theme,hover[i]);GlassRender.round(g,left+9,top+8,19,19,8,active?GlassRender.alpha(core.theme.accent(),.7):0x493e5b7c);UiText.centered(g,Integer.toString(i+1),left+18,top+13,0xffebf6ff);UiText.centered(g,UiText.fit(labels[i],cardW-16),x,top+cardH-19,active?core.theme.text():0xffaec4de);
  }
  GlassRender.round(g,cx-64,layout.labelY(),128,25,10,0xc014263c);UiText.centered(g,selected<0?"Dein Moment":UiText.fit(labels[selected],115),cx,layout.labelY()+8,core.theme.text());if(selected>=0&&UiText.width(labels[selected])>115)UiTooltip.request(labels[selected],mx,my);UiText.centered(g,"B loslassen · 1–8 wählen · ESC zurück",cx,logicalHeight-33,core.theme.muted());g.pose().popMatrix();
  if(minecraft.player!=null){double seconds=(System.nanoTime()-opened)/1e9;for(int i=0;i<8;i++){int[] c=cards[i];int x1=Math.round((c[0]+12)*uiScale),y1=Math.round((c[1]+8)*uiScale),x2=Math.round((c[0]+c[2]-12)*uiScale),y2=Math.round((c[1]+c[3]-24)*uiScale);String id=LocalLooks.wheel[i];var preview=previews[i];EmotePlayer.withPreview(id,seconds%Math.max(1,dev.votex.motion.EmoteTimeline.duration(id)),()->preview.render(g,x1,y1,x2,y2));}
   if(minecraft.options.getCameraType().isFirstPerson()){int x1=Math.round((cx-53)*uiScale),y1=Math.round((cy-78)*uiScale),x2=Math.round((cx+53)*uiScale),y2=Math.round((layout.labelY()-8)*uiScale);String id=selected<0?"":LocalLooks.wheel[selected];EmotePlayer.withPreview(id,seconds,()->centerPreview.render(g,x1,y1,x2,y2));}
  }g.pose().pushMatrix();g.pose().scale(uiScale);UiTooltip.render(g,logicalWidth,logicalHeight);g.pose().popMatrix();
 }
 private void play(){if(selected>=0&&minecraft.player!=null)EmotePlayer.play(LocalLooks.wheel[selected]);onClose();}
 @Override public boolean mouseClicked(MouseButtonEvent e,boolean twice){if(e.button()==0){selected=layout.selected(e.x(),e.y());for(int i=0;i<cards.length;i++){int[] card=cards[i];double x=e.x()/uiScale,y=e.y()/uiScale;if(card[2]>0&&x>=card[0]&&x<card[0]+card[2]&&y>=card[1]&&y<card[1]+card[3]){selected=i;break;}}play();return true;}return super.mouseClicked(e,twice);}
 @Override public boolean keyReleased(KeyEvent e){if(core.emoteKey.matches(e)){play();return true;}return super.keyReleased(e);}
 @Override public boolean keyPressed(KeyEvent e){if(e.key()>=GLFW.GLFW_KEY_1&&e.key()<=GLFW.GLFW_KEY_8){selected=e.key()-GLFW.GLFW_KEY_1;play();return true;}if(e.key()==GLFW.GLFW_KEY_ESCAPE){onClose();return true;}return super.keyPressed(e);}
}
