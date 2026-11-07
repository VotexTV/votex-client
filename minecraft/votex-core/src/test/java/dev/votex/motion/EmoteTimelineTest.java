package dev.votex.motion;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class EmoteTimelineTest {
 @Test void borrowedAndOwnedPathsProduceIdenticalPosesWithoutMutatingSnapshots(){var owned=new EmoteTimeline();var borrowed=new EmoteTimeline();owned.request("dance");borrowed.request("dance");double[] first=owned.step(.01),snapshot=first.clone();borrowed.stepInto(.01);double[] previous=null;for(int i=0;i<240;i++){double[] a=owned.step(1d/120),b=borrowed.stepInto(1d/120);assertArrayEquals(a,b,1e-12);if(previous!=null)assertSame(previous,b);previous=b;}assertArrayEquals(snapshot,first);}
 private double[] frames(EmoteTimeline m,double seconds){double[] p=null;for(int i=0;i<(int)(seconds*120);i++)p=m.step(1.0/120);return p;}
 @Test void easedEndpointsHaveNoHardSlope(){assertEquals(0,EmoteTimeline.envelope(0));assertEquals(0,EmoteTimeline.envelope(EmoteTimeline.DURATION));assertEquals(1,EmoteTimeline.envelope(EmoteTimeline.ENTER),1e-10);assertTrue(EmoteTimeline.envelope(.0001)/.0001<.0001);assertTrue(EmoteTimeline.envelope(EmoteTimeline.DURATION-.0001)/.0001<.0001);}
 @Test void switchingReplayingAndInterruptingPreserveThePose(){var m=new EmoteTimeline();m.request("wave");double[] before=frames(m,1.3);for(String next:new String[]{"sit","sit","dance",null}){m.request(next);double[] after=m.step(1.0/240);for(int k=0;k<after.length;k++)assertTrue(Math.abs(after[k]-before[k])<.06);before=frames(m,.8);}double[] idle=frames(m,2);for(double v:idle)assertTrue(Math.abs(v)<.002);assertTrue(m.settled());assertFalse(m.running());}
 @Test void allGesturesReturnToVanillaAfterAFullPerformance(){for(String id:EmoteTimeline.IDS){var m=new EmoteTimeline();m.request(id);double[] active=frames(m,2);assertTrue(java.util.Arrays.stream(active).anyMatch(v->Math.abs(v)>.01));double[] idle=frames(m,7);for(double v:idle){assertTrue(Double.isFinite(v));assertTrue(Math.abs(v)<.002);}assertTrue(m.settled());assertFalse(m.running());}}
}
