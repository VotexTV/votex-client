package dev.votex.client;
import dev.votex.config.ModuleRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.phys.HitResult;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Enabled labels refresh at 5 Hz; rendering reuses text, measured widths and positions. */
final class VotexHud {
 private static final DateTimeFormatter CLOCK=DateTimeFormatter.ofPattern("HH:mm");
 private static final EquipmentSlot[] ARMOR={EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET};
 private static final String[] ARMOR_NAMES={"Kopf","Brust","Beine","Füße"};
 private static final int ARMOR_WIDTH=122,ARMOR_HEIGHT=102;
 private final VotexClient core;
 private final boolean defaultMinimapPresent=net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("xaerominimap");
 private final String[] labels=new String[ModuleRegistry.HUD_MODULES.size()];
 private final int[] widths=new int[labels.length];
 private final ModuleRegistry.Appearance defaultAppearance=new ModuleRegistry.Appearance();
 private static final int HISTORY_SIZE=24;
 private final float[] fpsHistory=new float[HISTORY_SIZE], frameHistory=new float[HISTORY_SIZE];
 private int historyHead,historyCount;
 private net.minecraft.client.multiplayer.ClientLevel movementWorld;
 private net.minecraft.client.player.LocalPlayer movementPlayer;
 private net.minecraft.world.phys.Vec3 previousPosition;
 private double horizontalSpeed,verticalSpeed;
 private long clockTicks;
 private int generation;private long refresh,joined;private net.minecraft.client.multiplayer.ClientPacketListener sessionConnection;private java.util.UUID player;private String language="";
 VotexHud(VotexClient core){this.core=core;java.util.Arrays.fill(labels,"");}
 void tick(Minecraft mc){
  sampleMovement(mc);
  if(mc.player==null){player=null;sessionConnection=null;return;}
  long now=System.nanoTime();if(!mc.player.getUUID().equals(player)||sessionConnection!=mc.getConnection()){player=mc.player.getUUID();sessionConnection=mc.getConnection();joined=now;refresh=0;historyHead=historyCount=0;}if(now-refresh<200_000_000L)return;refresh=now;
  if(!language.equals(mc.options.languageCode)){language=mc.options.languageCode;VotexTextCache.clear();}
  if(generation!=VotexTextCache.generation){java.util.Arrays.fill(labels,"");generation=VotexTextCache.generation;}
  if(core.modules.enabled("fps")||core.modules.enabled("frametime")){
   fpsHistory[historyHead]=mc.getFps();
   frameHistory[historyHead]=(float)core.engine.frameMs();
   historyHead=(historyHead+1)%HISTORY_SIZE;
   historyCount=Math.min(HISTORY_SIZE,historyCount+1);
  }else{historyHead=historyCount=0;}
  var p=mc.player;var pos=p.blockPosition();var level=mc.level;
  clockTicks=level==null?0:Math.floorMod(level.getOverworldClockTime()+6000,24000);
  for(int i=0;i<labels.length;i++){String id=ModuleRegistry.HUD_MODULES.get(i).id();if(!core.modules.enabled(id))continue;String next=switch(id){
   case "fps"->mc.getFps()+" FPS";
   case "cps"->"";
   case "frametime"->String.format(Locale.ROOT,"%.2f ms",core.engine.frameMs());
   case "clock"->LocalTime.now().format(CLOCK);
   case "memory"->{var rt=Runtime.getRuntime();yield (rt.totalMemory()-rt.freeMemory())/1048576+" / "+rt.maxMemory()/1048576+" MB";}
   case "keystrokes"->"";
   case "health"->String.format(Locale.ROOT,"♥ %.1f / %.1f%s",p.getHealth()/2,p.getMaxHealth()/2,p.getAbsorptionAmount()>0?" + "+p.getAbsorptionAmount()/2:"");
   case "food"->"Nahrung "+p.getFoodData().getFoodLevel()+" / 20 · Sättigung "+Math.round(p.getFoodData().getSaturationLevel());
   case "experience"->"Level "+p.experienceLevel+" · "+Math.round(p.experienceProgress*100)+" %";
   case "biome"->level==null?"—":level.getBiome(pos).unwrapKey().map(k->biomeName(k.identifier())).orElse("Unbekannt");
   case "dimension"->level==null?"—":dimensionName(level.dimension().identifier());
   case "day"->level==null?"—":Long.toString(Math.floorDiv(level.getOverworldClockTime(),24000)+1);
   case "session"->{long s=(now-joined)/1_000_000_000;yield String.format(Locale.ROOT,"%02d:%02d:%02d",s/3600,s/60%60,s%60);}
   case "players"->mc.getConnection()==null?"—":Integer.toString(mc.getConnection().getListedOnlinePlayers().size());
   case "weather"->{
    if(level==null)yield "—";
    if(!level.dimensionType().hasSkyLight())yield "Kein Himmelswetter";
    if(!level.isRaining())yield "Klarer Himmel";
    var precipitation=level.getBiome(pos).value().getPrecipitationAt(pos,level.getSeaLevel());
    yield switch(precipitation){
     case SNOW -> "Schneefall";
     case RAIN -> level.isThundering()?"Gewitter":"Regen";
     default -> "Trockenes Biom";
    };
   }
   case "air"->"Atemluft "+Math.round(100f*Math.max(0,p.getAirSupply())/Math.max(1,p.getMaxAirSupply()))+" %";
   default->"";};
   if(!next.equals(labels[i])){labels[i]=next;widths[i]=UiText.width(next)+26;}
  }
 }
 private void sampleMovement(Minecraft mc){
  var current=mc.player;
  boolean needed=core.modules.enabled("speed")||core.modules.enabled("movement");
  if(!needed||current==null||mc.level==null){
   movementWorld=null;movementPlayer=null;previousPosition=null;
   horizontalSpeed=verticalSpeed=0;return;
  }
  var position=current.position();
  if(movementWorld!=mc.level||movementPlayer!=current||previousPosition==null){
   horizontalSpeed=verticalSpeed=0;
  }else{
   var delta=position.subtract(previousPosition);
   if(delta.lengthSqr()>144){horizontalSpeed=verticalSpeed=0;}
   else{
    horizontalSpeed+=(delta.horizontalDistance()*20-horizontalSpeed)*.30;
    verticalSpeed+=(delta.y*20-verticalSpeed)*.30;
    if(Math.abs(horizontalSpeed)<.04)horizontalSpeed=0;
    if(Math.abs(verticalSpeed)<.04)verticalSpeed=0;
   }
  }
  movementWorld=mc.level;movementPlayer=current;previousPosition=position;
 }
 private static String human(String s){String words=s.replace('_',' ');return words.isEmpty()?s:Character.toUpperCase(words.charAt(0))+words.substring(1);}
 private static String biomeName(net.minecraft.resources.Identifier id){
  String key="biome."+id.getNamespace()+"."+id.getPath().replace('/','.');
  return net.minecraft.network.chat.Component.translatableWithFallback(key,human(id.getPath())).getString();
 }
 private static String dimensionName(net.minecraft.resources.Identifier id){
  if(id.getNamespace().equals("minecraft"))return switch(id.getPath()){
   case "overworld" -> "Oberwelt";
   case "the_nether" -> "Nether";
   case "the_end" -> "Das Ende";
   default -> human(id.getPath());
  };
  return human(id.getPath())+" · "+id.getNamespace();
 }
 ModuleRegistry.Position position(int index){
  var config=core.modules.config();
  var window=Minecraft.getInstance().getWindow();
  int viewportWidth=window.getGuiScaledWidth(),viewportHeight=window.getGuiScaledHeight();
  var saved=config.positions.get(id(index));
  if(saved!=null)return new ModuleRegistry.Position(
   dev.votex.config.HudLayouts.resolve(saved.x,saved.viewWidth,viewportWidth,width(index)),
   dev.votex.config.HudLayouts.resolve(saved.y,saved.viewHeight,viewportHeight,height(index)));
  if(isArmor(index))return new ModuleRegistry.Position(Math.max(4,viewportWidth-width(index)-12),Math.max(4,viewportHeight-height(index)-12));
  int startY=Math.clamp(config.y,4,Math.max(4,viewportHeight-36));
  int y=startY,offset=Math.max(4,config.x),columnWidth=0;
  for(int i=0;i<=index;i++){
   if(i!=index&&(!core.modules.enabled(id(i))||isArmor(i)||config.positions.containsKey(id(i))))continue;
   int w=width(i),h=height(i);
   if(y!=startY&&y+h>viewportHeight-12){offset+=columnWidth+10;y=startY;columnWidth=0;}
   if(i==index)return new ModuleRegistry.Position(defaultMinimapPresent?viewportWidth-offset-w:offset,y);
   y+=h+6;columnWidth=Math.max(columnWidth,w);
  }
  throw new IllegalStateException("Missing HUD module");
 }

 ModuleRegistry.Appearance appearance(int index){return core.modules.config().appearance.getOrDefault(ModuleRegistry.HUD_MODULES.get(index).id(),defaultAppearance);}
 float scale(int index){return core.modules.config().scale/100f*appearance(index).scale/100f;}
 private boolean isArmor(int index){return ModuleRegistry.HUD_MODULES.get(index).id().equals("armor");}
 private String id(int index){return ModuleRegistry.HUD_MODULES.get(index).id();}
 private static boolean meter(String id){
  return switch(id){case "health","food","experience","air","memory","durability" -> true;default -> false;};
 }
 private int baseWidth(int index){
  return switch(id(index)){
   case "armor" -> ARMOR_WIDTH;
   case "keystrokes","fps","frametime","cps" -> 106;
   case "durability","target" -> 136;
   case "direction","effects","coordinates" -> 152;
   case "speed","movement","world_time" -> 132;
   case "clock","day","session","players" -> 110;
   case "biome","dimension","weather","chunk" -> 144;
   case "ping" -> 108;
   default -> Math.max(65,widths[index]);
  };
 }
 private int baseHeight(int index){
  return switch(id(index)){
   case "armor" -> ARMOR_HEIGHT;
   case "keystrokes" -> 72;
   case "fps","frametime","cps","target","speed","coordinates","world_time","movement" -> 36;
   case "clock","biome","dimension","day","session","players","weather" -> 36;
   case "direction","chunk" -> 46;
   case "effects" -> 81;
   default -> meter(id(index))?28:22;
  };
 }
 int width(int i){return Math.round(baseWidth(i)*scale(i));}
 int height(int i){return Math.round(baseHeight(i)*scale(i));}
 void render(GuiGraphicsExtractor g,boolean editing){
  var mc=Minecraft.getInstance();
  if(mc.player==null||(!editing&&mc.gui.hud.isHidden()))return;
  long start=core.engine.timer(),now=System.nanoTime();
  for(int i=0;i<labels.length;i++){
   String id=id(i);
   if(!core.modules.enabled(id))continue;
   var p=position(i);var a=appearance(i);
   int w=Math.min(width(i),g.guiWidth()-8),h=height(i);
   if(w<=0)continue;
   int x=Math.clamp(p.x,4,Math.max(4,g.guiWidth()-w-4));
   int y=Math.clamp(p.y,4,Math.max(4,g.guiHeight()-h-4));
   float scale=scale(i);int localW=Math.round(w/scale);
   g.pose().pushMatrix();
   g.pose().translate(x,y);
   g.pose().scale(scale,scale);
   if(isArmor(i))armor(g,mc,localW,a,editing);
   else{
    surface(g,localW,baseHeight(i),a,editing);
    switch(id){
     case "keystrokes" -> keys(g,mc,localW);
     case "cps" -> clicks(g,localW,now);
     case "fps","frametime" -> history(g,localW,id.equals("fps"),label(i));
     case "durability" -> tool(g,mc,localW);
     case "direction" -> compass(g,mc,localW);
     case "effects" -> effects(g,mc,localW);
     case "target" -> target(g,mc,localW);
     case "ping" -> connection(g,mc,localW);
     case "coordinates" -> coordinates(g,mc,localW);
     case "speed" -> speed(g,localW);
     case "world_time" -> worldTime(g,localW);
     case "movement" -> movement(g,mc,localW);
     case "clock" -> info(g,localW,"Lokale Uhrzeit",label(i),"sun");
     case "biome" -> info(g,localW,"Biom",label(i),"globe");
     case "dimension" -> info(g,localW,"Dimension",label(i),"globe");
     case "day" -> info(g,localW,"Welttag",label(i),"sun");
     case "session" -> info(g,localW,"Spielzeit",label(i),"performance");
     case "players" -> info(g,localW,"Spielerliste",label(i),"friends");
     case "weather" -> info(g,localW,"Wetter im Biom",label(i),label(i).equals("Klarer Himmel")?"sun":"cloud");
     case "chunk" -> chunk(g,mc,localW);
     default -> {
      if(a.accent)g.fill(6,8,8,14,core.theme.accent());
      UiText.draw(g,UiText.fit(label(i),Math.max(0,localW-21)),13,7,core.theme.text());
      if(meter(id))meterBar(g,6,21,localW-12,progress(mc,id),core.theme.accent());
     }
    }
   }
   g.pose().popMatrix();
  }
  core.engine.hud(start);
 }
 private String label(int i){return labels[i].isEmpty()?ModuleRegistry.HUD_MODULES.get(i).name():labels[i];}
 private void surface(GuiGraphicsExtractor g,int width,int height,ModuleRegistry.Appearance style,boolean editing){
  int bg=((int)(style.background*2.55)<<24)|0x0c1422;
  if(!VotexGlassEngine.draw(g,0,0,width,height,10,bg,dev.votex.config.GlassConfig.Material.CLEAR,style.glassIntensity/100.))
   GlassRender.round(g,0,0,width,height,10,bg);
  if(editing)GlassRender.border(g,0,0,width,height,10,core.theme.accent());
 }
 private static void meterBar(GuiGraphicsExtractor g,int x,int y,int width,float value,int color){
  if(width<=0)return;
  g.fill(x,y,x+width,y+2,0x66475c74);
  int filled=Math.round(width*Math.clamp(value,0f,1f));
  if(filled>0)g.fill(x,y,x+filled,y+2,color);
 }
 private static float progress(Minecraft mc,String id){
  var player=mc.player;
  return switch(id){
   case "health" -> player.getHealth()/Math.max(1,player.getMaxHealth());
   case "food" -> player.getFoodData().getFoodLevel()/20f;
   case "experience" -> player.experienceProgress;
   case "air" -> (float)player.getAirSupply()/Math.max(1,player.getMaxAirSupply());
   case "memory" -> {var rt=Runtime.getRuntime();yield (float)(rt.totalMemory()-rt.freeMemory())/Math.max(1L,rt.maxMemory());}
   default -> 0;
  };
 }
 private void keys(GuiGraphicsExtractor g,Minecraft mc,int width){
  int cell=(width-20)/3;
  if(cell<8)return;
  key(g,8+cell+2,6,cell,18,mc.options.keyUp);
  key(g,8,26,cell,18,mc.options.keyLeft);
  key(g,8+cell+2,26,cell,18,mc.options.keyDown);
  key(g,8+(cell+2)*2,26,cell,18,mc.options.keyRight);
  key(g,8,47,width-16,17,mc.options.keyJump);
 }
 private void key(GuiGraphicsExtractor g,int x,int y,int width,int height,net.minecraft.client.KeyMapping binding){
  boolean pressed=binding.isDown();
  int bg=pressed?GlassRender.alpha(core.theme.accent(),.74):0x70425470;
  GlassRender.round(g,x,y,width,height,4,bg);
  String name=UiText.fit(binding.getTranslatedKeyMessage().getString(),width-4);
  UiText.centered(g,name,x+width/2,y+4,pressed?GlassRender.foreground(bg,core.theme):core.theme.text());
 }
 private void clicks(GuiGraphicsExtractor g,int width,long now){
  int half=width/2;
  UiText.centered(g,"Links",half/2,5,core.theme.muted());
  UiText.centered(g,"Rechts",half+half/2,5,core.theme.muted());
  UiText.centered(g,VotexGlassEngine.clicks.count(0,now)+" CPS",half/2,19,core.theme.text());
  UiText.centered(g,VotexGlassEngine.clicks.count(1,now)+" CPS",half+half/2,19,core.theme.text());
  g.fill(half,6,half+1,29,0x55475c74);
 }
 private void history(GuiGraphicsExtractor g,int width,boolean fps,String label){
  UiText.draw(g,UiText.fit(label,Math.max(0,width-14)),7,5,core.theme.text());
  float[] values=fps?fpsHistory:frameHistory;
  float ceiling=fps?60:16.67f;
  for(int n=0;n<historyCount;n++)ceiling=Math.max(ceiling,values[(historyHead+HISTORY_SIZE-1-n)%HISTORY_SIZE]);
  int chartWidth=Math.max(0,width-14);
  for(int n=0;n<historyCount;n++){
   float value=values[(historyHead+HISTORY_SIZE-1-n)%HISTORY_SIZE];
   int right=width-7-n*chartWidth/HISTORY_SIZE;
   int left=width-7-(n+1)*chartWidth/HISTORY_SIZE;
   if(right<=left)continue;
   int height=Math.clamp(Math.round(13*value/ceiling),1,13);
   int color=(fps?value<30:value>33.3)?0xfff58b7c:core.theme.accent();
   g.fill(left,30-height,Math.max(left+1,right-1),30,color);
  }
 }
 private static final String[] CARDINALS={"N","NO","O","SO","S","SW","W","NW"};
 private void compass(GuiGraphicsExtractor g,Minecraft mc,int width){
  float bearing=net.minecraft.util.Mth.positiveModulo(mc.player.getYRot()+180f,360f);
  int center=width/2;
  UiText.centered(g,Math.round(bearing)%360+"° · "+CARDINALS[Math.round(bearing/45)%8],center,6,core.theme.text());
  float pixelsPerDegree=Math.max(1,width-18)/120f;
  int first=(int)Math.floor((bearing-60)/15)*15;
  for(int angle=first;angle<=bearing+60;angle+=15){
   if(angle<bearing-60)continue;
   int x=Math.round(center+(angle-bearing)*pixelsPerDegree);
   boolean major=Math.floorMod(angle,45)==0;
   g.fill(x,major?23:26,x+1,30,core.theme.muted());
   if(major&&x>=12&&x<=width-12)UiText.centered(g,CARDINALS[Math.floorMod(angle/45,8)],x,32,core.theme.text());
  }
  g.fill(center,20,center+1,31,core.theme.accent());
 }
 private void effects(GuiGraphicsExtractor g,Minecraft mc,int width){
  var active=mc.player.getActiveEffects();
  String title=active.size()>3?"Statuseffekte · +"+(active.size()-3):"Statuseffekte";
  UiText.draw(g,UiText.fit(title,width-14),7,6,core.theme.text());
  if(active.isEmpty()){
   UiText.draw(g,"Keine aktiven Effekte",7,28,core.theme.muted());
   return;
  }
  int row=0;
  for(var effect:active){
   if(row==3)break;
   int y=20+row++*19;
   var key=effect.getEffect().unwrapKey();
   if(key.isPresent()){
    var id=key.get().identifier();
    var texture=net.minecraft.resources.Identifier.fromNamespaceAndPath(id.getNamespace(),"textures/mob_effect/"+id.getPath()+".png");
    g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,texture,7,y,0,0,16,16,16,16);
   }
   int seconds=Math.max(0,(effect.getDuration()+19)/20);
   String time=effect.isInfiniteDuration()?"∞":seconds/60+":"+(seconds%60<10?"0":"")+seconds%60;
   String name=effect.getEffect().value().getDisplayName().getString();
   if(effect.getAmplifier()>0)name+=" "+(effect.getAmplifier()+1);
   int timeWidth=UiText.width(time);
   UiText.draw(g,UiText.fit(name,Math.max(0,width-35-timeWidth)),27,y+4,core.theme.text());
   UiText.draw(g,time,width-7-timeWidth,y+4,!effect.isInfiniteDuration()&&seconds<=10?0xffff737a:core.theme.muted());
  }
 }
 private void target(GuiGraphicsExtractor g,Minecraft mc,int width){
  HitResult hit=mc.hitResult;
  if(hit==null||hit.getType()==HitResult.Type.MISS){
   UiText.draw(g,"Kein Ziel",8,11,core.theme.muted());
   return;
  }
  String name=switch(hit){
   case net.minecraft.world.phys.EntityHitResult entity -> entity.getEntity().getName().getString();
   case net.minecraft.world.phys.BlockHitResult block -> mc.level.getBlockState(block.getBlockPos()).getBlock().getName().getString();
   default -> "Ziel";
  };
  UiText.draw(g,UiText.fit(name,width-16),8,6,core.theme.text());
  double distance=mc.player.getEyePosition().distanceTo(hit.getLocation());
  UiText.draw(g,String.format(Locale.ROOT,"%.2f Blöcke",distance),8,20,core.theme.accent());
 }
 private void connection(GuiGraphicsExtractor g,Minecraft mc,int width){
  if(mc.hasSingleplayerServer()){
   UiText.draw(g,"Einzelspieler",8,7,core.theme.muted());
   return;
  }
  var connection=mc.getConnection();
  var info=connection==null?null:connection.getPlayerInfo(mc.player.getUUID());
  if(info==null||info.getLatency()<0){
   UiText.draw(g,"Keine Messung",8,7,core.theme.muted());
   return;
  }
  int ms=info.getLatency();
  int strength=ms<80?4:ms<150?3:ms<250?2:1;
  int color=strength>=3?0xff79e4a1:strength==2?0xffffd36a:0xffff737a;
  for(int i=0;i<4;i++)g.fill(8+i*4,16-(4+i*2),11+i*4,16,i<strength?color:0x66475c74);
  UiText.draw(g,ms+" ms",30,7,core.theme.text());
 }
 private void info(GuiGraphicsExtractor g,int width,String title,String value,String icon){
  GlassRender.icon(g,icon,7,11,14,core.theme.accent());
  UiText.draw(g,UiText.fit(title,width-34),27,5,core.theme.muted());
  UiText.draw(g,UiText.fit(value,width-34),27,19,core.theme.text());
 }
 private void chunk(GuiGraphicsExtractor g,Minecraft mc,int width){
  var pos=mc.player.blockPosition();
  int localX=Math.floorMod(pos.getX(),16),localZ=Math.floorMod(pos.getZ(),16);
  int map=width-39;
  if(map>=60){
   g.fill(map,7,map+32,39,0x60425571);
   g.fill(map+15,7,map+16,39,0x88778da8);
   g.fill(map,22,map+32,23,0x88778da8);
   g.fill(map+localX*2,7+localZ*2,map+localX*2+2,9+localZ*2,core.theme.accent());
  }else map=width;
  UiText.draw(g,UiText.fit("Chunk "+Math.floorDiv(pos.getX(),16)+" / "+Math.floorDiv(pos.getZ(),16),map-12),7,8,core.theme.text());
  UiText.draw(g,UiText.fit("Lokal "+localX+" / "+localZ,map-12),7,26,core.theme.muted());
 }
 private void coordinates(GuiGraphicsExtractor g,Minecraft mc,int width){
  var pos=mc.player.blockPosition();
  coordinate(g,width,0,"X",pos.getX(),0xffff9a91);
  coordinate(g,width,1,"Y",pos.getY(),0xff85e8ac);
  coordinate(g,width,2,"Z",pos.getZ(),0xff92c4ff);
 }
 private void coordinate(GuiGraphicsExtractor g,int width,int slot,String axis,int value,int color){
  int left=slot*width/3,right=(slot+1)*width/3,center=(left+right)/2;
  UiText.centered(g,axis,center,5,color);
  UiText.centered(g,UiText.fit(Integer.toString(value),Math.max(0,right-left-6)),center,19,core.theme.text());
  if(slot<2)g.fill(right,7,right+1,29,0x55475c74);
 }
 private void speed(GuiGraphicsExtractor g,int width){
  UiText.draw(g,UiText.fit(String.format(Locale.ROOT,"%.1f Blöcke/s",horizontalSpeed),width-14),7,6,core.theme.text());
  String rise=String.format(Locale.ROOT,"Vertikal %+.1f Blöcke/s",verticalSpeed);
  UiText.draw(g,UiText.fit(rise,width-14),7,21,core.theme.muted());
 }
 private void worldTime(GuiGraphicsExtractor g,int width){
  String time=String.format(Locale.ROOT,"%02d:%02d",clockTicks/1000,clockTicks%1000*60/1000);
  UiText.draw(g,time,7,6,core.theme.text());
  String phase=clockTicks<5000?"Nacht":clockTicks<7000?"Morgen":clockTicks<18000?"Tag":clockTicks<19500?"Abend":"Nacht";
  UiText.draw(g,phase,width-7-UiText.width(phase),6,core.theme.muted());
  int rail=Math.max(1,width-16),left=8;
  g.fill(left,24,left+rail,27,0xff405a88);
  g.fill(left+rail/4,24,left+rail*3/4,27,0xffd7a85d);
  int marker=left+(int)(clockTicks*rail/24000);
  g.fill(marker,21,marker+2,30,core.theme.text());
 }
 private void movement(GuiGraphicsExtractor g,Minecraft mc,int width){
  var p=mc.player;
  String state,detail;
  if(p.isPassenger()){state="Fahrzeug";detail=p.getVehicle().getName().getString();}
  else if(p.isFallFlying()){state="Elytra-Flug";detail="In der Luft";}
  else if(p.getAbilities().flying){state="Fliegen";detail="Freier Flug";}
  else if(p.isSwimming()){state="Schwimmen";detail="Im Wasser";}
  else if(p.onClimbable()){state="Klettern";detail="An einer Kletterfläche";}
  else if(!p.onGround()){state=verticalSpeed>.15?"Springen":"Fallen";detail="Kein Bodenkontakt";}
  else if(horizontalSpeed<.1){state="Stillstand";detail=p.isCrouching()?"Geduckt":"Am Boden";}
  else if(p.isSprinting()){state="Sprinten";detail="Am Boden";}
  else if(p.isCrouching()){state="Schleichen";detail="Geduckt";}
  else{state="Gehen";detail="Am Boden";}
  UiText.draw(g,UiText.fit(state,width-14),7,6,core.theme.text());
  UiText.draw(g,UiText.fit(detail,width-14),7,21,core.theme.muted());
 }
 private void tool(GuiGraphicsExtractor g,Minecraft mc,int width){
  var item=mc.player.getMainHandItem();
  if(!item.isEmpty())g.item(item,6,5);
  String name=item.isEmpty()?"Leere Hand":item.getHoverName().getString();
  int max=item.isDamageableItem()?item.getMaxDamage():0;
  int remaining=max>0?Math.clamp(max-item.getDamageValue(),0,max):0;
  String value=max>0?remaining+"/"+max:"";
  int valueWidth=value.isEmpty()?0:UiText.width(value)+5;
  UiText.draw(g,UiText.fit(name,Math.max(0,width-34-valueWidth)),27,6,core.theme.text());
  if(max>0){
   float ratio=(float)remaining/max;
   int color=ratio>.5f?0xff79e4a1:ratio>.2f?0xffffd36a:0xffff737a;
   UiText.draw(g,value,width-6-UiText.width(value),6,color);
   meterBar(g,27,20,width-33,ratio,color);
  }
 }
 private void armor(GuiGraphicsExtractor g,Minecraft mc,int width,ModuleRegistry.Appearance style,boolean editing){
  surface(g,width,ARMOR_HEIGHT,style,editing);
  if(style.accent)g.fill(7,8,9,16,core.theme.accent());
  UiText.draw(g,"Rüstung",14,7,core.theme.text());
  for(int i=0;i<ARMOR.length;i++){
   var item=mc.player.getItemBySlot(ARMOR[i]);
   int y=20+i*19;
   GlassRender.round(g,6,y,17,17,4,0x503d5168);
   if(!item.isEmpty())g.item(item,6,y);
   UiText.draw(g,ARMOR_NAMES[i],28,y+3,core.theme.text());
   if(item.isEmpty()||!item.isDamageableItem()){
    UiText.draw(g,"—",width-16,y+3,core.theme.muted());
    continue;
   }
   int max=item.getMaxDamage(),remaining=Math.clamp(max-item.getDamageValue(),0,max);
   float ratio=(float)remaining/max;
   int color=ratio>.5f?0xff79e4a1:ratio>.2f?0xffffd36a:0xffff737a;
   String durability=remaining+"/"+max;
   UiText.draw(g,durability,Math.max(62,width-7-UiText.width(durability)),y+3,color);
   int bar=Math.max(0,width-35);
   g.fill(28,y+15,28+bar,y+16,0x66475c74);
   if(remaining>0&&bar>0)g.fill(28,y+15,28+Math.max(1,Math.round(bar*ratio)),y+16,color);
  }
 }

}

