package dev.votex.performance;

/** Stable observation windows. Only optional Votex effects can be adjusted; no Minecraft settings or simulation. */
public final class OptimizationCoordinator {
 public enum Mode {DISABLED,CONSERVATIVE,BALANCED,AGGRESSIVE}
 private int bad,good,tier;private long changed=Long.MIN_VALUE/2;
 public int observe(double frameMs,double targetMs,Mode mode,long seconds){
  if(mode==Mode.DISABLED){bad=good=tier=0;return 0;}
  double threshold=mode==Mode.CONSERVATIVE?1.8:mode==Mode.BALANCED?1.3:1.1;
  if(frameMs>targetMs*threshold){bad++;good=0;}else if(frameMs<targetMs*.85){good++;bad=0;}else{bad=good=0;}
  if(seconds-changed>=15){if(bad>=5&&tier<2){tier++;bad=good=0;changed=seconds;}else if(good>=10&&tier>0){tier--;bad=good=0;changed=seconds;}}
  return tier;
 }
 public int tier(){return tier;}
 public static double targetFrameMs(int requested,int cap,int refreshRate,boolean vsync){int target=Math.max(1,requested);if(cap>0)target=Math.min(target,cap);if(vsync&&refreshRate>0)target=Math.min(target,refreshRate);return 1000d/target;}
}
