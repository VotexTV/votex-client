package dev.votex.client;
import com.google.gson.*;
import dev.votex.config.AtomicJson;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.texture.DynamicTexture;
import com.mojang.blaze3d.platform.NativeImage;
import java.util.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;

/** In-game choices are independently owned; launcher authoring files are never rewritten. */
public final class Wardrobe {
 public record Item(String id,String name,String slot,String description){}
 public static final String[] SLOTS={"all","wings","cape","head","face","pet","back","aura","overlay","feet"};
 public static final String[] SLOT_NAMES={"Alles","Flügel","Capes","Kopf","Gesicht","Begleiter","Rücken","Auren","Overlays","Füße"};
 private static List<Item> catalog=List.of(),skins=List.of(),looks=List.of(),emotes=List.of();
 private static final Map<String,JsonObject> LOOKS=new HashMap<>();
 private static final Map<String,Identifier> HEADS=new LinkedHashMap<>(64,.75f,true);
 private static final Map<String,Identifier> PREVIEWS=new LinkedHashMap<>(32,.75f,true);
 private static JsonObject own=new JsonObject();private static boolean readOwn;
 private Wardrobe(){}
 public static List<Item> cosmetics(){return catalog;}public static List<Item> skins(){return skins;}public static List<Item> looks(){return looks;}public static List<Item> emotes(){return emotes;}
 public static void refresh(VotexClient core){
  try{
   releasePreviews();
   if(!readOwn){readOwn=true;Path pref=core.directory.resolve("ingame-look.json");if(Files.exists(pref)&&Files.size(pref)<=64_000){JsonObject value=JsonParser.parseString(Files.readString(pref)).getAsJsonObject();if(value.has("schemaVersion")&&value.get("schemaVersion").getAsInt()==1)own=value;}}
   JsonArray bundled;try(var in=Wardrobe.class.getResourceAsStream("/assets/votex_client/catalog/index.json")){bundled=JsonParser.parseString(new String(in.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonArray();}
   List<Item> items=new ArrayList<>();for(var e:bundled){var a=e.getAsJsonObject();items.add(new Item(a.get("id").getAsString(),a.get("name").getAsString(),a.get("slot").getAsString(),a.has("description")?a.get("description").getAsString():""));}
   List<Item> skinList=new ArrayList<>(),lookList=new ArrayList<>(),emoteList=new ArrayList<>();LOOKS.clear();
   for(int i=0;i<EmotePlayer.IDS.length;i++)emoteList.add(new Item(EmotePlayer.IDS[i],EmotePlayer.NAMES[i],"emote","Votex Originals"));
   Path file=core.directory.resolve("presets.json");if(Files.exists(file)&&Files.size(file)<=8_000_000){var presets=JsonParser.parseString(Files.readString(file)).getAsJsonObject();
    if(presets.has("skins"))for(var e:presets.getAsJsonArray("skins")){var a=e.getAsJsonObject();skinList.add(item(a,"skin","Deine Skin-Bibliothek"));}
    if(presets.has("cosmetics"))for(var e:presets.getAsJsonArray("cosmetics")){var a=e.getAsJsonObject();Item l=item(a,"look","Gespeichertes Outfit");lookList.add(l);LOOKS.put(l.id,a.deepCopy());}
    if(presets.has("customModels"))for(var e:presets.getAsJsonArray("customModels")){var a=e.getAsJsonObject();items.add(item(a,a.get("slot").getAsString(),"Dein Modell aus dem Atelier"));}
    if(presets.has("customEmotes"))for(var e:presets.getAsJsonArray("customEmotes")){var a=e.getAsJsonObject();String id=a.get("id").getAsString();if(EmotePlayer.valid(id))emoteList.add(item(a,"emote","Deine Animation"));}
   }
   if(own.has("importedCapes")){int count=0;for(var entry:own.getAsJsonArray("importedCapes")){if(count++>=128)break;var cape=entry.getAsJsonObject();String id=cape.get("id").getAsString();if(id.matches("ingame-[a-f0-9-]{36}")&&Files.isRegularFile(core.directory.resolve("assets/cape-"+id+".png"),LinkOption.NOFOLLOW_LINKS))items.add(new Item(id,cape.has("name")?cape.get("name").getAsString():"Eigenes Cape","cape","Dein importiertes Bild · lokal gespeichert"));}}
   catalog=List.copyOf(items);skins=List.copyOf(skinList);looks=List.copyOf(lookList);emotes=List.copyOf(emoteList);
   apply(core);
  }catch(Exception e){core.error("Garderobe konnte nicht geladen werden",e);}
 }
 private static Item item(JsonObject a,String slot,String description){String id=a.get("id").getAsString();if(!id.matches("[A-Za-z0-9_-]{1,80}"))throw new IllegalArgumentException("Ungültige Auswahl");return new Item(id,a.has("name")?a.get("name").getAsString():id,slot,description);}
 private static void apply(VotexClient core)throws Exception{
  if(own.has("outfit")&&LOOKS.containsKey(own.get("outfit").getAsString())){var look=LOOKS.get(own.get("outfit").getAsString()).deepCopy();LocalLooks.look=look;LocalLooks.selectCape(core.directory,look.has("customCape")&&look.get("customCape").getAsBoolean()?look.get("id").getAsString():"");}
  if(own.has("equipment")){JsonObject equipment=LocalLooks.look.has("equipment")?LocalLooks.look.getAsJsonObject("equipment").deepCopy():new JsonObject();for(var e:own.getAsJsonObject("equipment").entrySet()){String id=e.getValue().getAsString();if(id.isEmpty())equipment.remove(e.getKey());else if(catalog.stream().anyMatch(i->i.id.equals(id)&&i.slot.equals(e.getKey())))equipment.addProperty(e.getKey(),id);}LocalLooks.look.add("equipment",equipment);LocalLooks.look.addProperty("cape",equipment.has("cape"));if(own.getAsJsonObject("equipment").has("cape"))LocalLooks.selectCape(core.directory,"");}
  if(own.has("skin")){String id=own.get("skin").getAsString();if(skins.stream().anyMatch(i->i.id.equals(id)))LocalLooks.selectSkin(core.directory,id);}
  if(own.has("wheel")){var a=own.getAsJsonArray("wheel");if(a.size()==8){String[] wheel=LocalLooks.wheel.clone();for(int i=0;i<8;i++)if(EmotePlayer.valid(a.get(i).getAsString()))wheel[i]=a.get(i).getAsString();LocalLooks.wheel=wheel;}}
  if(own.has("speed"))EmotePlayer.setSpeed(own.get("speed").getAsDouble());if(own.has("loop"))EmotePlayer.setLoop(own.get("loop").getAsBoolean());
  if(own.has("nametag")){String name=own.get("nametag").getAsString();if(name.length()<=48&&name.codePoints().noneMatch(Character::isISOControl))LocalLooks.look.addProperty("nametag",name);}
  if(own.has("scale"))LocalLooks.look.addProperty("scale",Math.clamp(own.get("scale").getAsInt(),50,150));
  if(own.has("customCape")){String id=own.get("customCape").getAsString();if(id.matches("ingame-[a-f0-9-]{36}")){LocalLooks.selectCape(core.directory,id);LocalLooks.look.addProperty("cape",true);}}
  LocalLooks.revision++;
 }
 public static boolean equipped(Item item){if(item.id.startsWith("ingame-"))return item.id.equals(LocalLooks.assetCapeId())&&LocalLooks.enabled("cape");try{return LocalLooks.look.getAsJsonObject("equipment").get(item.slot).getAsString().equals(item.id);}catch(Exception e){return false;}}
 public static void equip(VotexClient core,Item item){if(catalog.stream().noneMatch(i->i.id.equals(item.id)))return;boolean on=equipped(item);JsonObject equipment=own.has("equipment")?own.getAsJsonObject("equipment"):new JsonObject();equipment.addProperty(item.slot,on||item.id.startsWith("ingame-")?"":item.id);own.add("equipment",equipment);if(item.slot.equals("cape")){own.remove("customCape");if(item.id.startsWith("ingame-")&&!on)own.addProperty("customCape",item.id);try{LocalLooks.selectCape(core.directory,"");}catch(Exception e){core.error("Cape konnte nicht gewählt werden",e);}}commit(core);}
 public static void outfit(VotexClient core,Item item){own.addProperty("outfit",item.id);own.remove("equipment");own.remove("customCape");commit(core);}
 public static void skin(VotexClient core,Item item){own.addProperty("skin",item.id);commit(core);}
 public static void slot(VotexClient core,int slot,String id){if(slot<0||slot>=8||!EmotePlayer.valid(id))return;LocalLooks.wheel[slot]=id;own.add("wheel",AtomicJson.GSON.toJsonTree(LocalLooks.wheel));commit(core);}
 public static void playback(VotexClient core,double speed,boolean loop){own.addProperty("speed",Math.clamp(speed,.5,2));own.addProperty("loop",loop);commit(core);}
 public static void nametag(VotexClient core,String name){if(name.length()>48||name.codePoints().anyMatch(Character::isISOControl))return;own.addProperty("nametag",name);commit(core);}
 public static void scale(VotexClient core,int scale){own.addProperty("scale",Math.clamp(scale,50,150));commit(core);}
 public static boolean canImportCape(){return !own.has("importedCapes")||own.getAsJsonArray("importedCapes").size()<128;}
 public static void importedCape(VotexClient core,String assetId){if(!assetId.matches("ingame-[a-f0-9-]{36}")||!canImportCape())return;JsonArray library=own.has("importedCapes")?own.getAsJsonArray("importedCapes"):new JsonArray();JsonObject entry=new JsonObject();entry.addProperty("id",assetId);entry.addProperty("name","Eigenes Cape "+(library.size()+1));library.add(entry);own.add("importedCapes",library);own.addProperty("customCape",assetId);JsonObject equipment=own.has("equipment")?own.getAsJsonObject("equipment"):new JsonObject();equipment.addProperty("cape","");own.add("equipment",equipment);var items=new ArrayList<>(catalog);items.add(new Item(assetId,entry.get("name").getAsString(),"cape","Dein importiertes Bild · lokal gespeichert"));catalog=List.copyOf(items);commit(core);}
 public static void launcher(VotexClient core){JsonArray imports=own.has("importedCapes")?own.getAsJsonArray("importedCapes").deepCopy():null;own=new JsonObject();if(imports!=null)own.add("importedCapes",imports);own.addProperty("schemaVersion",1);LocalLooks.reload(core.directory);commit(core);}
 private static void commit(VotexClient core){try{own.addProperty("schemaVersion",1);apply(core);core.engine.saveWardrobe(own);}catch(Exception e){core.error("Auswahl konnte nicht gespeichert werden",e);}}
 public static Identifier head(VotexClient core,String id){return thumbnail(core,id,false);}
 public static Identifier preview(String id){if(!id.matches("[a-z0-9_-]{1,80}"))return null;Identifier found=PREVIEWS.get(id);if(found!=null)return found;var manager=Minecraft.getInstance().getTextureManager();if(PREVIEWS.size()>=32){String oldest=PREVIEWS.keySet().iterator().next();manager.release(PREVIEWS.remove(oldest));}Identifier texture=Identifier.fromNamespaceAndPath("votex_client","textures/previews/"+id+".png");PREVIEWS.put(id,texture);return texture;}
 public static void releasePreviews(){var manager=Minecraft.getInstance().getTextureManager();for(var texture:HEADS.values())manager.release(texture);for(var texture:PREVIEWS.values())manager.release(texture);HEADS.clear();PREVIEWS.clear();}
 public static Identifier capeThumbnail(VotexClient core,String id){return thumbnail(core,id,true);}
 private static Identifier thumbnail(VotexClient core,String id,boolean cape){String key=(cape?"cape-":"skin-")+id;if(HEADS.containsKey(key))return HEADS.get(key);try{if(!id.matches("[A-Za-z0-9_-]{1,80}"))return null;Path p=core.directory.resolve("assets/"+key+".png");if(Files.isSymbolicLink(p)||!Files.exists(p)||Files.size(p)>1_125_000)return null;byte[] bytes=Files.readAllBytes(p);dev.votex.social.SocialRules.validatePng(bytes,cape?32:64);NativeImage image=NativeImage.read(bytes);if(HEADS.size()>=64){String oldest=HEADS.keySet().iterator().next();Minecraft.getInstance().getTextureManager().release(HEADS.remove(oldest));}Identifier texture=Identifier.fromNamespaceAndPath("votex_client","cards/"+key);Minecraft.getInstance().getTextureManager().register(texture,new DynamicTexture(()->"Votex library card",image));HEADS.put(key,texture);return texture;}catch(Exception e){return null;}}
}
