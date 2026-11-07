package dev.votex.chat;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
final class DuplicateGroupsTest {
 record Entry(String text,String source){}
 @Test void stacksOnlyConsecutiveEqualSourceAndContents(){var a=new Entry("hello","alice");var b=new Entry("hello","alice");var c=new Entry("hello","bob");var d=new Entry("hello","alice");var groups=new DuplicateGroups<Entry>();groups.rebuild(List.of(a,b,c,d),x->List.of(x.source(),x.text()));assertEquals(2,groups.count(a));assertEquals(0,groups.count(b));assertEquals(1,groups.count(c));assertEquals(1,groups.count(d));assertEquals("hello",a.text());}
 @Test void originalObjectsRemainDistinctAndRebuildReleasesOldHistory(){var a=new Entry("hello","alice");var b=new Entry("hello","alice");var g=new DuplicateGroups<Entry>();g.rebuild(List.of(a,b),Entry::text);g.rebuild(List.of(b),Entry::text);assertEquals(1,g.count(a));assertEquals(1,g.count(b));g.clear();assertEquals(1,g.count(b));}
 @Test void queryIsCaseInsensitiveAndOnlySafeReplyNamesPass(){assertTrue(DuplicateGroups.matches("Hallo Spieler Ähre","  ÄHRE  "));assertFalse(DuplicateGroups.matches("Hallo","Spieler"));assertEquals("_Votex",DuplicateGroups.safeName("_Votex"));assertEquals("",DuplicateGroups.safeName("Alice run op"));assertEquals("",DuplicateGroups.safeName("Bob\n/op"));}
}
