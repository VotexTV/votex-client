package dev.votex.motion;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Render-independent bounded gesture weights and inertial output. */
public final class EmoteTimeline {
 public static final String[] IDS={"wave","sit","dance","point","clap","salute","celebrate","bow"};
 private static final JsonObject TIMING=timing();
 public static final double DURATION=TIMING.get("duration").getAsDouble(),ENTER=TIMING.get("enter").getAsDouble(),EXIT=TIMING.get("exit").getAsDouble(),SPRING=TIMING.get("spring").getAsDouble();
 private record Frame(double time,double[] pose){}
 private record Custom(String name,double duration,List<Frame> frames){}
 private static final Map<String,Custom> CUSTOM=new LinkedHashMap<>();
 private final double[] starts=new double[8],weights=new double[8],velocity=new double[8],rendered=new double[19],inertia=new double[19];
 private double time,clock,amount,customWeight,customVelocity;private int active=-1;private String activeCustom;
 private static JsonObject timing(){try(var s=EmoteTimeline.class.getResourceAsStream("/assets/votex_client/emote-timing.json")){if(s==null)throw new IllegalStateException("Missing motion timing");return JsonParser.parseString(new String(s.readAllBytes(),StandardCharsets.UTF_8).replace("\ufeff","")).getAsJsonObject();}catch(Exception e){throw new IllegalStateException("Invalid motion timing",e);}}
 public static boolean valid(String id){return Arrays.asList(IDS).contains(id);}
 public static synchronized void setCustom(JsonArray values){
  CUSTOM.clear();if(values==null)return;
  if(values.size()>50)return;
  for(JsonElement value:values)try{
   JsonObject item=value.getAsJsonObject();String id=item.get("id").getAsString();if(!id.matches("custom-emote-[a-f0-9-]{1,40}"))continue;
   String name=item.get("name").getAsString();if(name.isBlank()||name.length()>48)continue;
   double duration=item.get("duration").getAsDouble();if(!Double.isFinite(duration)||duration<1||duration>10)continue;
   JsonArray source=item.getAsJsonArray("keyframes");if(source.size()<2||source.size()>24)continue;
   List<Frame> frames=new ArrayList<>();double previous=-1;boolean bad=false;
   for(JsonElement element:source){JsonObject frame=element.getAsJsonObject();double at=frame.get("time").getAsDouble();JsonArray poseJson=frame.getAsJsonArray("pose");if(!Double.isFinite(at)||at<=previous||poseJson.size()!=19){bad=true;break;}double[] pose=new double[19];for(int i=0;i<19;i++){pose[i]=poseJson.get(i).getAsDouble();if(!Double.isFinite(pose[i])||Math.abs(pose[i])>3){bad=true;break;}}if(bad)break;frames.add(new Frame(at,pose));previous=at;}
   if(!bad&&frames.getFirst().time()==0&&frames.getLast().time()==1)CUSTOM.put(id,new Custom(name,duration,List.copyOf(frames)));
  }catch(Exception ignored){}
 }
 public static synchronized boolean customValid(String id){return CUSTOM.containsKey(id);}
 public static synchronized String customName(String id){Custom item=CUSTOM.get(id);return item==null?null:item.name();}
 public void request(String id){
  active=valid(id)?Arrays.asList(IDS).indexOf(id):-1;
  synchronized(EmoteTimeline.class){activeCustom=CUSTOM.containsKey(id)?id:null;}
  if(active>=0||activeCustom!=null){clock=0;if(active>=0)starts[active]=time;}
 }
 public boolean running(){double duration=activeCustom==null?DURATION:customDuration(activeCustom);return !settled()||active>=0&&clock<duration+1.5||activeCustom!=null&&clock<duration+1.5;}
 public double amount(){return amount;}
 public static double smooth(double t){double v=Math.clamp(t,0,1);return v*v*v*(v*(v*6-15)+10);}
 public static double envelope(double t){return t<0||t>=DURATION?0:Math.min(smooth(t/ENTER),smooth((DURATION-t)/EXIT));}
 private static double envelope(double t,double duration){double edge=Math.min(.25,duration*.18);return t<0||t>=duration?0:Math.min(smooth(t/edge),smooth((duration-t)/edge));}
 private static synchronized double customDuration(String id){Custom item=CUSTOM.get(id);return item==null?0:item.duration();}
 public boolean settled(){for(int i=0;i<8;i++)if(Math.abs(weights[i])>=.0007||Math.abs(velocity[i])>=.006)return false;for(int i=0;i<19;i++)if(Math.abs(rendered[i])>=.0015||Math.abs(inertia[i])>=.008)return false;return Math.abs(customWeight)<.0007&&Math.abs(customVelocity)<.006;}
 public double[] step(double seconds){double dt=Math.clamp(seconds,0,.05);time+=dt;clock+=dt;double sum=0;
  for(int i=0;i<8;i++){double target=i==active?envelope(clock):0,x=weights[i]-target,decay=Math.exp(-SPRING*dt),temp=(velocity[i]+SPRING*x)*dt;velocity[i]=(velocity[i]-SPRING*temp)*decay;weights[i]=target+(x+temp)*decay;sum+=Math.max(0,weights[i]);}
  double customDuration=activeCustom==null?0:customDuration(activeCustom),customGoal=activeCustom==null?0:envelope(clock,customDuration),cx=customWeight-customGoal,cd=Math.exp(-SPRING*dt),ct=(customVelocity+SPRING*cx)*dt;customVelocity=(customVelocity-SPRING*ct)*cd;customWeight=customGoal+(cx+ct)*cd;sum+=Math.max(0,customWeight);
  amount=Math.clamp(sum,0,1);double scale=sum>1?1/sum:1;double[] next=new double[19];
  for(int i=0;i<8;i++){double[] pose=raw(IDS[i],time-starts[i]);for(int k=0;k<19;k++)next[k]+=pose[k]*Math.max(0,weights[i])*scale;}
  if(activeCustom!=null){double[] pose=customPose(activeCustom,clock);for(int k=0;k<19;k++)next[k]+=pose[k]*Math.max(0,customWeight)*scale;}
  for(int i=0;i<19;i++){double x=rendered[i]-next[i],decay=Math.exp(-22*dt),temp=(inertia[i]+22*x)*dt;inertia[i]=(inertia[i]-22*temp)*decay;rendered[i]=next[i]+(x+temp)*decay;}
  return rendered.clone();
 }
 private static synchronized double[] customPose(String id,double seconds){Custom custom=CUSTOM.get(id);if(custom==null)return new double[19];double t=Math.clamp(seconds/custom.duration(),0,1);List<Frame> frames=custom.frames();Frame left=frames.getFirst(),right=frames.getLast();for(int i=1;i<frames.size();i++)if(t<=frames.get(i).time()){left=frames.get(i-1);right=frames.get(i);break;}double blend=smooth((t-left.time())/Math.max(.0001,right.time()-left.time()));double[] pose=new double[19];for(int i=0;i<pose.length;i++)pose[i]=left.pose()[i]+(right.pose()[i]-left.pose()[i])*blend;return pose;}
 public static double[] raw(String id,double t){double[] p=new double[19];switch(id){
  case "wave"->{p[9]=-.18;p[11]=-2.35+Math.sin(t*7)*.24;p[1]=-.09;p[8]=.08;}
  case "sit"->{p[12]=p[15]=-1.42;p[6]=p[9]=-.18;p[8]=.12;p[11]=-.12;p[18]=-7.5;p[0]=.04;}
  case "dance"->{double beat=Math.sin(t*3.7);p[5]=beat*.12;p[6]=-.2+Math.sin(t*3.7+.6)*.22;p[9]=-.2-Math.sin(t*3.7+.6)*.22;p[8]=.42+beat*.2;p[11]=-.42+beat*.2;p[12]=beat*.22;p[15]=-beat*.22;p[18]=(1-Math.cos(t*7.4))*.3;p[1]=-beat*.12;}
  case "point"->{p[9]=-1.48;p[10]=-.15;p[1]=-.22;p[5]=-.025;}
  case "clap"->{p[6]=p[9]=-1.18;p[7]=-.16;p[10]=.16;double clap=(1-Math.cos(t*5.5))*.5;p[8]=-.24-clap*.36;p[11]=.24+clap*.36;p[0]=.04;}
  case "salute"->{p[9]=-1.87;p[11]=-.46;p[0]=Math.sin(t*2)*.035;}
  case "celebrate"->{p[8]=2.58+Math.sin(t*4.5)*.08;p[11]=-2.58-Math.sin(t*4.5)*.08;p[18]=(1-Math.cos(t*4.5))*.35;p[0]=-.1;}
  case "bow"->{double bend=.55*Math.sin(Math.PI*Math.min(1,t/DURATION));p[3]=bend;p[0]=bend*.35;p[6]=p[9]=-bend*.5;p[18]=-bend*.6;}
 }return p;}
}
