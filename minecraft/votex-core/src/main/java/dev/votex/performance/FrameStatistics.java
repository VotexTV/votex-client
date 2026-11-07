package dev.votex.performance;
import java.util.Arrays;

/** Computed off the render thread from a bounded capture. Low FPS = reciprocal of tail-mean frame time. */
public record FrameStatistics(int samples,double averageFps,double medianFps,Double low1Fps,Double low01Fps,
 double p50Ms,double p95Ms,double p99Ms,double maxMs,int[] histogram) {
 public static FrameStatistics of(long[] source,int count){
  if(count<1||count>source.length)throw new IllegalArgumentException("No valid frame samples");
  long[] a=Arrays.copyOf(source,count);long sum=0;int[] histogram=new int[8];
  double[] edges={4.17,8.33,16.67,25,33.33,50,100};
  for(long n:a){if(n<=0)throw new IllegalArgumentException("Invalid frame interval");sum+=n;int bin=0;while(bin<edges.length&&n/1e6>edges[bin])bin++;histogram[bin]++;}
  Arrays.sort(a);double p50=percentile(a,.5),p95=percentile(a,.95),p99=percentile(a,.99);
  return new FrameStatistics(count,1e9*count/sum,1e9/p50,count>=100?tail(a,.01):null,count>=1000?tail(a,.001):null,p50/1e6,p95/1e6,p99/1e6,a[count-1]/1e6,histogram);
 }
 private static double percentile(long[] a,double p){double at=(a.length-1)*p;int i=(int)at;return a[i]+(a[Math.min(i+1,a.length-1)]-a[i])*(at-i);}
 private static double tail(long[] a,double share){int n=Math.max(1,(int)Math.ceil(a.length*share));double sum=0;for(int i=a.length-n;i<a.length;i++)sum+=a[i];return 1e9*n/sum;}
}
