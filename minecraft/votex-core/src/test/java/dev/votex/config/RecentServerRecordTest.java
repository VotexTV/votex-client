package dev.votex.config;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class RecentServerRecordTest {
 @Test void successfulJoinRecordStoresOnlyValidatedServerMetadata(){var record=RecentServerRecord.create("main","Mein Server\n","MC.Example.org:25566",Instant.parse("2026-10-21T12:34:56Z"));assertEquals("mc.example.org:25566",record.get("address").getAsString());assertEquals("Mein Server",record.get("name").getAsString());assertEquals("main",record.get("profileId").getAsString());assertEquals(5,record.size());assertEquals("2026-10-21T12:34:56Z",record.get("joinedAt").getAsString());}
 @Test void addressValidationRejectsArgumentsUrlsAndMalformedPorts(){for(String bad:new String[]{"--server","https://example.org","example.org --demo","example.org:0","example.org:65536","host\n--username","localhost\n","999.2.3.4","[not-ipv6]"})assertThrows(IllegalArgumentException.class,()->RecentServerRecord.address(bad),bad);assertTrue(RecentServerRecord.address("[2001:db8::1]:25565").endsWith("]:25565"));assertEquals("xn--mller-kva.example",RecentServerRecord.address("müller.example"));}
}
