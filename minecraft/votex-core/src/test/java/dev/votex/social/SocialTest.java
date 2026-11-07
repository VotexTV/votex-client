package dev.votex.social;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.io.*;
import java.util.zip.DeflaterOutputStream;
import static org.junit.jupiter.api.Assertions.*;

class SocialTest {
 @Test void optionalSkinModelSurvivesTransportAndRejectsUnknownVariants(){
  for(String model:List.of("default","slim")){var value=appearance();value.addProperty("skinModel",model);assertEquals(model,SocialRules.unpack(SocialRules.pack(SocialRules.validate(value))).get("skinModel").getAsString());}
  var invalid=appearance();invalid.addProperty("skinModel","arbitrary-model");assertThrows(RuntimeException.class,()->SocialRules.validate(invalid));
  assertFalse(SocialRules.validate(appearance()).has("skinModel"));
 }
 static JsonObject appearance(){JsonObject a=new JsonObject(),look=new JsonObject(),equipment=new JsonObject();equipment.addProperty("wings","cloud-feathers");look.add("equipment",equipment);look.addProperty("scale",100);look.addProperty("nametag","Votex Client");a.add("look",look);return a;}
 @Test void builtInLookRoundTripsWithoutPrivateMetadata(){var a=appearance();a.getAsJsonObject("look").addProperty("privateLocalPath","C:/private.png");var result=SocialRules.unpack(SocialRules.pack(SocialRules.validate(a)));assertEquals("cloud-feathers",result.getAsJsonObject("look").getAsJsonObject("equipment").get("wings").getAsString());assertFalse(result.getAsJsonObject("look").has("privateLocalPath"));assertEquals("Votex Client",result.getAsJsonObject("look").get("nametag").getAsString());}
 @Test void clearedSlotDoesNotPreventOtherCosmeticsFromReachingPeers(){
  var sender=appearance();
  var slots=sender.getAsJsonObject("look").getAsJsonObject("equipment");
  slots.addProperty("cape","");
  slots.addProperty("head","");
  var receiver=new SocialRules.Transfer();
  JsonObject received=null;
  for(var packet:SocialRules.chunks(sender))received=receiver.accept(packet,1000);
  assertNotNull(received);
  var equipment=received.getAsJsonObject("look").getAsJsonObject("equipment");
  assertEquals("cloud-feathers",equipment.get("wings").getAsString());
  assertEquals(1,equipment.size());
  assertEquals("",slots.get("cape").getAsString());
  slots.addProperty("wings","");
  assertTrue(SocialRules.validate(sender).getAsJsonObject("look").getAsJsonObject("equipment").isEmpty());
 }
 @Test void wireHasVersionSizeAndNestingLimits(){assertEquals("hello",SocialRules.parse(SocialRules.message("hello").toString()).get("type").getAsString());assertThrows(RuntimeException.class,()->SocialRules.parse("x".repeat(SocialRules.MAX_WIRE+1)));assertThrows(RuntimeException.class,()->SocialRules.parse("{\"v\":2,\"type\":\"hello\"}"));assertThrows(RuntimeException.class,()->SocialRules.parse("{\"v\":1,\"type\":\"hello\",\"nested\":"+"[".repeat(1000)+"0"+"]".repeat(1000)+"}"));}
 @Test void unknownModelOrSlotCannotInjectResourcePath(){var a=appearance();a.getAsJsonObject("look").getAsJsonObject("equipment").addProperty("wings","../../evil");assertThrows(RuntimeException.class,()->SocialRules.validate(a));var b=appearance();b.getAsJsonObject("look").getAsJsonObject("equipment").addProperty("script","cloud-feathers");assertThrows(RuntimeException.class,()->SocialRules.validate(b));}
 @Test void rejectsNonFiniteScaleAndControlCharacters(){var a=appearance();a.getAsJsonObject("look").addProperty("scale",Double.NaN);assertThrows(RuntimeException.class,()->SocialRules.validate(a));var b=appearance();b.getAsJsonObject("look").addProperty("nametag","spoof\nname");assertThrows(RuntimeException.class,()->SocialRules.validate(b));}
 @Test void completeTransferRequiresDigestAndRecipientIndependentBytes(){var chunks=SocialRules.chunks(appearance());var transfer=new SocialRules.Transfer();JsonObject result=null;for(var p:chunks)result=transfer.accept(p,1000);assertNotNull(result);var corrupt=chunks.getFirst().deepCopy();corrupt.addProperty("hash","0".repeat(64));assertThrows(RuntimeException.class,()->new SocialRules.Transfer().accept(corrupt,1000));}
 @Test void duplicateChunkCannotExpandMemoryOrCompleteEarly(){var random=new Random(11);var text=new StringBuilder();for(int i=0;i<18000;i++)text.append(random.nextInt(10000)).append('/');byte[] raw=("{\"look\":{\"unused\":\""+text+"\",\"equipment\":{}}}").getBytes(java.nio.charset.StandardCharsets.UTF_8);byte[] packed;try{var out=new ByteArrayOutputStream();try(var zip=new DeflaterOutputStream(out)){zip.write(raw);}packed=out.toByteArray();}catch(IOException e){throw new AssertionError(e);}assertTrue(packed.length>SocialRules.PART_BYTES);var first=SocialRules.message("appearance-part");first.addProperty("hash",SocialRules.hash(packed));first.addProperty("index",0);int count=(packed.length+SocialRules.PART_BYTES-1)/SocialRules.PART_BYTES;first.addProperty("count",count);first.addProperty("data",Base64.getEncoder().encodeToString(Arrays.copyOfRange(packed,0,SocialRules.PART_BYTES)));var transfer=new SocialRules.Transfer();assertNull(transfer.accept(first,0));assertNull(transfer.accept(first,1));for(int i=1;i<count;i++){var p=first.deepCopy();p.addProperty("index",i);p.addProperty("data",Base64.getEncoder().encodeToString(Arrays.copyOfRange(packed,i*SocialRules.PART_BYTES,Math.min(packed.length,(i+1)*SocialRules.PART_BYTES))));JsonObject result=transfer.accept(p,2);if(i==count-1)assertNotNull(result);else assertNull(result);}}
 @Test void transferMustStartAtZeroAndCannotOutliveFiveSeconds(){var p=SocialRules.chunks(appearance()).getFirst().deepCopy();p.addProperty("count",2);p.addProperty("index",1);assertThrows(RuntimeException.class,()->new SocialRules.Transfer().accept(p,0));p.addProperty("index",0);var transfer=new SocialRules.Transfer();assertNull(transfer.accept(p,0));p.addProperty("index",1);assertThrows(RuntimeException.class,()->transfer.accept(p,5001));}
 @Test void decompressionHasExpandedByteBudget()throws Exception{byte[] huge=new byte[SocialRules.MAX_APPEARANCE+1];Arrays.fill(huge,(byte)' ');var out=new ByteArrayOutputStream();try(var deflater=new DeflaterOutputStream(out)){deflater.write(huge);}assertThrows(RuntimeException.class,()->SocialRules.unpack(out.toByteArray()));}
 @Test void validatesPngHeaderAndDimensionsBeforeNativeDecode(){byte[] fake=new byte[24];byte[] sig={(byte)137,80,78,71,13,10,26,10};System.arraycopy(sig,0,fake,0,8);var b=java.nio.ByteBuffer.wrap(fake);b.putInt(16,64);b.putInt(20,64);assertDoesNotThrow(()->SocialRules.validatePng(fake,64));assertThrows(RuntimeException.class,()->SocialRules.validatePng(fake,32));fake[0]=1;assertThrows(RuntimeException.class,()->SocialRules.validatePng(fake,64));}
 @Test void tokenIsBoundToRecipientAndCanOnlyBeUsedOnce(){var book=new HandshakeBook();UUID a=UUID.randomUUID(),b=UUID.randomUUID(),c=UUID.randomUUID();var invite=book.invite(a,b,0);assertNotNull(invite);assertNull(book.accept(c,invite.token(),1));assertNull(book.accept(b,UUID.randomUUID(),1));assertEquals(invite,book.accept(b,invite.token(),1));assertNull(book.accept(b,invite.token(),2));}
 @Test void consentCannotBeReplacedOrAcceptedAfterExpiry(){var book=new HandshakeBook();UUID a=UUID.randomUUID(),b=UUID.randomUUID(),c=UUID.randomUUID();var invite=book.invite(a,b,0);assertNull(book.invite(c,b,1));assertNull(book.accept(b,invite.token(),10_001));assertNotNull(book.invite(c,b,10_002));}
 @Test void invitationsHaveSenderCooldownAndDisconnectCleanup(){var book=new HandshakeBook();UUID a=UUID.randomUUID(),b=UUID.randomUUID(),c=UUID.randomUUID();assertNull(book.invite(a,a,0));assertNotNull(book.invite(a,b,0));assertNull(book.invite(a,c,4999));book.remove(b);assertEquals(0,book.pending());assertNotNull(book.invite(a,c,5000));book.remove(a);assertEquals(0,book.pending());}
 @Test void tokenBucketBoundsFloodAndRefillsWithoutClockRollback(){var limiter=new SocialRules.Limiter(4,2,1000);assertTrue(limiter.take(1000,4));assertFalse(limiter.take(1000,1));assertTrue(limiter.take(1500,1));assertFalse(limiter.take(1400,1));assertFalse(limiter.take(2400,2));assertTrue(limiter.take(2500,2));}
 @Test void customEmoteHasOrderedCompleteBoundedPose(){JsonObject emote=new JsonObject();emote.addProperty("id","custom-emote-a");emote.addProperty("name","Meine Geste");emote.addProperty("duration",3);JsonArray keys=new JsonArray();for(int i=0;i<2;i++){JsonObject frame=new JsonObject();frame.addProperty("time",i);JsonArray pose=new JsonArray();for(int n=0;n<19;n++)pose.add(0);frame.add("pose",pose);keys.add(frame);}emote.add("keyframes",keys);assertDoesNotThrow(()->SocialRules.validateEmote(emote));keys.get(1).getAsJsonObject().addProperty("time",0);assertThrows(RuntimeException.class,()->SocialRules.validateEmote(emote));}
}
