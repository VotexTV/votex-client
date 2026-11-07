package dev.votex.client;
import dev.votex.chat.DuplicateGroups;
import dev.votex.ui.ChatLayout;
import dev.votex.ui.WorkspaceMotion;
import java.util.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;
/** Bounded original history, responsive glass presentation, no mutation of signed messages. */
public final class ChatHistoryScreen extends Screen implements VotexMenuSurface {
 private record Row(GuiMessage message,List<FormattedCharSequence> lines,boolean truncated){}
 private final VotexClient core;private final String draft;private EditBox search;private String query="";
 private List<Row> rows=List.of();private List<GuiMessage> lastMessages=List.of();
 private ChatLayout.History layout;private float scale=1;private int left,top,panelWidth,panelHeight;
 private double scroll,scrollTarget,scrollGrab;private boolean draggingScroll;private long sampled=System.nanoTime(),opened=sampled;
 private WorkspaceMotion.Frame displayedMotion=new WorkspaceMotion.Frame(1,0,0);
 public ChatHistoryScreen(VotexClient core,String draft){super(Component.literal("Votex Chat Studio"));this.core=core;this.draft=draft==null?"":draft;}
 @Override public boolean isPauseScreen(){return false;}
 private boolean motion(){return VotexGlassDesignSystem.motion(core);}
 @Override protected void init(){
  layout=ChatLayout.history(width,height,minecraft.getWindow().getGuiScale());scale=layout.scale();left=layout.left();top=layout.top();panelWidth=layout.width();panelHeight=layout.height();
  search=addRenderableWidget(new EditBox(UiText.font(),left+48,top+75,panelWidth-83,20,UiText.component("Nachrichten suchen")));
  search.setBordered(false);search.setTextShadow(false);search.setMaxLength(128);search.setHint(UiText.component("Nachrichten oder Namen durchsuchen …"));search.setTextColor(VotexGlassDesignSystem.ink(core.theme.text()));search.addFormatter((value,index)->UiText.component(value).getVisualOrderText());search.setValue(query);
  search.setResponder(value->{query=value;scroll=scrollTarget=0;filter(false);});setInitialFocus(search);
  addRenderableWidget(new GlassButton(core,left+panelWidth-104,top+18,86,30,"Zurück","",()->false,b->onClose()).icon("arrow_left"));
  addRenderableWidget(new GlassButton(core,left+18,top+panelHeight-44,182,30,"Duplikate bündeln","",()->core.modules.config().chatStackDuplicates,b->{core.modules.config().chatStackDuplicates=!core.modules.config().chatStackDuplicates;core.engine.saveModules(core.modules);}).toggle());filter(true);
 }
 private void filter(boolean preserveAnchor){
  int anchorIndex=rows.isEmpty()?0:Math.min(rows.size()-1,(int)(scroll/ChatLayout.ROW_STEP));GuiMessage anchor=preserveAnchor&&scroll>0&&!rows.isEmpty()?rows.get(anchorIndex).message():null;
  lastMessages=ChatTools.history();var result=new ArrayList<Row>();
  for(var message:lastMessages)if(DuplicateGroups.matches(message.content().getString(),query)){var wrapped=UiText.font().split(UiText.presentation(message.content()),Math.max(30,layout.listWidth()-38));result.add(new Row(message,List.copyOf(wrapped.subList(0,Math.min(2,wrapped.size()))),wrapped.size()>2));}rows=List.copyOf(result);
  if(anchor!=null)for(int i=0;i<rows.size();i++)if(rows.get(i).message()==anchor){double shift=(i-anchorIndex)*ChatLayout.ROW_STEP;scroll+=shift;scrollTarget+=shift;break;}
  scroll=Math.clamp(scroll,0,layout.maxScroll(rows.size()));scrollTarget=Math.clamp(scrollTarget,0,layout.maxScroll(rows.size()));
 }
 @Override public void tick(){super.tick();if(!lastMessages.equals(ChatTools.history()))filter(true);}
 private double localX(double x){return displayedMotion.localX(x/scale);}private double localY(double y){return displayedMotion.localY(y/scale);}
 private MouseButtonEvent local(MouseButtonEvent e){return new MouseButtonEvent(localX(e.x()),localY(e.y()),e.buttonInfo());}
 @Override public void extractBackground(GuiGraphicsExtractor g,int x,int y,float d){if(core.modules.config().blurMenus&&core.theme.blur())extractBlurredBackground(g);g.fill(0,0,width,height,0x48070c16);}
 @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float d){
  long now=System.nanoTime();double dt=Math.min(.1,(now-sampled)/1e9);sampled=now;scroll=motion()?scroll+(scrollTarget-scroll)*(1-Math.exp(-dt*20)):scrollTarget;if(Math.abs(scrollTarget-scroll)<.05)scroll=scrollTarget;
  displayedMotion=WorkspaceMotion.frame(now,opened,0,motion(),false,layout.viewportWidth(),layout.viewportHeight(),VotexGlassDesignSystem.transitionMillis(core));int x=(int)Math.round(localX(mx)),y=(int)Math.round(localY(my));
  g.pose().pushMatrix();g.pose().scale(scale);g.pose().translate(displayedMotion.x(),displayedMotion.y());g.pose().scale(displayedMotion.scale());
  GlassRender.panel(g,left,top,panelWidth,panelHeight,core.theme);UiText.title(g,"Chat Studio",left+21,top+19,core.theme.text(),1.6f);UiText.draw(g,"Nachrichten finden · Kopieren · Antworten",left+22,top+45,core.theme.muted());
  GlassRender.round(g,left+18,top+65,panelWidth-36,36,10,0xa6091424);GlassRender.border(g,left+18,top+65,panelWidth-36,36,10,search.isFocused()?core.theme.accent():0x407d9cbd);GlassRender.icon(g,"search",left+28,top+76,15,core.theme.muted());
  g.enableScissor(layout.listLeft(),layout.listTop(),layout.listLeft()+layout.listWidth(),layout.listTop()+layout.listHeight());int first=Math.max(0,(int)(scroll/ChatLayout.ROW_STEP));
  for(int i=first;i<rows.size();i++){int yy=(int)Math.round(layout.listTop()+i*ChatLayout.ROW_STEP-scroll);if(yy>=layout.listTop()+layout.listHeight())break;var row=rows.get(i);boolean hover=layout.rowAt(x,y,scroll,rows.size())==i;
   GlassRender.round(g,layout.listLeft(),yy,layout.listWidth(),ChatLayout.ROW_HEIGHT,10,hover?0x9b345574:0x62223650);int lineY=yy+10;for(var line:row.lines()){g.text(UiText.font(),line,layout.listLeft()+13,lineY,VotexGlassDesignSystem.ink(core.theme.text()),false);lineY+=15;}if(row.truncated())UiText.draw(g,"…",layout.listLeft()+layout.listWidth()-20,yy+25,core.theme.muted());}
  if(rows.isEmpty())UiText.centered(g,"Keine passenden Nachrichten",left+panelWidth/2,layout.listTop()+24,core.theme.muted());g.disableScissor();
  if(layout.maxScroll(rows.size())>0){GlassRender.round(g,left+panelWidth-18,layout.listTop(),5,layout.listHeight(),2,0x243b516e);GlassRender.round(g,left+panelWidth-18,(int)Math.round(layout.thumbY(scroll,rows.size())),5,layout.thumbHeight(rows.size()),2,0x8cb2d7f7);}
  UiText.draw(g,UiText.fit(rows.size()+" Treffer · Rechtsklick für Aktionen",panelWidth-235),left+215,top+panelHeight-33,core.theme.muted());super.extractRenderState(g,x,y,d);g.pose().popMatrix();
 }
 @Override public boolean mouseClicked(MouseButtonEvent event,boolean twice){var e=local(event);if(super.mouseClicked(e,twice))return true;
  if(e.button()==0&&layout.maxScroll(rows.size())>0&&e.x()>=left+panelWidth-23&&e.x()<left+panelWidth-8&&e.y()>=layout.listTop()&&e.y()<layout.listTop()+layout.listHeight()){
   draggingScroll=true;double thumb=layout.thumbY(scroll,rows.size());scrollGrab=e.y()>=thumb&&e.y()<thumb+layout.thumbHeight(rows.size())?e.y()-thumb:layout.thumbHeight(rows.size())/2d;scroll=scrollTarget=layout.scrollFromThumb(e.y(),scrollGrab,rows.size());return true;}
  int index=layout.rowAt(e.x(),e.y(),scroll,rows.size());if(index>=0&&(e.button()==0||e.button()==1)){minecraft.gui.setScreen(new ChatContextScreen(core,rows.get(index).message(),draft,(int)event.x(),(int)event.y(),this));return true;}return false;
 }
 @Override public boolean mouseReleased(MouseButtonEvent e){if(draggingScroll){draggingScroll=false;return true;}return super.mouseReleased(local(e));}
 @Override public boolean mouseDragged(MouseButtonEvent e,double dx,double dy){if(draggingScroll){scroll=scrollTarget=layout.scrollFromThumb(localY(e.y()),scrollGrab,rows.size());return true;}return super.mouseDragged(local(e),dx/(scale*displayedMotion.scale()),dy/(scale*displayedMotion.scale()));}
 @Override public void mouseMoved(double x,double y){super.mouseMoved(localX(x),localY(y));}
 @Override public boolean mouseScrolled(double x,double y,double dx,double dy){if(!layout.inList(localX(x),localY(y)))return false;scrollTarget=Math.clamp(scrollTarget-dy*ChatLayout.ROW_STEP*1.6,0,layout.maxScroll(rows.size()));return true;}
 @Override public boolean keyPressed(KeyEvent e){if(e.key()==GLFW.GLFW_KEY_F&&e.hasControlDown()){setFocused(search);return true;}return super.keyPressed(e);}
 @Override public void onClose(){draggingScroll=false;minecraft.gui.setScreen(new ChatScreen(draft,false));}
}
