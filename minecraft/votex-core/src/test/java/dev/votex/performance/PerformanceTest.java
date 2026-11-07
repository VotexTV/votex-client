package dev.votex.performance;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
class PerformanceTest {
 @TempDir Path dir;
 @Test void reportsRealTailStatisticsAndSampleThresholds(){long[] frames=new long[1000];java.util.Arrays.fill(frames,10_000_000);frames[999]=110_000_000;var s=FrameStatistics.of(frames,1000);assertEquals(1000,s.samples());assertEquals(1000d/10.1,s.averageFps(),.001);assertEquals(100,s.medianFps());assertEquals(50,s.low1Fps());assertEquals(1000d/110,s.low01Fps(),.001);assertEquals(10,s.p99Ms());assertEquals(110,s.maxMs());assertEquals(1000,java.util.Arrays.stream(s.histogram()).sum());assertNull(FrameStatistics.of(frames,99).low1Fps());assertNull(FrameStatistics.of(frames,999).low01Fps());}
 @Test void adaptiveRequiresSustainedPressureAndCooldownAndRecovers(){var c=new OptimizationCoordinator();for(int i=0;i<4;i++)assertEquals(0,c.observe(40,10,OptimizationCoordinator.Mode.BALANCED,i));assertEquals(1,c.observe(40,10,OptimizationCoordinator.Mode.BALANCED,4));for(int i=5;i<19;i++)assertEquals(1,c.observe(40,10,OptimizationCoordinator.Mode.BALANCED,i));assertEquals(2,c.observe(40,10,OptimizationCoordinator.Mode.BALANCED,19));for(int i=20;i<34;i++)assertEquals(2,c.observe(5,10,OptimizationCoordinator.Mode.BALANCED,i));assertEquals(1,c.observe(5,10,OptimizationCoordinator.Mode.BALANCED,34));assertEquals(0,c.observe(40,10,OptimizationCoordinator.Mode.DISABLED,35));}
 @Test void invalidPerformanceConfigIsPreserved()throws Exception{Path p=dir.resolve("performance.json");Files.writeString(p,"{\"schemaVersion\":1,\"targetFps\":1}");assertThrows(java.io.IOException.class,()->PerformanceConfig.read(p));assertTrue(Files.readString(p).contains("targetFps"));assertEquals(OptimizationCoordinator.Mode.DISABLED,PerformanceConfig.read(dir.resolve("missing")).adaptive);}
 @Test void adaptiveBudgetRespectsIntentionalFpsCapsAndVsync(){assertEquals(1000d/60,OptimizationCoordinator.targetFrameMs(120,60,165,false));assertEquals(1000d/60,OptimizationCoordinator.targetFrameMs(240,260,60,true));assertEquals(1000d/120,OptimizationCoordinator.targetFrameMs(120,260,165,true));}
}
