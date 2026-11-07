package dev.votex.social;

import com.google.gson.*;
import com.google.gson.stream.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

/** Shared validation. No URLs, executable assets, filesystem paths or client-supplied identities. */
public final class SocialRules {
 public static final int VERSION=1,MAX_WIRE=24_000,PART_BYTES=12_000,MAX_PACKED=192_000,MAX_APPEARANCE=1_000_000,MAX_PEERS=128;
 private static final Set<String> SLOTS=Set.of("wings","head","face","back","cape","aura","feet","overlay","shoes","horns","halo","pet","body","hands","shoulders");
 private static final Set<String> JOINTS=Set.of("head","body","leftArm","rightArm","leftLeg","rightLeg");
 private static final Set<String> BUILTINS=new HashSet<>();
 static {try(var stream=SocialRules.class.getResourceAsStream("/assets/votex_client/catalog/index.json")){if(stream!=null)for(var e:JsonParser.parseString(new String(stream.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonArray())BUILTINS.add(e.getAsJsonObject().get("id").getAsString());}catch(Exception ignored){}}
 private SocialRules(){}
 public static JsonObject message(String type){var o=new JsonObject();o.addProperty("v",VERSION);o.addProperty("type",type);return o;}
 public static JsonObject parse(String json){if(json==null||json.length()>MAX_WIRE)throw new IllegalArgumentException("Payload too large");JsonObject o=parseBounded(json);if(o.get("v").getAsInt()!=VERSION||o.size()>16)throw new IllegalArgumentException("Unsupported social payload");return o;}
 private static JsonObject parseBounded(String json){try{JsonReader reader=new JsonReader(new StringReader(json));reader.setStrictness(Strictness.STRICT);reader.setNestingLimit(32);JsonObject result=JsonParser.parseReader(reader).getAsJsonObject();if(reader.peek()!=JsonToken.END_DOCUMENT)throw new IllegalArgumentException("Trailing JSON content");return result;}catch(IOException|JsonParseException invalid){throw new IllegalArgumentException("Invalid social JSON",invalid);}}
 public static String text(JsonObject o,String key,int max){String s=o.get(key).getAsString();if(s.length()>max||s.codePoints().anyMatch(c->c<32||c==127))throw new IllegalArgumentException("Invalid text");return s;}
 public static UUID uuid(JsonObject o,String key){return UUID.fromString(text(o,key,36));}
 public static boolean emote(String id){return Set.of("wave","sit","dance","point","clap","salute","celebrate","bow","handshake").contains(id)||id.matches("custom-emote-[a-f0-9-]{1,40}");}
 public static String hash(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(Exception e){throw new IllegalStateException(e);}}
 public static byte[] pack(JsonObject appearance){byte[] raw=appearance.toString().getBytes(StandardCharsets.UTF_8);if(raw.length>MAX_APPEARANCE)throw new IllegalArgumentException("Appearance exceeds mesh budget");try{var out=new ByteArrayOutputStream();try(var zip=new DeflaterOutputStream(out)){zip.write(raw);}byte[] packed=out.toByteArray();if(packed.length>MAX_PACKED)throw new IllegalArgumentException("Appearance exceeds transfer budget");return packed;}catch(IOException e){throw new UncheckedIOException(e);}}
 public static JsonObject unpack(byte[] packed){if(packed.length>MAX_PACKED)throw new IllegalArgumentException("Transfer too large");try(var in=new InflaterInputStream(new ByteArrayInputStream(packed))){byte[] bytes=in.readNBytes(MAX_APPEARANCE+1);if(bytes.length>MAX_APPEARANCE||in.read()!=-1)throw new IllegalArgumentException("Expanded appearance too large");return validate(parseBounded(new String(bytes,StandardCharsets.UTF_8)));}catch(IOException e){throw new IllegalArgumentException("Invalid compressed appearance",e);}}
 public static List<JsonObject> chunks(JsonObject appearance){byte[] packed=pack(validate(appearance));String digest=hash(packed);int count=(packed.length+PART_BYTES-1)/PART_BYTES;List<JsonObject> out=new ArrayList<>(count);for(int i=0;i<count;i++){JsonObject p=message("appearance-part");p.addProperty("hash",digest);p.addProperty("index",i);p.addProperty("count",count);p.addProperty("data",Base64.getEncoder().encodeToString(Arrays.copyOfRange(packed,i*PART_BYTES,Math.min(packed.length,(i+1)*PART_BYTES))));out.add(p);}return List.copyOf(out);}
 public static JsonObject validate(JsonObject source){
  if(source.size()>8)throw new IllegalArgumentException("Unknown appearance fields");
  JsonObject out=new JsonObject(),look=source.getAsJsonObject("look").deepCopy();
  if(look.size()>32)throw new IllegalArgumentException("Invalid look");
  JsonObject selections=look.has("equipment")?look.getAsJsonObject("equipment"):new JsonObject();
  if(selections.size()>12)throw new IllegalArgumentException("Too many equipment slots");
  JsonObject equipment=new JsonObject();
  JsonArray models=source.has("models")?source.getAsJsonArray("models"):new JsonArray();if(models.size()>12)throw new IllegalArgumentException("Too many models");
  Set<String> customs=new HashSet<>();int[] budget={0};for(JsonElement model:models){validateModel(model.getAsJsonObject(),budget);customs.add(model.getAsJsonObject().get("id").getAsString());}
  for(var entry:selections.entrySet()){
   String slot=entry.getKey();
   JsonElement value=entry.getValue();
   if(!SLOTS.contains(slot)||!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isString())throw new IllegalArgumentException("Unknown cosmetic selection");
   String id=value.getAsString();
   // The launcher stores unequipped slots as empty strings. Omit them on the wire.
   if(id.isEmpty())continue;
   if(!BUILTINS.contains(id)&&!customs.contains(id))throw new IllegalArgumentException("Unknown cosmetic selection");
   equipment.addProperty(slot,id);
  }
  // Copy only render fields. Names/author metadata and private local asset paths never cross the wire.
  JsonObject cleanLook=new JsonObject();cleanLook.add("equipment",equipment.deepCopy());
  if(look.has("nametag"))cleanLook.addProperty("nametag",text(look,"nametag",48));
  for(String key:List.of("cape","wings","halo","horns")){cleanLook.addProperty(key,look.has(key)&&look.get(key).getAsBoolean());}
  for(String key:List.of("primary","secondary","accent","wingColor","haloColor","hornColor","capeColor"))if(look.has(key)){String color=look.get(key).getAsString();if(!color.matches("#[a-fA-F0-9]{6}"))throw new IllegalArgumentException("Invalid color");cleanLook.addProperty(key,color);}
  double scale=look.has("scale")?look.get("scale").getAsDouble():100;if(!Double.isFinite(scale)||scale<50||scale>150)throw new IllegalArgumentException("Invalid scale");cleanLook.addProperty("scale",scale);
  out.add("look",cleanLook);out.add("models",models.deepCopy());out.addProperty("visible",!source.has("visible")||source.get("visible").getAsBoolean());out.addProperty("capeVisible",!source.has("capeVisible")||source.get("capeVisible").getAsBoolean());
  if(source.has("skinModel")){String model=source.get("skinModel").getAsString();if(!Set.of("default","slim").contains(model))throw new IllegalArgumentException("Invalid skin model");out.addProperty("skinModel",model);}
  for(String image:List.of("skin","cape"))if(source.has(image)){String encoded=source.get(image).getAsString();byte[] bytes=Base64.getDecoder().decode(encoded);validatePng(bytes,image.equals("cape")?32:64);out.addProperty(image,encoded);}
  JsonArray emotes=source.has("emotes")?source.getAsJsonArray("emotes"):new JsonArray();if(emotes.size()>8)throw new IllegalArgumentException("Too many custom emotes");for(var e:emotes)validateEmote(e.getAsJsonObject());out.add("emotes",emotes.deepCopy());
  return out;
 }
 public static void validatePng(byte[] bytes,int height){if(bytes.length<24||bytes.length>32_768)throw new IllegalArgumentException("PNG budget exceeded");byte[] sig={(byte)137,80,78,71,13,10,26,10};for(int i=0;i<8;i++)if(bytes[i]!=sig[i])throw new IllegalArgumentException("Not PNG");var b=java.nio.ByteBuffer.wrap(bytes);if(b.getInt(16)!=64||b.getInt(20)!=height)throw new IllegalArgumentException("Unexpected PNG dimensions");}
 public static void validateEmote(JsonObject e){if(e.size()>8||!text(e,"id",60).matches("custom-emote-[a-f0-9-]{1,40}"))throw new IllegalArgumentException("Invalid emote ID");text(e,"name",48);double duration=e.get("duration").getAsDouble();if(!Double.isFinite(duration)||duration<1||duration>10)throw new IllegalArgumentException("Invalid duration");JsonArray frames=e.getAsJsonArray("keyframes");if(frames.size()<2||frames.size()>24)throw new IllegalArgumentException("Invalid frame budget");double prev=-1;for(var frame:frames){JsonObject f=frame.getAsJsonObject();double time=f.get("time").getAsDouble();if(!Double.isFinite(time)||time<0||time>1||time<=prev)throw new IllegalArgumentException("Invalid timeline");prev=time;array(f.getAsJsonArray("pose"),19,3);}if(frames.get(0).getAsJsonObject().get("time").getAsDouble()!=0||prev!=1)throw new IllegalArgumentException("Incomplete timeline");}
 private static void array(JsonArray a,int length,double bound){if(a.size()!=length)throw new IllegalArgumentException("Invalid array shape");for(var v:a){double n=v.getAsDouble();if(!Double.isFinite(n)||Math.abs(n)>bound)throw new IllegalArgumentException("Invalid finite coordinate");}}
 private static void validateModel(JsonObject model,int[] budget){if(!text(model,"id",60).matches("custom-[a-f0-9-]{1,40}")||model.get("schemaVersion").getAsInt()!=2||!SLOTS.contains(text(model,"slot",16)))throw new IllegalArgumentException("Invalid custom model");JsonArray parts=model.getAsJsonArray("parts");if(parts.size()>24)throw new IllegalArgumentException("Part budget exceeded");for(var part:parts){JsonObject p=part.getAsJsonObject();if(!JOINTS.contains(text(p,"joint",16)))throw new IllegalArgumentException("Unknown joint");JsonArray positions=p.getAsJsonArray("positions"),normals=p.getAsJsonArray("normals"),colors=p.getAsJsonArray("colors"),quads=p.getAsJsonArray("quads");if(positions.size()%3!=0||positions.size()>24_000||quads.size()%4!=0||quads.size()>16_000||(budget[0]+=quads.size())>40_000)throw new IllegalArgumentException("Mesh budget exceeded");array(positions,positions.size(),100);array(normals,positions.size(),1.01);array(colors,positions.size(),1);for(var q:quads)if(q.getAsInt()<0||q.getAsInt()*3>=positions.size())throw new IllegalArgumentException("Invalid vertex index");double opacity=p.getAsJsonObject("material").get("opacity").getAsDouble();if(!Double.isFinite(opacity)||opacity<0||opacity>1)throw new IllegalArgumentException("Invalid opacity");if(p.has("texture"))throw new IllegalArgumentException("Custom texture references must be baked into vertex colors");if(p.has("motion")){JsonObject m=p.getAsJsonObject("motion");array(m.getAsJsonArray("pivot"),3,100);String type=m.has("type")?text(m,"type",16):"oscillate",axis=m.has("axis")?text(m,"axis",1):"y";if(!Set.of("wing","oscillate").contains(type)||!Set.of("x","y","z").contains(axis))throw new IllegalArgumentException("Unknown motion");for(String k:List.of("amplitude","speed","phase","duration"))if(m.has(k)){double n=m.get(k).getAsDouble();if(!Double.isFinite(n)||Math.abs(n)>10)throw new IllegalArgumentException("Invalid motion");}if(m.has("keyframes")){JsonArray keys=m.getAsJsonArray("keyframes");if(keys.size()>24)throw new IllegalArgumentException("Too many motion keys");double prev=-1;for(var key:keys){var k=key.getAsJsonObject();double t=k.get("time").getAsDouble(),a=k.get("angle").getAsDouble();if(!Double.isFinite(t)||!Double.isFinite(a)||t<0||t>1||t<=prev||Math.abs(a)>3)throw new IllegalArgumentException("Invalid motion key");prev=t;}}}}}
 /** One in-flight immutable content transfer per sender, bounded and expiring. */
 public static final class Transfer {
  private String hash="";private byte[][] parts;private int bytes,received;private long expires;
  public JsonObject accept(JsonObject p,long now){byte[] packed=acceptPacked(p,now);return packed==null?null:unpack(packed);}
  public byte[] acceptPacked(JsonObject p,long now){String next=text(p,"hash",64);if(!next.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Invalid hash");int count=p.get("count").getAsInt(),index=p.get("index").getAsInt();if(count<1||count>16||index<0||index>=count)throw new IllegalArgumentException("Invalid chunk index");byte[] part=Base64.getDecoder().decode(text(p,"data",PART_BYTES*2));if(part.length>PART_BYTES)throw new IllegalArgumentException("Chunk too large");if(parts==null||now>expires||!hash.equals(next)){if(index!=0)throw new IllegalArgumentException("Transfer must start at zero");hash=next;parts=new byte[count][];bytes=received=0;expires=now+5_000;}if(parts.length!=count)throw new IllegalArgumentException("Inconsistent chunk count");if(parts[index]!=null)return null;parts[index]=part;bytes+=part.length;received++;if(bytes>MAX_PACKED){clear();throw new IllegalArgumentException("Packed budget exceeded");}if(received!=count)return null;var out=new ByteArrayOutputStream(bytes);for(var value:parts)out.writeBytes(value);byte[] packed=out.toByteArray();String digest=hash;clear();if(!SocialRules.hash(packed).equals(digest))throw new IllegalArgumentException("Content hash mismatch");return packed;}
  public void clear(){hash="";parts=null;bytes=received=0;expires=0;}
 }
 /** Tick/time based token bucket; no per-frame work and no unbounded sender history. */
 public static final class Limiter {private double tokens;private long previous;private final double capacity,perMs;public Limiter(double capacity,double perSecond,long now){this.capacity=capacity;this.perMs=perSecond/1000;tokens=capacity;previous=now;}public boolean take(long now,double cost){tokens=Math.min(capacity,tokens+Math.max(0,now-previous)*perMs);previous=Math.max(previous,now);if(cost>tokens)return false;tokens-=cost;return true;}}
}
