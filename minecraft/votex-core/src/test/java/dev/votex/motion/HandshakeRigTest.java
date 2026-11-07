package dev.votex.motion;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HandshakeRigTest {
 private static double[] palmPair(HandshakeRig.Plan plan,boolean first,double time,double pairYaw){
  // B is located distance along A's forward vector and faces A.
  double[] p=new double[5];plan.pose(first,time,p);double s=first?plan.scaleA():plan.scaleB(),u=HandshakeRig.PLAYER_MODEL_SCALE*s/16,c=HandshakeRig.gripX(first?plan.slimA():plan.slimB());
  double rotatedX=c*Math.cos(p[2])+HandshakeRig.GRIP_Y*Math.sin(p[1])*Math.sin(p[2]),rotatedY=HandshakeRig.GRIP_Y*Math.cos(p[1]),rotatedZ=-c*Math.sin(p[2])+HandshakeRig.GRIP_Y*Math.sin(p[1])*Math.cos(p[2]);
  double x=(-5+rotatedX)*u,y=(first?0:plan.deltaY())+HandshakeRig.PLAYER_MODEL_SCALE*s*HandshakeRig.MODEL_ORIGIN-(12-10*Math.cos(p[0])+rotatedY)*u,z=(-10*Math.sin(p[0])+rotatedZ)*u;
  double body=Math.toRadians(180-(pairYaw+(first?0:180))),wx=-x*Math.cos(body)+z*Math.sin(body),wz=x*Math.sin(body)+z*Math.cos(body);
  if(!first){wx-=Math.sin(Math.toRadians(pairYaw))*plan.distance();wz+=Math.cos(Math.toRadians(pairYaw))*plan.distance();}
  return new double[]{wx,y,wz};
 }
 @Test void bothPalmsShareOneWorldPointAcrossRangeClassicSlimAndEveryBodyHeading(){
  for(boolean a:new boolean[]{false,true})for(boolean b:new boolean[]{false,true})for(double distance:new double[]{.6,.8,1,1.3,1.6,1.8}){
   var plan=HandshakeRig.plan(distance,0,1,1,a,b);assertNotNull(plan,"Flat eligible distance "+distance);
   for(double time:new double[]{.8,1,1.4,2,2.6})for(double heading:new double[]{-179,-90,-30,0,45,90,179}){
    double[] pa=palmPair(plan,true,time,heading),pb=palmPair(plan,false,time,heading);assertArrayEquals(pa,pb,1e-10,"same actual fixed-length palm point");assertEquals(plan.height()+HandshakeRig.pump(time),pa[1],1e-10);
   }
  }
 }
 @Test void smallVerticalOffsetsAndDifferentAdultModelScalesStillHaveContact(){
  for(double dy:new double[]{-.25,-.1,.1,.25})for(double distance:new double[]{.7,1,1.3,1.6}){
   var plan=HandshakeRig.plan(distance,dy,1,.95,false,true);assertNotNull(plan,"Ordinary reachable step "+dy+" at "+distance);for(int i=0;i<50;i++)assertArrayEquals(palmPair(plan,true,i*.064,67),palmPair(plan,false,i*.064,67),1e-10);
  }
 }
 @Test void solverKeepsFeetAndBoneLengthsAndRejectsPhysicallyUnreachableStances(){
  assertNull(HandshakeRig.plan(.1,0,1,1,false,false));assertNull(HandshakeRig.plan(.5,1.7,1,1,false,false));assertNull(HandshakeRig.plan(1.81,0,1,1,false,false));assertNull(HandshakeRig.plan(Double.NaN,0,1,1,false,false));
  var plan=HandshakeRig.plan(1.8,0,1,1,false,true);double[] p=new double[5];plan.pose(true,1,p);assertTrue(p[0]<=HandshakeRig.MAX_LEAN);assertTrue(p[0]>.7,"long reach needs honest torso lean");assertEquals(0,p[3]);assertEquals(0,HandshakeRig.blend(0));assertEquals(1,HandshakeRig.blend(1));assertEquals(0,HandshakeRig.blend(3.2));
 }
 @Test void canonicalParticipantSwapProducesTheSameContactHeight(){for(double dy:new double[]{-.3,0,.3}){var a=HandshakeRig.plan(1.2,dy,1,.95,false,true);var b=HandshakeRig.plan(1.2,-dy,.95,1,true,false);assertNotNull(a);assertNotNull(b);assertEquals(a.height(),b.height()+dy,.0000001);}}
}
