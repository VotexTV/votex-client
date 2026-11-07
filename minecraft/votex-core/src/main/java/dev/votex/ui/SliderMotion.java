package dev.votex.ui;
/** Deformation only: actual value and lens centre never trail the cursor. */
public final class SliderMotion {
 private final DampedSpring press=new DampedSpring(0),stretch=new DampedSpring(0),lean=new DampedSpring(0);private double previous;private boolean sampled;
 public void step(double value,boolean dragging,double dt,boolean animate){double speed=sampled&&dt>0?(value-previous)/dt:0;previous=value;sampled=true;if(!animate){press.reset(0);stretch.reset(0);lean.reset(0);return;}press.step(dragging?1:0,dt);stretch.step(dragging?Math.min(.13,Math.abs(speed)*.035):0,dt);lean.step(dragging?Math.clamp(speed*.025,-.035,.035):0,dt);}
 public float scaleX(){return (float)Math.clamp(1+press.value()*.055+stretch.value(),.97,1.20);}
 public float scaleY(){return (float)Math.clamp(1+press.value()*.07-stretch.value()*.45,.91,1.10);}
 public float angle(){return (float)lean.value();}
 public static double valueAt(double mouseX,int x,int width){return Math.clamp((mouseX-x-18)/Math.max(1,width-36),0,1);}
}
