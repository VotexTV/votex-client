import dev.votex.motion.EmoteTimeline;
import java.lang.management.ManagementFactory;
/** Isolated allocation microbenchmark, not an FPS benchmark. No forced collection. */
public final class EmoteAllocationProbe {
 static volatile double blackhole;
 static void run(int count){var timeline=new EmoteTimeline();timeline.request("dance");double sum=0;for(int i=0;i<count;i++){if(i%240==0)timeline.request("dance");double[] pose=timeline.stepInto(1d/120);sum+=pose[i%19];}blackhole=sum;}
 public static void main(String[] args){var bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();long id=Thread.currentThread().threadId();for(int i=0;i<4;i++)run(100000);System.out.print("{\"java\":\""+System.getProperty("java.version")+"\",\"iterations\":500000,\"runs\":[");for(int i=0;i<3;i++){long bytes=bean.getThreadAllocatedBytes(id),start=System.nanoTime();run(500000);long ns=System.nanoTime()-start,allocated=bean.getThreadAllocatedBytes(id)-bytes;if(i>0)System.out.print(",");System.out.print("{\"nsPerStep\":"+(ns/500000d)+",\"bytesPerStep\":"+(allocated/500000d)+"}");}System.out.println("]}");}
}
