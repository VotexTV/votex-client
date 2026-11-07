package dev.votex.client;
import com.google.gson.*;
import com.mojang.blaze3d.platform.NativeImage;
import dev.votex.config.AtomicJson;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import java.nio.file.*;
import java.util.*;
import dev.votex.social.SocialRules;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.core.ClientAsset;
public final class LocalLooks {
 public static boolean visible=true,capeVisible=true;
 public static JsonObject look=new JsonObject();public static int revision;public static String[] wheel=EmotePlayer.IDS.clone();
 private static Identifier skin,cape;private static final Identifier DEFAULT_CAPE=Identifier.fromNamespaceAndPath("votex_client","textures/cape.png");
 private static PlayerModelType skinModel;
 private record SkinResult(PlayerSkin original,Identifier texture,PlayerModelType model,PlayerSkin result){}
 private static final Map<UUID,SkinResult> SKIN_RESULTS=new LinkedHashMap<>(16,.75f,true);
 private static String skinAssetId="",capeAssetId="";private static JsonArray networkModels=new JsonArray(),networkEmotes=new JsonArray();
 public record Remote(JsonObject look,Identifier skin,Identifier cape,boolean visible,boolean capeVisible,int revision,PlayerModelType model){}
 private static final Map<UUID,Remote> REMOTE=new LinkedHashMap<>();private static int remoteRevision;
 public static boolean local(AvatarRenderState state){var p=Minecraft.getInstance().player;return p!=null&&p.getId()==state.id&&!state.isInvisible;}
 public static void selectSkin(Path directory,String id)throws Exception{PlayerModelType nextModel=readSkinModel(directory,id);Identifier next=load(directory,"skin",id,false);if(!id.isEmpty()&&next==null)throw new IllegalArgumentException("Skin asset is missing");if(skin!=null)Minecraft.getInstance().getTextureManager().release(skin);skin=next;skinModel=next==null?null:nextModel;skinAssetId=id;revision++;}
 private static PlayerModelType skinModel(JsonObject data,String id){if(!data.has("skins"))return null;int count=0;for(var item:data.getAsJsonArray("skins")){if(++count>256)break;var value=item.getAsJsonObject();if(value.has("id")&&value.get("id").getAsString().equals(id)&&value.has("model"))return parseSkinModel(value.get("model").getAsString());}return null;}
 private static PlayerModelType parseSkinModel(String name){return switch(name){case "slim"->PlayerModelType.SLIM;case "default"->PlayerModelType.WIDE;default->throw new IllegalArgumentException("Invalid skin model");};}
 private static PlayerModelType readSkinModel(Path directory,String id)throws Exception{Path file=directory.resolve("presets.json");if(id.isEmpty()||!Files.exists(file))return null;if(Files.size(file)>8_000_000)throw new IllegalArgumentException("Preset metadata too large");return skinModel(JsonParser.parseString(Files.readString(file)).getAsJsonObject(),id);}
 public static PlayerSkin playerSkin(UUID owner,PlayerSkin original){
  var player=Minecraft.getInstance().player;boolean local=player!=null&&owner.equals(player.getUUID());Remote remote=REMOTE.get(owner);Identifier texture=local?skin:remote==null?null:remote.skin;PlayerModelType model=local?skinModel:remote==null?null:remote.model;
  if(texture==null)return original;if(model==null)model=original.model();SkinResult cached=SKIN_RESULTS.get(owner);if(cached!=null&&cached.original.equals(original)&&cached.texture.equals(texture)&&cached.model==model)return cached.result;
  PlayerSkin result=new PlayerSkin(new ClientAsset.ResourceTexture(texture,texture),original.cape(),original.elytra(),model,false);SKIN_RESULTS.put(owner,new SkinResult(original,texture,model,result));while(SKIN_RESULTS.size()>128)SKIN_RESULTS.remove(SKIN_RESULTS.keySet().iterator().next());return result;
 }
 public static void selectCape(Path directory,String id)throws Exception{Identifier next=load(directory,"cape",id,true);if(!id.isEmpty()&&next==null)throw new IllegalArgumentException("Cape asset is missing");if(cape!=null)Minecraft.getInstance().getTextureManager().release(cape);cape=next;capeAssetId=id;revision++;}
 public static String assetSkinId(){return skinAssetId;}public static String assetCapeId(){return capeAssetId;}
 public static Identifier skin(AvatarRenderState state){Remote remote=remote(state);return local(state)?skin:remote==null?null:remote.skin;}
 public static UUID playerId(AvatarRenderState state){var c=Minecraft.getInstance();if(c.level==null)return null;var entity=c.level.getEntity(state.id);return entity==null?null:entity.getUUID();}
 public static Remote remote(AvatarRenderState state){if(state.isInvisible)return null;UUID id=playerId(state);return id==null?null:REMOTE.get(id);}
 public static JsonObject forState(AvatarRenderState state){if(local(state))return look;Remote remote=remote(state);return remote==null?null:remote.look;}
 public static int revision(AvatarRenderState state){Remote remote=remote(state);return local(state)?revision:remote==null?-1:remote.revision;}
 public static boolean visible(AvatarRenderState state){Remote remote=remote(state);return local(state)?visible:remote!=null&&remote.visible;}
 public static boolean capeVisible(AvatarRenderState state){Remote remote=remote(state);return local(state)?capeVisible:remote!=null&&remote.capeVisible;}
 public static Identifier cape(AvatarRenderState state){if(local(state))return cape();Remote remote=remote(state);if(remote!=null&&remote.cape!=null)return remote.cape;JsonObject chosen=forState(state);try{if(chosen!=null&&chosen.has("equipment")){String id=chosen.getAsJsonObject("equipment").get("cape").getAsString();JsonObject model=WearableLayer.model(id);if(model!=null&&model.has("capeTexture"))return Identifier.fromNamespaceAndPath("votex_client","catalog/capes/"+id+".png");}}catch(Exception ignored){}return DEFAULT_CAPE;}
 public static Identifier cape(){
  if(cape!=null)return cape;
  try{if(look.has("equipment")){String id=look.getAsJsonObject("equipment").get("cape").getAsString();JsonObject model=WearableLayer.model(id);if(model!=null&&model.has("capeTexture"))return Identifier.fromNamespaceAndPath("votex_client","catalog/capes/"+id+".png");}}catch(Exception ignored){}
  return DEFAULT_CAPE;
 }
 public static boolean enabled(String key){return look.has(key)&&look.get(key).isJsonPrimitive()&&look.get(key).getAsJsonPrimitive().isBoolean()&&look.get(key).getAsBoolean();}
 public static boolean enabled(AvatarRenderState state,String key){JsonObject chosen=forState(state);return chosen!=null&&chosen.has(key)&&chosen.get(key).isJsonPrimitive()&&chosen.get(key).getAsJsonPrimitive().isBoolean()&&chosen.get(key).getAsBoolean();}
 public static boolean coversHead(){try{return java.util.Set.of("axolotl-cap","fox-beanie","red-panda","creator-fox-hat","creator-frog-hat","orbit-helmet","star-witch").contains(look.getAsJsonObject("equipment").get("head").getAsString());}catch(Exception ignored){return false;}}
 public static boolean coversHead(AvatarRenderState state){try{return java.util.Set.of("axolotl-cap","fox-beanie","red-panda","creator-fox-hat","creator-frog-hat","orbit-helmet","star-witch").contains(forState(state).getAsJsonObject("equipment").get("head").getAsString());}catch(Exception ignored){return false;}}
 public static String text(String key,String fallback){try{return look.has(key)?look.get(key).getAsString():fallback;}catch(Exception e){return fallback;}}
 public static float scale(){try{return Math.max(.5f,Math.min(1.5f,look.get("scale").getAsFloat()/100f));}catch(Exception e){return 1;}}
 public static float scale(AvatarRenderState state){try{return Math.max(.5f,Math.min(1.5f,forState(state).get("scale").getAsFloat()/100f));}catch(Exception e){return 1;}}
 public static String nametag(AvatarRenderState state){try{String label=forState(state).get("nametag").getAsString();return label.length()<=48&&!label.isBlank()&&!label.codePoints().anyMatch(c->c<32||c==127)?label:null;}catch(Exception ignored){return null;}}
 public static JsonObject networkAppearance(){JsonObject out=new JsonObject();out.add("look",look.deepCopy());out.addProperty("visible",visible);out.addProperty("capeVisible",capeVisible);if(skin!=null&&skinModel!=null)out.addProperty("skinModel",skinModel==PlayerModelType.SLIM?"slim":"default");JsonArray models=new JsonArray(),emotes=new JsonArray();Set<String> ids=new HashSet<>();try{for(var item:look.getAsJsonObject("equipment").entrySet())ids.add(item.getValue().getAsString());}catch(Exception ignored){}for(var m:networkModels)if(ids.contains(m.getAsJsonObject().get("id").getAsString()))models.add(m.deepCopy());for(var e:EmotePlayer.customDefinitions())if(Arrays.asList(wheel).contains(e.getAsJsonObject().get("id").getAsString()))emotes.add(e.deepCopy());out.add("models",models);out.add("emotes",emotes);return out;}
 public static void remote(UUID player,JsonObject appearance){if(REMOTE.size()>=SocialRules.MAX_PEERS&&!REMOTE.containsKey(player))return;Identifier nextSkin=null,nextCape=null;try{PlayerModelType model=appearance.has("skinModel")?parseSkinModel(appearance.get("skinModel").getAsString()):null;if(appearance.has("skin"))nextSkin=remoteTexture(appearance.get("skin").getAsString(),"skin",64);if(appearance.has("cape"))nextCape=remoteTexture(appearance.get("cape").getAsString(),"cape",32);Remote previous=REMOTE.put(player,new Remote(appearance.getAsJsonObject("look"),nextSkin,nextCape,appearance.get("visible").getAsBoolean(),appearance.get("capeVisible").getAsBoolean(),++remoteRevision,model));if(previous!=null)release(previous);WearableLayer.remoteModels(player,appearance.getAsJsonArray("models"));EmotePlayer.setRemoteCustom(player,appearance.getAsJsonArray("emotes"));}catch(Exception invalid){if(nextSkin!=null)Minecraft.getInstance().getTextureManager().release(nextSkin);if(nextCape!=null)Minecraft.getInstance().getTextureManager().release(nextCape);}}
 private static Identifier remoteTexture(String encoded,String type,int height)throws Exception{byte[] bytes=Base64.getDecoder().decode(encoded);SocialRules.validatePng(bytes,height);NativeImage image=NativeImage.read(bytes);Identifier id=Identifier.fromNamespaceAndPath("votex_client","remote/"+type+"-"+UUID.randomUUID());Minecraft.getInstance().getTextureManager().register(id,new DynamicTexture(()->"Votex peer "+type,image));return id;}
 private static void release(Remote remote){var textures=Minecraft.getInstance().getTextureManager();if(remote.skin!=null)textures.release(remote.skin);if(remote.cape!=null)textures.release(remote.cape);}
 public static void removeRemote(UUID player){SKIN_RESULTS.remove(player);Remote removed=REMOTE.remove(player);if(removed!=null)release(removed);WearableLayer.removeRemote(player);EmotePlayer.removeRemote(player);}
 public static void clearRemote(){for(UUID player:new ArrayList<>(REMOTE.keySet()))removeRemote(player);}
 public static int color(String key){try{String c=text(key,"#a7ceff");if(!c.matches("#[a-fA-F0-9]{6}"))return 0xffa7ceff;return 0xff000000|Integer.parseInt(c.substring(1),16);}catch(Exception e){return 0xffa7ceff;}}
 public static void reload(Path directory){
  try{Path file=directory.resolve("presets.json");if(!Files.exists(file))return;if(Files.size(file)>8_000_000)throw new IllegalArgumentException("Preset metadata too large");JsonObject data=AtomicJson.GSON.fromJson(Files.readString(file),JsonObject.class);if(data.get("schemaVersion").getAsInt()!=1)throw new IllegalArgumentException("Unknown preset version");JsonObject next=new JsonObject();String selected=data.has("selectedCosmetic")?data.get("selectedCosmetic").getAsString():"";
   networkModels=data.has("customModels")?data.getAsJsonArray("customModels").deepCopy():new JsonArray();networkEmotes=data.has("customEmotes")?data.getAsJsonArray("customEmotes").deepCopy():new JsonArray();WearableLayer.setCustomModels(networkModels);EmotePlayer.setCustom(networkEmotes);
   for(JsonElement e:data.getAsJsonArray("cosmetics")){JsonObject l=e.getAsJsonObject();if(l.get("id").getAsString().equals(selected)){next=l;break;}}
   String[] nextWheel=EmotePlayer.IDS.clone();if(data.has("emoteWheel")&&data.get("emoteWheel").isJsonArray()){JsonArray a=data.getAsJsonArray("emoteWheel");if(a.size()!=8)throw new IllegalArgumentException("Invalid wheel");for(int i=0;i<8;i++){String id=a.get(i).getAsString();if(!EmotePlayer.valid(id))throw new IllegalArgumentException("Unknown emote");nextWheel[i]=id;}}
   String selectedSkin=data.has("selectedSkin")?data.get("selectedSkin").getAsString():"";if(next.has("skin")&&!next.get("skin").getAsString().isEmpty())selectedSkin=next.get("skin").getAsString();if(VotexClient.INSTANCE!=null&&!VotexClient.INSTANCE.modules.config().selectedSkin.isEmpty())selectedSkin=VotexClient.INSTANCE.modules.config().selectedSkin;String capeId=next.has("id")?next.get("id").getAsString():"";
   PlayerModelType nextSkinModel=skinModel(data,selectedSkin);Identifier newSkin=load(directory,"skin",selectedSkin,false),newCape=next.has("customCape")&&next.get("customCape").getAsBoolean()?load(directory,"cape",capeId,true):null;
   var manager=Minecraft.getInstance().getTextureManager();if(skin!=null)manager.release(skin);if(cape!=null)manager.release(cape);skin=newSkin;skinModel=newSkin==null?null:nextSkinModel;cape=newCape;skinAssetId=selectedSkin;capeAssetId=newCape==null?"":capeId;look=next;wheel=nextWheel;revision++;
  }catch(Exception e){if(VotexClient.INSTANCE!=null)VotexClient.INSTANCE.error("Lokaler Look konnte nicht geladen werden",e);}
 }
 private static Identifier load(Path dir,String type,String id,boolean cape)throws Exception{
  if(id.isEmpty())return null;if(!id.matches("[a-zA-Z0-9_-]{1,80}"))throw new IllegalArgumentException("Invalid asset ID");Path asset=dir.resolve("assets").resolve(type+"-"+id+".png");if(!Files.exists(asset))return null;if(Files.isSymbolicLink(asset)||Files.size(asset)>1_125_000)throw new IllegalArgumentException("Invalid local image");byte[] bytes=Files.readAllBytes(asset);if(bytes.length<24)throw new IllegalArgumentException("Invalid PNG");var buffer=java.nio.ByteBuffer.wrap(bytes);int w=buffer.getInt(16),h=buffer.getInt(20);if(w!=64||(cape?h!=32:h!=64))return null;
  NativeImage image=NativeImage.read(bytes);Identifier texture=Identifier.fromNamespaceAndPath("votex_client","local/"+type+"-"+java.util.UUID.randomUUID());Minecraft.getInstance().getTextureManager().register(texture,new DynamicTexture(()->"Votex local "+type,image));return texture;
 }
}
