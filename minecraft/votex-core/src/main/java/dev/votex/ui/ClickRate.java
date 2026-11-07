package dev.votex.ui;
/** Bounded one-second physical press history, with an injectable monotonic clock. */
public final class ClickRate {
 private final long[][] events={new long[256],new long[256]};private final int[] next=new int[2],counts=new int[2];
 public void press(int button,long ns){if(button<0||button>1)return;int n=next[button];events[button][n]=ns;next[button]=(n+1)%256;counts[button]=Math.min(256,counts[button]+1);}
 public int count(int button,long now){int result=0;for(int i=0;i<counts[button];i++){long age=now-events[button][i];if(age>=0&&age<1_000_000_000L)result++;}return result;}
}
