package dev.votex.client;
import dev.votex.config.*;
import dev.votex.performance.OptimizationCoordinator;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import java.util.*;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Renderable;
import dev.votex.ui.WorkspaceLayout;
import dev.votex.ui.WorkspaceWindow;
import dev.votex.ui.WorkspaceMotion;
import dev.votex.ui.HoverMotion;

/** Votex workspace: a real typographic glass interface, independent of vanilla menu widgets. */
public final class VotexScreen extends Screen implements VotexMenuSurface {
 private static final String[] PAGES={"MODULES","COSMETICS","SKINS","ANIMATIONS","FRIENDS","PERFORMANCE","SETTINGS"};
 private static final String[] NAMES={"Mod Menu","Garderobe","Skins","Emotes","Zusammen spielen","Performance","Einstellungen"};
 private static final String[] ICONS={"modules","cosmetics","skins","emotes","friends","performance","settings"};
 private final VotexClient core;private String page;private final AvatarPreview preview=new AvatarPreview();
 private long contentOpened;private float contentShift;private final dev.votex.ui.DampedSpring selection=new dev.votex.ui.DampedSpring(0);private boolean selectionReady;private long selectionSample=System.nanoTime();
 private int left,top,w,h,content,cw,rows,columns=2,category,wheelSlot,scrollViewportTop,scrollViewportBottom;private String query="",moduleCategory="Alle";private boolean savedLooks,categoryOpen;private long categoryOpened,lastScrollInput;private final List<GlassButton> categoryOptions=new ArrayList<>();
 private float uiScale=1;private int logicalWidth,logicalHeight;private EditBox search,retainedSearch;
 private WorkspaceWindow.Rect dragStart;private int windowEdge;private double dragX,dragY;
 private int panelHeight,popupY,popupStep=28;private boolean compact;
 private float pageScroll,scrollTarget;private long scrollSampled=System.nanoTime();
 private final List<Renderable> workspaceRenderables=new ArrayList<>();private List<Renderable> contentRenderables=List.of();private final Map<AbstractWidget,Integer> contentY=new IdentityHashMap<>();private final Set<AbstractWidget> pinnedContentWidgets=Collections.newSetFromMap(new IdentityHashMap<>()),pinnedContentHints=Collections.newSetFromMap(new IdentityHashMap<>());
 private final GlassTransition.Origin trigger=GlassTransition.origin();private long opened=System.nanoTime(),closing;private boolean workspaceTransition;
 private WorkspaceMotion.Frame displayedMotion=new WorkspaceMotion.Frame(1,0,0);
 private final HoverMotion panelInteraction=new HoverMotion();private long panelSampled=System.nanoTime();
 private List<ModuleRegistry.Module> matching=ModuleRegistry.MODULES;
 private List<VotexSocial.Peer> socialPeers=List.of();
 public VotexScreen(VotexClient core,String page){super(Component.literal("Votex Client"));this.core=core;this.page=page;}
 @Override public boolean isPauseScreen(){return false;}
 private boolean motion(){return core.modules.config().glass.motion(core.theme,core.modules.config().menuAnimations);}
 private <T extends AbstractWidget> T widget(T value){super.addRenderableWidget(value);workspaceRenderables.add(value);return value;}
 private GlassButton button(int x,int y,int width,int height,String name,String detail,java.util.function.BooleanSupplier selected,net.minecraft.client.gui.components.Button.OnPress action){return widget(new GlassButton(core,x,y,width,height,name,detail,selected,action));}
 private GlassButton action(int x,int y,int width,int height,String name,String icon,Runnable action){return button(x,y,width,height,name,"",()->false,b->action.run()).icon(icon);}
 private <T extends AbstractWidget> T pin(T widget){pinnedContentHints.add(widget);return widget;}
 private void open(String target){if(target.equals(page))return;if(!page.equals("HOME")&&!target.equals("HOME")){Wardrobe.releasePreviews();page=target;query="";categoryOpen=false;pageScroll=scrollTarget=0;contentOpened=System.nanoTime();contentShift=motion()?6:0;rebuildWidgets();return;}minecraft.gui.setScreen(new VotexScreen(core,target));}
 private void changed(){core.engine.saveModules(core.modules);}
 private static String searchText(String value){
  return java.text.Normalizer.normalize(value.toLowerCase(Locale.ROOT),java.text.Normalizer.Form.NFD)
    .replaceAll("\\p{M}+","").replace('ß','s').replace('_',' ').replace('-',' ');
 }
 private static String moduleAliases(String id){
  return switch(id){
   case "armor" -> "armor armour durability equipment ausrüstung haltbarkeit";
   case "durability" -> "tool held item durability haltbarkeit werkzeug";
   case "light" -> "licht lichtlevel blocklicht spawn monster";
   case "item_physics" -> "gegenstandsphysik dropped items fallen boden";
   case "effects" -> "potion status effects trank";
   case "keystrokes" -> "keys wasd keyboard eingabe";
   case "direction" -> "compass heading himmelsrichtung";
   case "ping" -> "latency ms verbindung netzwerk";
   case "fps","frametime" -> "performance frametime bildrate";
   case "cps" -> "click clicks per second maus";
   default -> "";
  };
 }
 private void filter(){
  String[] words=searchText(query).strip().split("\\s+");
  matching=ModuleRegistry.MODULES.stream().filter(module->{
   boolean category=moduleCategory.equals("Alle")
     ||moduleCategory.equals("Aktiv")&&core.modules.enabled(module.id())
     ||module.category().equals(moduleCategory);
   if(!category)return false;
   String haystack=searchText(module.name()+" "+module.description()+" "+module.id()+" "+module.category()+" "+moduleAliases(module.id()));
   for(String word:words)if(!haystack.contains(word))return false;
   return true;
  }).toList();
 }
 private int moduleHudWidth(){return Math.clamp(cw/3,48,130);}
 private int moduleSearchWidth(){return cw-moduleHudWidth()-12;}
 private void resetModuleFilters(){
  moduleCategory="Alle";pageScroll=scrollTarget=0;
  if(search!=null&&!search.getValue().isEmpty())search.setValue("");
  else rebuildSearch();
 }
 private void rebuildSearch(){
  EditBox previous=search;
  boolean focused=previous!=null&&previous.isFocused();
  retainedSearch=previous;
  try {rebuildWidgets();} finally {retainedSearch=null;}
  if(focused&&search!=null)setFocused(search);
 }
 private void search(int x,int y,int width,String hint){
  if(retainedSearch!=null){
   search=retainedSearch;
   search.setX(x+32);search.setY(y+13);search.setWidth(width-43);
   widget(search);
   return;
  }
  search=new EditBox(UiText.font(),x+32,y+13,width-43,20,UiText.component(hint));
  search.setMaxLength(80);search.setBordered(false);search.setTextShadow(false);
  search.setTextColor(VotexGlassDesignSystem.ink(core.theme.text()));
  search.setHint(UiText.component(hint));
  search.addFormatter((value,i)->UiText.component(value).getVisualOrderText());
  search.setValue(query);
  search.setResponder(value->{if(!value.equals(query)){query=value;pageScroll=scrollTarget=0;filter();rebuildSearch();}});
  widget(search);
 }
 @Override protected void init(){
  workspaceRenderables.clear();categoryOptions.clear();contentY.clear();pinnedContentWidgets.clear();pinnedContentHints.clear();contentRenderables=List.of();search=null;var layout=WorkspaceLayout.measure(width,height,minecraft.getWindow().getGuiScale());uiScale=layout.scale();logicalWidth=layout.logicalWidth();logicalHeight=layout.logicalHeight();var rect=WorkspaceWindow.restore(core.modules.config().workspaceWindow,logicalWidth,logicalHeight,new WorkspaceWindow.Rect((logicalWidth-layout.width())/2,(logicalHeight-layout.panelHeight())/2,layout.width(),layout.panelHeight()));w=rect.width();panelHeight=rect.height();h=page.equals("FRIENDS")?panelHeight:Math.max(540,panelHeight);compact=w<790||panelHeight<500;left=rect.x();top=rect.y();content=left+(compact?74:174);cw=w-(compact?74:174)-22;scrollViewportTop=top+60;scrollViewportBottom=top+panelHeight-12;pageScroll=Math.clamp(pageScroll,0,h-panelHeight);scrollTarget=Math.clamp(scrollTarget,0,h-panelHeight);
  displayedMotion=sampleMotion();
  if(page.equals("HOME")){home();return;}
  int step=compact?Math.min(43,Math.max(22,(panelHeight-150)/7)):43;
  for(int i=0;i<PAGES.length;i++){String target=PAGES[i];rail(button(left+12,top+(compact?66:89)+i*step,compact?48:142,compact?step-2:35,target.equals("FRIENDS")?"Freunde":NAMES[i],"",()->target.equals(page),b->open(target)).navigation().sharedSelection().icon(ICONS[i]));}
  rail(action(left+12,top+panelHeight-(compact?79:94),compact?48:142,compact?30:34,"Mods & Shader","cube",()->minecraft.gui.setScreen(new ModLibraryScreen(core,this))).navigation());
  rail(action(left+12,top+panelHeight-(compact?42:51),compact?48:142,compact?30:34,"Zurück zum Spiel","arrow",this::onClose).navigation());
  action(left+w-42,top+15,27,27,"","close",this::onClose).navigation();
  int firstContent=workspaceRenderables.size();switch(page){
   case "MODULES"->modules();case "COSMETICS","SKINS"->wardrobe();case "ANIMATIONS"->emotes();case "FRIENDS"->social();case "PERFORMANCE"->performance();case "SETTINGS"->settings();
  }
  contentRenderables=List.copyOf(workspaceRenderables.subList(firstContent,workspaceRenderables.size()));for(var r:contentRenderables)if(r instanceof AbstractWidget widget&&!categoryOptions.contains(widget)){int y=widget.getY();contentY.put(widget,y);if(pinnedContentHints.contains(widget)||isPinnedContent(y))pinnedContentWidgets.add(widget);}placeContent();
 }
 private GlassButton rail(GlassButton button){return compact?button.iconOnly():button;}
 private int homeY(){return Math.max(164,logicalHeight/2-Math.max(0,(540-logicalHeight)/4));}
 private boolean inContent(double x,double y){return x>=content&&x<=content+cw&&y>=top+60&&y<=top+panelHeight-12;}
 private boolean inScrollViewport(double x,double y){return x>=content&&x<=content+cw&&y>=scrollViewportTop&&y<=scrollViewportBottom;}
 private boolean clickVisibleContent(MouseButtonEvent event,boolean twice){
  event=contentPoint(event);
  var masked=new ArrayList<AbstractWidget>();
  for(var widget:contentY.keySet())if(widget.visible&&(!inContent(event.x(),event.y())||!pinnedContentWidgets.contains(widget)&&!inScrollViewport(event.x(),event.y()))){widget.visible=false;masked.add(widget);}
  try{return super.mouseClicked(event,twice);}finally{for(var widget:masked)widget.visible=true;}
 }
 private boolean isPinnedContent(int y){return switch(page){case "MODULES","COSMETICS","SKINS","ANIMATIONS"->y<top+150;default->false;};}
 private void placeContent(){int shift=Math.round(pageScroll);for(var entry:contentY.entrySet()){var widget=entry.getKey();boolean pinned=pinnedContentWidgets.contains(widget);int y=entry.getValue()-(pinned?0:shift);widget.setY(y);widget.visible=pinned?y+widget.getHeight()>top+60&&y<top+panelHeight-12:y+widget.getHeight()>scrollViewportTop&&y<scrollViewportBottom;}}
 private void stepScroll(){long now=System.nanoTime();float dt=Math.min(.1f,(now-scrollSampled)/1e9f);scrollSampled=now;pageScroll=motion()?pageScroll+(scrollTarget-pageScroll)*(1-(float)Math.exp(-dt*18)):scrollTarget;if(Math.abs(pageScroll-scrollTarget)<.05f)pageScroll=scrollTarget;placeContent();}
 private void home(){
  int cx=logicalWidth/2,cy=homeY();
  action(cx-185,cy+13,370,53,"Mod Menu öffnen","modules",()->open("MODULES")).primary();
  String[] targets={"COSMETICS","SKINS","ANIMATIONS","FRIENDS","PERFORMANCE","SETTINGS"},icons={"cosmetics","skins","emotes","friends","performance","settings"},labels={"Garderobe","Skins","Emotes","Freunde","Leistung","Design"};
  for(int i=0;i<targets.length;i++){String target=targets[i];action(cx-219+i*74,cy+82,68,62,"",icons[i],()->open(target));}
 }
 private void modules(){
  filter();
  search(content,top+69,moduleSearchWidth(),"Module suchen …");
  var hudButton=action(content+cw-moduleHudWidth(),top+69,moduleHudWidth(),35,
    "HUD gestalten","hud",()->minecraft.gui.setScreen(new HudEditorScreen(core)));
  if(moduleHudWidth()<108)hudButton.iconOnly();
  String[] categories={"Alle","Aktiv","HUD","Interface","Grafik"};
  int gap=5,filterWidth=(cw-gap*(categories.length-1))/categories.length;
  for(int i=0;i<categories.length;i++){
   String cat=categories[i];
   button(content+i*(filterWidth+gap),top+115,filterWidth,26,cat,"",()->moduleCategory.equals(cat),b->{
    moduleCategory=cat;pageScroll=scrollTarget=0;rebuildSearch();
   });
  }
  columns=cw>=500?2:1;
  int cardW=(cw-12*(columns-1))/columns;
  int totalRows=(matching.size()+columns-1)/columns;
  scrollViewportTop=top+170;scrollViewportBottom=top+panelHeight-12;
  h=WorkspaceLayout.flowPageHeight(panelHeight,panelHeight-12,174,totalRows,83,72);
  rows=Math.max(1,(panelHeight-12-174+82)/83);
  pageScroll=Math.clamp(pageScroll,0,h-panelHeight);scrollTarget=Math.clamp(scrollTarget,0,h-panelHeight);
  if(matching.isEmpty()){
   action(content,top+214,Math.min(cw,160),30,"Filter zurücksetzen","refresh",this::resetModuleFilters);
  }
  for(int n=0;n<matching.size();n++){
   var module=matching.get(n);
   int x=content+n%columns*(cardW+12),y=top+174+n/columns*83;
   button(x,y,cardW-40,72,module.name(),module.description(),()->core.modules.enabled(module.id()),b->{
    core.toggle(module.id());
    if(moduleCategory.equals("Aktiv"))rebuildSearch();
   }).icon(moduleIcon(module.id())).toggle();
   action(x+cardW-34,y,34,72,module.name()+" einstellen","settings",
     ()->minecraft.gui.setScreen(new ModuleSettingsScreen(core,this,module))).iconOnly();
  }
 }
 private String moduleIcon(String id){return switch(id){case "item_physics"->"cube";case "chat"->"chat";case "armor","durability"->"shield";case "direction","coordinates"->"globe";case "fps","frametime","memory"->"performance";default->"hud";};}
 private void wardrobe(){
  int available=compact?cw:cw-210;search(content,top+69,available,"Suchen …");if(!compact)action(content+available+12,top+69,198,35,"Launcher-Look verwenden","refresh",()->{Wardrobe.launcher(core);rebuildSearch();});
  if(page.equals("COSMETICS")){
   button(content,top+115,100,28,savedLooks?"Outfits":"Katalog","",()->savedLooks,b->{savedLooks=!savedLooks;pageScroll=scrollTarget=0;rebuildSearch();});
   button(content+110,top+115,Math.min(available-110,180),28,Wardrobe.SLOT_NAMES[category]+"  ⌄","",()->categoryOpen,b->{pageScroll=scrollTarget;categoryOpen=!categoryOpen;categoryOpened=System.nanoTime();rebuildSearch();if(categoryOpen)setFocused(categoryOptions.get(category));});
  }
  List<Wardrobe.Item> items=wardrobeItems();columns=Math.max(1,available/136);int gap=10,cardW=(available-gap*(columns-1))/columns;int totalRows=(items.size()+columns-1)/columns;scrollViewportTop=top+150;scrollViewportBottom=top+panelHeight-(compact?62:12);int cardH=Math.min(135,scrollViewportBottom-top-154),rowStep=cardH+gap;h=WorkspaceLayout.flowPageHeight(panelHeight,scrollViewportBottom-top,154,totalRows,rowStep,cardH);rows=Math.max(1,(scrollViewportBottom-top-154+rowStep-1)/rowStep);pageScroll=Math.clamp(pageScroll,0,h-panelHeight);scrollTarget=Math.clamp(scrollTarget,0,h-panelHeight);
  for(int n=0;n<items.size();n++){var item=items.get(n);widget(new WardrobeCard(core,content+n%columns*(cardW+gap),top+154+n/columns*rowStep,cardW,cardH,item,()->page.equals("SKINS")?item.id().equals(LocalLooks.assetSkinId()):Wardrobe.equipped(item),b->{if(page.equals("SKINS"))Wardrobe.skin(core,item);else if(savedLooks)Wardrobe.outfit(core,item);else Wardrobe.equip(core,item);}));}
  if(compact){boolean cosmetics=page.equals("COSMETICS");pin(action(content,top+panelHeight-50,cosmetics?cw-236:cw,38,"Deinen Look anpassen","settings",()->minecraft.gui.setScreen(new AppearanceScreen(core,this))));if(cosmetics){pin(button(content+cw-228,top+panelHeight-50,128,38,"Accessoires","",()->LocalLooks.visible,b->{LocalLooks.visible=!LocalLooks.visible;core.modules.config().cosmeticsVisible=LocalLooks.visible;changed();}).toggle());pin(button(content+cw-92,top+panelHeight-50,92,38,"Cape","",()->LocalLooks.capeVisible,b->{LocalLooks.capeVisible=!LocalLooks.capeVisible;core.modules.config().capeVisible=LocalLooks.capeVisible;changed();}).toggle());}}
  else{pin(action(content+available+12,top+panelHeight-160,198,32,"Deinen Look anpassen","settings",()->minecraft.gui.setScreen(new AppearanceScreen(core,this))));if(page.equals("COSMETICS")){pin(button(content+available+12,top+panelHeight-118,198,32,"Accessoires","",()->LocalLooks.visible,b->{LocalLooks.visible=!LocalLooks.visible;core.modules.config().cosmeticsVisible=LocalLooks.visible;changed();}).toggle());pin(button(content+available+12,top+panelHeight-77,198,32,"Cape","",()->LocalLooks.capeVisible,b->{LocalLooks.capeVisible=!LocalLooks.capeVisible;core.modules.config().capeVisible=LocalLooks.capeVisible;changed();}).toggle());}}
  if(categoryOpen){popupStep=compact?24:28;int ph=Wardrobe.SLOTS.length*popupStep+12;popupY=Math.clamp(top+151,top+60,Math.max(top+60,top+panelHeight-ph-12));for(int i=0;i<Wardrobe.SLOTS.length;i++){int choice=i;categoryOptions.add(button(content+116,popupY+6+i*popupStep,198,popupStep-2,Wardrobe.SLOT_NAMES[i],"",()->category==choice,b->{category=choice;categoryOpen=false;pageScroll=scrollTarget=0;rebuildSearch();}).navigation().emerge(popupY,categoryOpened));}}
 }
 private List<Wardrobe.Item> wardrobeItems(){List<Wardrobe.Item> source=page.equals("SKINS")?Wardrobe.skins():savedLooks?Wardrobe.looks():Wardrobe.cosmetics();return source.stream().filter(i->(page.equals("SKINS")||savedLooks||category==0||i.slot().equals(Wardrobe.SLOTS[category]))&&(i.name()+" "+i.description()).toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))).toList();}
 private void emotes(){
  int listW=compact?cw:cw-210;search(content,top+69,listW,"Emotes suchen …");if(!compact)action(content+cw-198,top+69,198,35,"Emote-Rad öffnen · B","emotes",()->minecraft.gui.setScreen(new EmoteWheel(core)));
  for(int i=0;i<8;i++){final int slot=i;int bw=(listW-7*5)/8;button(content+i*(bw+5),top+117,bw,28,Integer.toString(i+1),"",()->wheelSlot==slot,b->{wheelSlot=slot;rebuildSearch();});}
  var items=Wardrobe.emotes().stream().filter(e->(e.name()+" "+e.description()).toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))).toList();rows=Math.max(1,(panelHeight-(compact?104:12)-159+70)/71);columns=1;scrollViewportTop=top+150;scrollViewportBottom=top+panelHeight-(compact?104:12);h=WorkspaceLayout.flowPageHeight(panelHeight,scrollViewportBottom-top,159,items.size(),71,60);pageScroll=Math.clamp(pageScroll,0,h-panelHeight);scrollTarget=Math.clamp(scrollTarget,0,h-panelHeight);
  for(int n=0;n<items.size();n++){var item=items.get(n);int rowY=top+159+n*71;button(content,rowY,listW-110,60,item.name(),item.description(),()->LocalLooks.wheel[wheelSlot].equals(item.id()),b->{Wardrobe.slot(core,wheelSlot,item.id());rebuildSearch();}).icon("emotes");action(content+listW-100,rowY,100,60,"Testen","play",()->EmotePlayer.play(item.id()));}
  if(compact){int half=(cw-8)/2;pin(action(content,top+panelHeight-96,half,38,"Emote-Rad","emotes",()->minecraft.gui.setScreen(new EmoteWheel(core))));pin(action(content+half+8,top+panelHeight-96,half,38,"Beenden","close",EmotePlayer::stop));pin(button(content,top+panelHeight-50,half,38,"Tempo "+String.format(Locale.ROOT,"%.2f×",EmotePlayer.speed()),"",()->false,b->{Wardrobe.playback(core,EmotePlayer.speed()>=2?.5:EmotePlayer.speed()+.25,EmotePlayer.loop());rebuildSearch();}));pin(button(content+half+8,top+panelHeight-50,half,38,"Wiederholen","",EmotePlayer::loop,b->Wardrobe.playback(core,EmotePlayer.speed(),!EmotePlayer.loop())).toggle());}
  else{pin(action(content+cw-198,top+panelHeight-160,198,34,"Animation beenden","close",EmotePlayer::stop));pin(button(content+cw-198,top+panelHeight-117,198,34,"Tempo: "+String.format(Locale.ROOT,"%.2f×",EmotePlayer.speed()),"",()->false,b->{Wardrobe.playback(core,EmotePlayer.speed()>=2?.5:EmotePlayer.speed()+.25,EmotePlayer.loop());rebuildSearch();}));pin(button(content+cw-198,top+panelHeight-74,198,34,"Wiederholen","",EmotePlayer::loop,b->Wardrobe.playback(core,EmotePlayer.speed(),!EmotePlayer.loop())).toggle());}
 }
 private void social(){
  int y=top+78;var peers=VotexSocial.peers();socialPeers=peers;rows=Math.max(1,(panelHeight-194-78+56)/57);columns=1;scrollViewportTop=top+68;scrollViewportBottom=top+panelHeight-194;h=WorkspaceLayout.flowPageHeight(panelHeight,panelHeight-194,78,peers.size(),57,48);pageScroll=Math.clamp(pageScroll,0,h-panelHeight);scrollTarget=Math.clamp(scrollTarget,0,h-panelHeight);
  for(int n=0;n<peers.size();n++){var peer=peers.get(n);action(content,y+n*57,cw,48,peer.name()+"   ·   Handschlag anbieten","friends",()->VotexSocial.requestHandshake(peer.id()));}
  pin(action(content,top+panelHeight-154,(cw-12)/2,38,"Voice-Chat öffnen","voice",()->VotexSocial.openVoice()));var lan=pin(action(content+(cw+12)/2,top+panelHeight-154,(cw-12)/2,38,"Welt im LAN öffnen","globe",()->VotexSocial.openLanWorld(this)));lan.active=minecraft.getSingleplayerServer()!=null;if(!lan.active)lan.setTooltip(net.minecraft.client.gui.components.Tooltip.create(UiText.component("Öffne eine Einzelspielerwelt, um sie im LAN freizugeben.")));
  pin(action(content,top+panelHeight-106,(cw-12)/2,38,"Chat durchsuchen","chat",()->ChatTools.openSearch("")));pin(action(content+(cw+12)/2,top+panelHeight-106,(cw-12)/2,38,"Arcade öffnen","games",()->minecraft.gui.setScreen(new ArcadeScreen(core))));
 }
 private void performance(){
  int gap=12,bw=(cw-gap)/2,y=top+116;
  scrollViewportTop=top+108;scrollViewportBottom=top+panelHeight-78;h=WorkspaceLayout.flowPageHeight(panelHeight,panelHeight-78,116,1,1,229);pageScroll=Math.clamp(pageScroll,0,h-panelHeight);scrollTarget=Math.clamp(scrollTarget,0,h-panelHeight);
  button(content,y,bw,65,"Text-Cache","Begrenzte Breitenberechnung",()->core.engine.config.textCache,b->{core.engine.config.textCache=!core.engine.config.textCache;core.engine.save();}).icon("performance").toggle();
  button(content+bw+gap,y,bw,65,"Emote-Puffer","Wiederverwendbare Pose-Daten",()->core.engine.config.reusableMotion,b->{core.engine.config.reusableMotion=!core.engine.config.reusableMotion;core.engine.save();}).icon("emotes").toggle();
  button(content,y+78,cw,46,"Adaptive Votex-Effekte: "+core.engine.config.adaptive,"",()->core.engine.config.adaptive!=OptimizationCoordinator.Mode.DISABLED,b->{var modes=OptimizationCoordinator.Mode.values();core.engine.config.adaptive=modes[(core.engine.config.adaptive.ordinal()+1)%modes.length];core.engine.save();rebuildSearch();}).toggle();
  action(content,y+138,bw,42,"30 Sekunden messen","play",()->core.engine.start("manual-static",30));action(content+bw+gap,y+138,bw,42,"Messung beenden","check",()->core.engine.finish());
  action(content,y+192,bw,37,"JFR starten / stoppen","performance",()->core.engine.jfr());button(content+bw+gap,y+192,bw,37,"Ziel: "+core.engine.config.targetFps+" FPS","",()->false,b->{core.engine.config.targetFps=core.engine.config.targetFps>=240?60:core.engine.config.targetFps+30;core.engine.save();rebuildSearch();});
 }
 private void settings(){
  int y=top+77,bw=(cw-12)/2;
  h=Math.max(h,640);action(content,y,cw,40,"Liquid Glass gestalten","sun",()->minecraft.gui.setScreen(new GlassDesignScreen(core,this)));y+=52;
  button(content,y,cw,47,"Menü-Animationen","Weiches Öffnen, Schweben und Übergänge",()->core.modules.config().menuAnimations,b->{core.modules.config().menuAnimations=!core.modules.config().menuAnimations;changed();}).icon("emotes").toggle();
  button(content,y+59,cw,47,"Hintergrund weichzeichnen","Glasoberfläche über deiner Welt",()->core.modules.config().blurMenus,b->{core.modules.config().blurMenus=!core.modules.config().blurMenus;changed();}).icon("sun").toggle();
  action(content,y+122,bw,40,"HUD gestalten","hud",()->minecraft.gui.setScreen(new HudEditorScreen(core)));action(content+bw+12,y+122,bw,40,"HUD zurücksetzen","refresh",()->{core.modules.config().positions.clear();changed();});
  action(content,y+174,bw,40,"Minecraft-Steuerung","settings",()->minecraft.gui.setScreen(new net.minecraft.client.gui.screens.options.controls.ControlsScreen(this,minecraft.options)));action(content+bw+12,y+174,bw,40,"Chat öffnen","chat",()->minecraft.gui.setScreen(new net.minecraft.client.gui.screens.ChatScreen("",false)));
  action(content,y+228,cw,40,"Minecraft-Freunde öffnen","friends",()->VotexSocial.openFriends(this));
  var microphone=button(content,y+280,cw,47,"Mikrofonzugriff für Voice",VoiceMicGate.allowed()?"Ausschalten schließt das Mikrofongerät":"Erst nach deiner Zustimmung wird das Gerät geöffnet",VoiceMicGate::allowed,b->{if(VoiceMicGate.allowed()){VoiceMicGate.revoke();rebuildSearch();}else VoiceMicGate.open(this);}).icon("voice").toggle();microphone.active=VoiceMicGate.ready();
  if(StarterKeyBindings.conflict(minecraft)){var next=StarterKeyBindings.suggestion(minecraft);var repair=button(content,y+339,cw,47,"Emote-Tastenkonflikt lösen",next==null?"Bitte eine freie Taste in Minecraft-Steuerung wählen":"Nur Xaero-Wegpunkt ändern: "+next.getDisplayName().getString(),()->false,b->{StarterKeyBindings.repair(minecraft);rebuildSearch();}).icon("settings");repair.active=next!=null;}
  if(LootBrainBridge.installed())action(content,y+398,cw,40,"LootBrain-Einstellungen","cube",()->LootBrainBridge.open(core));
 }
 @Override public void tick(){if(closing!=0&&System.nanoTime()-closing>180_000_000L){minecraft.gui.setScreen(null);return;}if(closing==0&&windowEdge==0&&page.equals("FRIENDS")&&!socialPeers.equals(VotexSocial.peers()))rebuildWidgets();}
 @Override public void onClose(){if(windowEdge!=0){windowEdge=0;dragStart=null;changed();}if(!motion()){minecraft.gui.setScreen(null);return;}if(closing==0)closing=System.nanoTime();}
 @Override public void removed(){if(windowEdge!=0){windowEdge=0;dragStart=null;changed();}Wardrobe.releasePreviews();}
 @Override public void extractBackground(GuiGraphicsExtractor g,int x,int y,float delta){if(core.modules.config().blurMenus&&core.theme.blur())extractBlurredBackground(g);g.fill(0,0,width,height,page.equals("HOME")?0x0a040b16:0x0c040b16);}
 @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
  stepScroll();contentShift=motion()&&contentOpened!=0?(float)(6*Math.pow(1-Math.clamp((System.nanoTime()-contentOpened)/180_000_000d,0,1),3)):0;displayedMotion=sampleMotion();mx=(int)Math.round(localX(mx));my=(int)Math.round(localY(my));UiTooltip.begin();g.pose().pushMatrix();g.pose().scale(uiScale);
  float s=displayedMotion.scale(),tx=displayedMotion.x(),ty=displayedMotion.y();g.pose().translate(tx,ty);g.pose().scale(s);
  if(page.equals("HOME")){int cx=logicalWidth/2,cy=homeY();GlassRender.icon(g,"cloud",cx-25,cy-142,50,0xffa9d4ff);UiText.title(g,"VOTEX CLIENT",cx-Math.round(UiText.titleWidth("VOTEX CLIENT")*1.5f),cy-76,core.theme.text(),3);UiText.centered(g,"Dein Spiel. Dein Horizont.",cx,cy-31,0xffb7c9df);super.extractRenderState(g,mx,my,delta);String[] labels={"Garderobe","Skins","Emotes","Freunde","Leistung","Design"};for(int i=0;i<labels.length;i++)UiText.centered(g,labels[i],cx-185+i*74,cy+152,core.theme.muted());if(logicalHeight>365)UiText.centered(g,"Rechte Umschalttaste · Votex öffnen     B · Emotes",cx,logicalHeight-35,0xffb1c3d9);g.pose().popMatrix();return;}
  GlassRender.panel(g,left,top,w,panelHeight,core.theme);long lightNow=System.nanoTime();panelInteraction.step((lightNow-panelSampled)/1e9,false,true,mx,my,left,top,w,panelHeight,core.theme,VotexGlassDesignSystem.motion(core));panelSampled=lightNow;GlassRender.pointerLight(g,left,top,w,panelHeight,core.theme.radius(),core.theme,panelInteraction);for(int k=0;k<3;k++){g.fill(left+w-9-k*4,top+panelHeight-5,left+w-7-k*4,top+panelHeight-3-k*4,0x809db6d5);}int side=compact?62:162;GlassRender.round(g,left+1,top+1,side-1,panelHeight-2,Math.max(8,core.theme.radius()),0x18202c3f);g.fill(left+side,top+18,left+side+1,top+panelHeight-18,0x26b9d6ed);
  GlassRender.icon(g,"cloud",left+22,top+24,30,0xffa9d4ff);if(!compact){UiText.title(g,"votex",left+61,top+25,core.theme.text(),1.7f);UiText.draw(g,"CLIENT",left+63,top+48,0xff9db6d5);UiText.draw(g,"DEIN SPIEL",left+24,top+73,0xff748ba8);}
  long selectedNow=System.nanoTime();int selectedIndex=Math.max(0,Arrays.asList(PAGES).indexOf(page));if(!selectionReady||!motion()){selection.reset(selectedIndex);selectionReady=true;}else selection.step(selectedIndex,(selectedNow-selectionSample)/1e9);selectionSample=selectedNow;int navStep=compact?Math.min(43,Math.max(22,(panelHeight-150)/7)):43;GlassRender.round(g,left+12,top+(compact?66:89)+(int)Math.round(selection.value()*navStep),compact?48:142,compact?navStep-2:35,12,GlassRender.alpha(core.theme.accent(),.27));
  g.pose().pushMatrix();g.pose().translate(contentShift,0);UiText.title(g,title(),content,top+22,core.theme.text(),1.65f);UiText.draw(g,UiText.fit(subtitle(),cw-8),content,top+47,core.theme.muted());g.pose().popMatrix();
  for(var r:workspaceRenderables)if(!contentRenderables.contains(r))r.extractRenderState(g,mx,my,delta);
  g.pose().pushMatrix();g.pose().translate(contentShift,0);mx-=Math.round(contentShift);
  g.enableScissor(content,top+60,content+cw,top+panelHeight-12);contentDecoration(g);
  for(var r:contentRenderables)if(r instanceof AbstractWidget widget&&pinnedContentWidgets.contains(widget)&&widget.visible)r.extractRenderState(g,mx,my,delta);
  g.disableScissor();
  g.enableScissor(content,scrollViewportTop,content+cw,scrollViewportBottom);
  g.pose().pushMatrix();g.pose().translate(0,Math.round(pageScroll)-pageScroll);for(var r:contentRenderables)if(r instanceof AbstractWidget widget&&!pinnedContentWidgets.contains(widget)&&widget.visible)r.extractRenderState(g,inScrollViewport(mx,my)?mx:-100000,inScrollViewport(mx,my)?my:-100000,delta);g.pose().popMatrix();g.disableScissor();
  if(page.equals("MODULES")&&h>panelHeight&&System.nanoTime()-lastScrollInput<1_200_000_000L){int track=Math.max(1,scrollViewportBottom-scrollViewportTop),thumb=Math.max(30,Math.round(track*(float)track/h));int yy=scrollViewportTop+Math.round((track-thumb)*pageScroll/(h-panelHeight));float fade=Math.clamp(1-(System.nanoTime()-lastScrollInput-500_000_000L)/700_000_000f,0,1);GlassRender.round(g,content+cw-5,scrollViewportTop,2,track,1,((int)(18*fade)<<24)|0x8ab7d6);GlassRender.round(g,content+cw-5,yy,2,thumb,1,((int)(112*fade)<<24)|0x9bc9ef);}
  g.pose().popMatrix();g.pose().popMatrix();mx+=Math.round(contentShift);
  if(!compact&&minecraft.player!=null&&(page.equals("COSMETICS")||page.equals("SKINS")||page.equals("ANIMATIONS"))){
   int x1=Math.round(((content+cw-192+contentShift)*s+tx)*uiScale),y1=Math.round(((top+145)*s+ty)*uiScale),x2=Math.round(((content+cw-6+contentShift)*s+tx)*uiScale),y2=Math.round(((top+panelHeight-190)*s+ty)*uiScale);
   int clipTop=Math.round(((top+60)*s+ty)*uiScale),clipBottom=Math.round(((top+panelHeight-12)*s+ty)*uiScale);
   if(y2>clipTop&&y1<clipBottom&&y2>y1){g.enableScissor(Math.round((content*s+tx)*uiScale),clipTop,Math.round(((content+cw)*s+tx)*uiScale),clipBottom);preview.render(g,x1,y1,x2,y2);g.disableScissor();}
  }
  g.pose().pushMatrix();g.pose().scale(uiScale);g.pose().translate(tx,ty);g.pose().scale(s);
  if(categoryOpen){UiTooltip.begin();g.nextStratum();GlassRender.round(g,left,top,w,panelHeight,Math.max(8,core.theme.radius()),0x26040b16);GlassRender.popover(g,content+110,popupY,210,Wardrobe.SLOTS.length*popupStep+12,core.theme);for(var option:categoryOptions)option.extractRenderState(g,mx,my,delta);}
  g.pose().popMatrix();g.pose().pushMatrix();g.pose().scale(uiScale);UiTooltip.render(g,logicalWidth,logicalHeight);g.pose().popMatrix();
  int edge=windowEdge!=0?windowEdge:WorkspaceWindow.edge(new WorkspaceWindow.Rect(left,top,w,panelHeight),mx,my);if(edge==0&&my>=top+8&&my<=top+57&&mx>=left+8&&mx<left+w-50)edge=16;if(edge!=0){g.requestCursor(edge==16||((edge&3)!=0&&(edge&12)!=0)?com.mojang.blaze3d.platform.cursor.CursorTypes.RESIZE_ALL:(edge&3)!=0?com.mojang.blaze3d.platform.cursor.CursorTypes.RESIZE_EW:com.mojang.blaze3d.platform.cursor.CursorTypes.RESIZE_NS);}
 }
 private void contentDecoration(GuiGraphicsExtractor g){
  if(search!=null){int searchW=page.equals("MODULES")?moduleSearchWidth():compact?cw:cw-210;GlassRender.round(g,content,top+69,searchW,35,10,0x8e0b1524);GlassRender.border(g,content,top+69,searchW,35,10,search.isFocused()?0x8da5cdff:0x30bbd5ef);GlassRender.icon(g,"search",content+10,top+79,15,0xff9bb4cf);}
  if(page.equals("MODULES")){
   int filterIndex=Arrays.asList("Alle","Aktiv","HUD","Interface","Grafik").indexOf(moduleCategory);
   int filterWidth=(cw-20)/5;
   if(filterIndex>=0){int right=content+filterIndex*(filterWidth+5)+filterWidth-9;GlassRender.round(g,right-5,top+125,5,5,2,core.theme.accent());}
   long active=ModuleRegistry.MODULES.stream().filter(module->core.modules.enabled(module.id())).count();
   UiText.draw(g,UiText.fit(matching.size()+" von "+ModuleRegistry.MODULES.size()+" Modulen · "+active+" aktiv",cw-8),
     content,top+151,core.theme.muted());
   if(matching.isEmpty())UiText.draw(g,UiText.fit("Keine passenden Module",cw-8),content,top+185,core.theme.text());
  }
  if(page.equals("PERFORMANCE")){UiText.draw(g,String.format(Locale.ROOT,"Framezeit %.2f ms",core.engine.frameMs()),content,top+80,0xffb9dfff);GlassRender.round(g,content,top+panelHeight-68,cw,50,10,0x64101b2b);UiText.draw(g,UiText.fit(core.engine.status(),cw-20),content+10,top+panelHeight-59,core.theme.text());UiText.draw(g,UiText.fit(core.engine.bottleneck(),cw-20),content+10,top+panelHeight-39,core.theme.muted());}
  if(page.equals("FRIENDS")){UiText.draw(g,UiText.fit(VotexSocial.status(),cw),content,top+panelHeight-191,0xffb1c7e3);UiText.draw(g,UiText.fit(VotexSocial.voiceStatus(),cw),content,top+panelHeight-173,core.theme.muted());if(VotexSocial.peers().isEmpty()){boolean small=panelHeight<410;int icon=small?24:44,yy=top+(small?76:112);GlassRender.icon(g,"friends",content+(cw-icon)/2,yy,icon,0xff7f9dbf);UiText.centered(g,"Gemeinsam ist es schöner.",content+cw/2,small?yy+32:top+176,core.theme.text());UiText.centered(g,UiText.fit("Hier erscheinen Votex-Spieler auf deinem verbundenen Server.",cw-16),content+cw/2,small?yy+49:top+200,core.theme.muted());}}
  if(!compact&&(page.equals("COSMETICS")||page.equals("SKINS")||page.equals("ANIMATIONS"))){int ax=content+cw-198;GlassRender.round(g,ax,top+115,198,panelHeight-300,14,0x4c1b3047);UiText.centered(g,minecraft.player==null?"Dein Charakter":"DEIN LOOK",ax+99,top+128,0xff99b8dc);}
  if(page.equals("ANIMATIONS")&&!compact)UiText.draw(g,UiText.fit("Platz "+(wheelSlot+1)+" · "+EmotePlayer.label(LocalLooks.wheel[wheelSlot]),cw-16),content,top+panelHeight-64,0xffb1cef1);
  if(!core.lastError.isEmpty())UiText.draw(g,UiText.fit(core.lastError,cw),content,top+panelHeight-15,0xffff9aa9);
 }
 private String title(){for(int i=0;i<PAGES.length;i++)if(PAGES[i].equals(page))return NAMES[i];return "Votex Client";}
 private String subtitle(){return switch(page){case "MODULES"->"Deine Module. Genau so, wie du spielst.";case "COSMETICS"->"Kostenlose Modelle und deine eigenen Kreationen.";case "SKINS"->"Deine Skin-Bibliothek. Sofort anprobieren.";case "ANIMATIONS"->"Rad belegen, Gesten abspielen und Wiedergabe anpassen.";case "FRIENDS"->"Spieler, Handschläge und gemeinsame Momente.";case "PERFORMANCE"->"Lokale Messungen. Bewusste Einstellungen.";default->"Dein Votex. Dein Gefühl.";};}
 private WorkspaceMotion.Frame sampleMotion(){if(motion()&&closing==0&&trigger.valid()&&!page.equals("HOME"))return GlassTransition.frame(opened,700-core.modules.config().glass.speed*3,trigger,uiScale,left+w/2.,top+panelHeight/2.);return WorkspaceMotion.frame(System.nanoTime(),opened,closing,motion(),workspaceTransition,logicalWidth,logicalHeight,(700-core.modules.config().glass.speed*3));}
 private double localX(double x){return displayedMotion.localX(x/uiScale);}
 private double localY(double y){return displayedMotion.localY(y/uiScale);}
 private MouseButtonEvent contentPoint(MouseButtonEvent e){return inContent(e.x(),e.y())?new MouseButtonEvent(e.x()-contentShift,e.y(),e.buttonInfo()):e;}
 private MouseButtonEvent local(MouseButtonEvent e){return new MouseButtonEvent(localX(e.x()),localY(e.y()),e.buttonInfo());}
 @Override public boolean mouseClicked(MouseButtonEvent e,boolean twice){if(closing!=0)return true;if(!page.equals("HOME")&&e.button()==0){double x=localX(e.x()),y=localY(e.y());var rect=new WorkspaceWindow.Rect(left,top,w,panelHeight);windowEdge=WorkspaceWindow.edge(rect,x,y);if(windowEdge==0&&y>=top+8&&y<=top+57&&x>=left+8&&x<left+w-50)windowEdge=16;if(windowEdge!=0){dragStart=rect;dragX=x;dragY=y;categoryOpen=false;return true;}}if(categoryOpen){double x=localX(e.x()),y=localY(e.y());int popupTop=popupY;if(x>=content+110&&x<=content+320&&y>=popupTop&&y<=popupTop+Wardrobe.SLOTS.length*popupStep+12){for(var option:List.copyOf(categoryOptions))if(option.isMouseOver(x,y)){option.mouseClicked(local(e),twice);break;}return true;}categoryOpen=false;rebuildSearch();return true;}if(inContent(localX(e.x()),localY(e.y()))&&preview.click(e))return true;return clickVisibleContent(local(e),twice);}
 @Override public boolean mouseReleased(MouseButtonEvent e){if(windowEdge!=0){windowEdge=0;dragStart=null;changed();return true;}return preview.release()||super.mouseReleased(contentPoint(local(e)));}
 @Override public boolean mouseDragged(MouseButtonEvent e,double x,double y){if(windowEdge!=0&&dragStart!=null){var rect=WorkspaceWindow.drag(dragStart,windowEdge,(int)Math.round(localX(e.x())-dragX),(int)Math.round(localY(e.y())-dragY),logicalWidth,logicalHeight);core.modules.config().workspaceWindow=WorkspaceWindow.remember(rect,logicalWidth,logicalHeight);rebuildWidgets();return true;}return preview.drag(x,y)||super.mouseDragged(contentPoint(local(e)),x/(uiScale*displayedMotion.scale()),y/(uiScale*displayedMotion.scale()));}
 @Override public void mouseMoved(double x,double y){super.mouseMoved(localX(x),localY(y));}
 @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){if(categoryOpen)return true;if(!compact&&inContent(localX(x),localY(y))&&preview.scroll(x,y,vertical))return true;if(!page.equals("HOME")&&h>panelHeight&&inScrollViewport(localX(x),localY(y))){scrollTarget=Math.clamp(scrollTarget-(float)vertical*42,0,h-panelHeight);lastScrollInput=System.nanoTime();return true;}return super.mouseScrolled(localX(x),localY(y),horizontal,vertical);}
 @Override public boolean keyPressed(KeyEvent event){if(categoryOpen){if(event.key()==GLFW.GLFW_KEY_ESCAPE){categoryOpen=false;rebuildSearch();return true;}if(event.key()==GLFW.GLFW_KEY_DOWN||event.key()==GLFW.GLFW_KEY_UP){int current=categoryOptions.indexOf(getFocused());setFocused(categoryOptions.get(Math.floorMod(current+(event.key()==GLFW.GLFW_KEY_DOWN?1:-1),categoryOptions.size())));return true;}}if(page.equals("MODULES")&&search!=null){
  if(event.key()==GLFW.GLFW_KEY_F&&event.hasControlDown()){setFocused(search);return true;}
  if(event.key()==GLFW.GLFW_KEY_ESCAPE&&search.isFocused()&&!query.isEmpty()){search.setValue("");return true;}
 }if(event.key()==GLFW.GLFW_KEY_ESCAPE||core.menuKey.matches(event)){onClose();return true;}return super.keyPressed(event);}
}
