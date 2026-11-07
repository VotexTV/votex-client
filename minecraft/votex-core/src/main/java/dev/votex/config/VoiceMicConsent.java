package dev.votex.config;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.*;
import java.util.Map;

/** A device permission, separate from the voice mod's mute/disable and onboarding settings. */
public final class VoiceMicConsent {
 private final Path file;
 private volatile boolean allowed;
 private VoiceMicConsent(Path file,boolean allowed){this.file=file;this.allowed=allowed;}
 public static VoiceMicConsent read(Path file)throws IOException{
  if(!Files.exists(file,LinkOption.NOFOLLOW_LINKS))return new VoiceMicConsent(file,false);
  if(Files.isSymbolicLink(file)||!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||Files.size(file)>4096)throw new IOException("Invalid microphone permission file");
  try{var json=JsonParser.parseString(Files.readString(file)).getAsJsonObject();
   if(!json.has("schemaVersion")||!json.get("schemaVersion").isJsonPrimitive()||!json.getAsJsonPrimitive("schemaVersion").isNumber()||!json.get("schemaVersion").getAsString().equals("1")||!json.has("allowed")||!json.get("allowed").isJsonPrimitive()||!json.getAsJsonPrimitive("allowed").isBoolean())throw new IOException("Invalid microphone permission schema");
   return new VoiceMicConsent(file,json.get("allowed").getAsBoolean());
  }catch(RuntimeException invalid){throw new IOException("Invalid microphone permission file",invalid);}
 }
 public static VoiceMicConsent denied(Path file){return new VoiceMicConsent(file,false);}
 public boolean allowed(){return allowed;}
 public synchronized void setAllowed(boolean next)throws IOException{
  // A failed write can never grant device permission; revocation takes effect in memory first.
  allowed=false;
  if(Files.isSymbolicLink(file))throw new IOException("Microphone permission must not be a symbolic link");
  AtomicJson.write(file,Map.of("schemaVersion",1,"allowed",next));allowed=next;
 }
}
