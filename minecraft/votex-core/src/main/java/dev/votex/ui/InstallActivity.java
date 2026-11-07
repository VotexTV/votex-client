package dev.votex.ui;

/** Thread-safe operation identity: an old download cannot finish a newer operation. */
public final class InstallActivity {
 public record State(long id,String title,String detail,String phase,double progress,long changed){}
 private long serial;private volatile State state;
 public synchronized long begin(String title,long now){long id=++serial;state=new State(id,title,"Dateien und Abhängigkeiten prüfen …","working",-1,now);return id;}
 public State snapshot(){return state;}
 public synchronized void progress(long id,String detail,long completed,long total){if(state==null||state.id!=id||!state.phase.equals("working"))return;state=new State(id,state.title,detail,state.phase,total>0?Math.clamp(completed/(double)total,0,1):-1,state.changed);}
 public synchronized void finish(long id,boolean success,String detail,long now){if(state==null||state.id!=id||!state.phase.equals("working"))return;state=new State(id,state.title,detail,success?"ready":"error",success?1:-1,now);}
 public synchronized void expire(long now){if(state!=null&&!state.phase.equals("working")&&now-state.changed> (state.phase.equals("error")?15_000_000_000L:8_000_000_000L))state=null;}
}
