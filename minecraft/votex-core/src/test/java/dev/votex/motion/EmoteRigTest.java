package dev.votex.motion;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.Arrays;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Geometry assertions use independent matrix multiplication, not expected signs alone. */
class EmoteRigTest {
 private static double[] rotateX(double[] p,double a){return new double[]{p[0],p[1]*Math.cos(a)-p[2]*Math.sin(a),p[1]*Math.sin(a)+p[2]*Math.cos(a)};}
 private static double[] rotateY(double[] p,double a){return new double[]{p[0]*Math.cos(a)+p[2]*Math.sin(a),p[1],-p[0]*Math.sin(a)+p[2]*Math.cos(a)};}
 private static double[] rotateZ(double[] p,double a){return new double[]{p[0]*Math.cos(a)-p[1]*Math.sin(a),p[0]*Math.sin(a)+p[1]*Math.cos(a),p[2]};}
 private static double[] source(double[] vector,double x,double y,double z){return rotateX(rotateY(rotateZ(vector,z),y),x);}
 private static double[] nativeVector(double[] vector,double[] angles){return rotateZ(rotateY(rotateX(vector,angles[0]),angles[1]),angles[2]);}
 private static double[] hand(int joint,double[] pose){double[] rotation=new double[3];EmoteRig.rotation(joint,pose,rotation);return nativeVector(new double[]{0,10,0},rotation);}

 @Test void multiAxisAndGimbalPosesMatchTheLauncherGeometry(){
  var random=new Random(714);double[] nativeAngles=new double[3];
  for(int i=0;i<3000;i++){
   double x=random.nextDouble(-3,3),y=i<3?new double[]{Math.PI/2,-Math.PI/2,0}[i]:random.nextDouble(-3,3),z=random.nextDouble(-3,3);
   EmoteRig.nativeRotation(x,y,z,nativeAngles,0);
   for(double[] vector:new double[][]{{1,0,0},{0,-10,0},{2,3,-4}}){
    double[] expected=source(vector,x,y,z);expected[1]*=-1;expected[2]*=-1;
    double[] actual=nativeVector(new double[]{vector[0],-vector[1],-vector[2]},nativeAngles);
    assertArrayEquals(expected,actual,1e-8,"basis/order conversion");
   }
  }
 }
 @Test void waveRaisesRightArmOutsideItsOwnShoulderForTheWholeCycle(){
  for(int i=0;i<100;i++){double[] hand=hand(EmoteRig.RIGHT_ARM,EmoteTimeline.raw("wave",i*.064));assertTrue(hand[0]<-4,"right hand must be outside the right shoulder");assertTrue(hand[1]<-4,"hand must be above the shoulder");}
 }
 @Test void celebrationRaisesBothHandsOutwardAndSittingPutsLegsInFront(){
  for(int i=0;i<100;i++){double[] pose=EmoteTimeline.raw("celebrate",i*.064),left=hand(EmoteRig.LEFT_ARM,pose),right=hand(EmoteRig.RIGHT_ARM,pose);assertTrue(left[0]>4&&right[0]<-4);assertTrue(left[1]<-7&&right[1]<-7);}
  double[] pose=EmoteTimeline.raw("sit",2),angles=new double[3],offset=new double[3];EmoteRig.rotation(EmoteRig.LEFT_LEG,pose,angles);double[] foot=nativeVector(new double[]{0,12,0},angles);assertTrue(foot[2]<-11,"feet in front rather than behind player");EmoteRig.offset(EmoteRig.BODY,pose,offset);assertEquals(7.5,offset[1],1e-12,"negative authored lift lowers player in Minecraft");
 }
 @Test void pointAndHandshakeExtendInFrontInsteadOfIntoTheTorso(){for(String id:new String[]{"point","handshake"}){double[] p=hand(EmoteRig.RIGHT_ARM,EmoteTimeline.raw(id,1));assertTrue(p[2]<-8,id+" should extend along native forward -Z");assertTrue(Math.abs(p[0])<4,id+" should stay close to shoulder X");}}
 @Test void bowKeepsTheHeadAndTorsoAtTheSameNeckPivot(){double[] p=EmoteTimeline.raw("bow",EmoteTimeline.DURATION/2),head=new double[3],body=new double[3];EmoteRig.offset(EmoteRig.HEAD,p,head);EmoteRig.offset(EmoteRig.BODY,p,body);assertArrayEquals(body,head,1e-12);assertTrue(head[2]<-5);}
 @Test void allGestureAndCustomInputsStayFiniteAndReturnWithoutDrift(){
  String[] all=Arrays.copyOf(EmoteTimeline.IDS,9);all[8]="handshake";
  for(String id:all){var clip=new EmoteTimeline();clip.request(id);double[] angles=new double[3],offset=new double[3];for(int i=0;i<1200;i++){double[] p=clip.stepInto(1d/120);for(int joint=0;joint<6;joint++){EmoteRig.rotation(joint,p,angles);EmoteRig.offset(joint,p,offset);for(double v:angles)assertTrue(Double.isFinite(v));for(double v:offset)assertTrue(Double.isFinite(v));}}assertTrue(clip.settled(),id);for(double v:clip.stepInto(0))assertEquals(0,v,.002);}
 }
 private JsonObject custom(String id,double angle){var item=new JsonObject();item.addProperty("id",id);item.addProperty("name","Eigene Geste");item.addProperty("duration",2);var keys=new JsonArray();for(int i=0;i<3;i++){var key=new JsonObject();key.addProperty("time",i*.5);var values=new JsonArray();for(int k=0;k<19;k++)values.add(k==11?angle:0);key.add("pose",values);keys.add(key);}item.add("keyframes",keys);return item;}
 @Test void remoteCustomLibrariesCannotReplaceLocalOrOtherSenders(){
  String id="custom-emote-aaaa";var local=new JsonArray();local.add(custom(id,-2));EmoteTimeline.setCustom(local);
  var first=new JsonArray();first.add(custom(id,1));var second=new JsonArray();second.add(custom(id,-1));
  var a=new EmoteTimeline(EmoteTimeline.parseLibrary(first,8));var b=new EmoteTimeline(EmoteTimeline.parseLibrary(second,8));var own=new EmoteTimeline();a.request(id);b.request(id);own.request(id);
  double[] ap=null,bp=null,op=null;for(int i=0;i<120;i++){ap=a.step(.01);bp=b.step(.01);op=own.step(.01);}assertEquals(1,ap[11],.01);assertEquals(-1,bp[11],.01);assertEquals(-2,op[11],.01);a.request(null);for(int i=0;i<200;i++)a.stepInto(.01);assertTrue(a.settled());
  assertEquals(-2,EmoteTimeline.customDefinitions().get(0).getAsJsonObject().getAsJsonArray("keyframes").get(0).getAsJsonObject().getAsJsonArray("pose").get(11).getAsDouble());EmoteTimeline.setCustom(new JsonArray());
 }
 @Test void remoteCustomLibrariesRejectInvalidAndOversizedData(){var items=new JsonArray();for(int i=0;i<9;i++)items.add(custom("custom-emote-aaaa",1));assertFalse(EmoteTimeline.parseLibrary(items,8).contains("custom-emote-aaaa"));var bad=new JsonArray();bad.add(custom("../unsafe",1));bad.add(custom("custom-emote-bbbb",Double.NaN));assertFalse(EmoteTimeline.parseLibrary(bad,8).contains("custom-emote-bbbb"));}
 @Test void previewSnapshotsAreDistinctAndDoNotAdvanceOrReplacePlayback(){var clip=new EmoteTimeline();clip.request("wave");double[] first=clip.step(.01),snapshot=first.clone(),preview=EmoteTimeline.preview("sit",1.2);assertEquals(-7.5,preview[18]);preview[18]=99;assertEquals(-7.5,EmoteTimeline.preview("sit",1.2)[18]);assertArrayEquals(snapshot,first);assertArrayEquals(EmoteTimeline.raw("wave",1.2),EmoteTimeline.preview("wave",1.2));}
}
