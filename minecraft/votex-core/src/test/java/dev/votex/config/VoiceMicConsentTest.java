package dev.votex.config;

import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

final class VoiceMicConsentTest {
 @TempDir Path directory;
 @Test void absentPermissionNeverOpensDeviceAndExplicitChoicePersists()throws Exception{Path file=directory.resolve("voice-microphone.json");var policy=VoiceMicConsent.read(file);assertFalse(policy.allowed());assertFalse(Files.exists(file));policy.setAllowed(true);assertTrue(policy.allowed());assertTrue(VoiceMicConsent.read(file).allowed());policy.setAllowed(false);assertFalse(policy.allowed());assertFalse(VoiceMicConsent.read(file).allowed());}
 @Test void invalidOrOutdatedPermissionIsNeverInterpretedAsConsent()throws Exception{Path file=directory.resolve("voice-microphone.json");for(String invalid:new String[]{"{}","null","{\"schemaVersion\":2,\"allowed\":true}","{\"schemaVersion\":1,\"allowed\":\"true\"}","{\"schemaVersion\":\"1\",\"allowed\":true}","[true]"}){Files.writeString(file,invalid);assertThrows(java.io.IOException.class,()->VoiceMicConsent.read(file));}Files.writeString(file," ".repeat(4097));assertThrows(java.io.IOException.class,()->VoiceMicConsent.read(file));}
 @Test void failedPersistenceDoesNotGrantPermission()throws Exception{Path blocker=directory.resolve("not-directory");Files.writeString(blocker,"blocked");var policy=VoiceMicConsent.denied(blocker.resolve("voice-microphone.json"));assertThrows(java.io.IOException.class,()->policy.setAllowed(true));assertFalse(policy.allowed());}
 @Test void failedRevocationStillDeniesCurrentProcess()throws Exception{Path file=directory.resolve("voice-microphone.json");var policy=VoiceMicConsent.read(file);policy.setAllowed(true);Files.delete(file);Files.createDirectory(file);assertThrows(java.io.IOException.class,()->policy.setAllowed(false));assertFalse(policy.allowed());}
}
