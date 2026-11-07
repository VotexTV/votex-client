package dev.votex.motion;

/**
 * The shared 19-channel pose uses the launcher's right-handed, Y-up skin rig and
 * XYZ Euler order. Minecraft 26.2 models use Y-down/Z-back and ZYX Euler order.
 * A sign change alone only works for a single axis: compose the authored matrix
 * and decompose the changed basis so multi-axis editor poses stay identical.
 */
public final class EmoteRig {
 private EmoteRig(){}
 public static final int HEAD=0,BODY=1,LEFT_ARM=2,RIGHT_ARM=3,LEFT_LEG=4,RIGHT_LEG=5;

 /** Writes native x/y/z rotations. Input and output may be the same array. */
 public static void nativeRotation(double x,double y,double z,double[] out,int offset){
  double cx=Math.cos(x),sx=Math.sin(x),cy=Math.cos(y),sy=Math.sin(y),cz=Math.cos(z),sz=Math.sin(z);
  // C * (Rx * Ry * Rz) * C, C = diag(1,-1,-1).
  double m00=cy*cz,m10=-(cx*sz+sx*sy*cz),m20=-(sx*sz-cx*sy*cz);
  double m01=cy*sz,m11=cx*cz-sx*sy*sz,m21=sx*cz+cx*sy*sz,m22=cx*cy;
  double ny=Math.asin(Math.clamp(-m20,-1,1));
  if(Math.abs(Math.cos(ny))>1e-7){out[offset]=Math.atan2(m21,m22);out[offset+2]=Math.atan2(m10,m00);}
  else {out[offset]=0;out[offset+2]=Math.atan2(-m01,m11);}
  out[offset+1]=ny;
 }

 /** Native offset from the fresh vanilla joint pivot, in model pixels. */
 public static void offset(int joint,double[] pose,double[] out){
  double bend=pose[3],roll=pose[5];out[0]=0;out[1]=-pose[18];out[2]=0;
  switch(joint){
   case HEAD->{out[0]=-Math.sin(roll)*9;out[1]+=12-12*Math.cos(bend);out[2]=-12*Math.sin(bend);}
   case BODY->{out[1]+=12-12*Math.cos(bend);out[2]=-12*Math.sin(bend);}
   case LEFT_ARM->{out[1]+=10-10*Math.cos(bend)-Math.sin(roll)*5;out[2]=-10*Math.sin(bend);}
   case RIGHT_ARM->{out[1]+=10-10*Math.cos(bend)+Math.sin(roll)*5;out[2]=-10*Math.sin(bend);}
   default->{}
  }
 }

 public static void rotation(int joint,double[] pose,double[] out){
  double z=pose[joint*3+2]+(joint==HEAD?pose[5]*.4:joint==LEFT_ARM||joint==RIGHT_ARM?pose[5]*.5:0);
  nativeRotation(pose[joint*3],pose[joint*3+1],z,out,0);
 }
}
