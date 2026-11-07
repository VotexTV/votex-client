package dev.votex.ui;
/** Exact underdamped second-order response, retaining velocity on target reversals. */
public final class DampedSpring {
 private double value,velocity;
 public DampedSpring(double initial){value=initial;}
 public double value(){return value;}
 public void reset(double target){value=target;velocity=0;}
 public void step(double target,double seconds){double dt=Math.clamp(Double.isFinite(seconds)?seconds:0,0,.1),omega=22,zeta=.72,wd=omega*Math.sqrt(1-zeta*zeta),offset=value-target,b=(velocity+zeta*omega*offset)/wd,decay=Math.exp(-zeta*omega*dt),sin=Math.sin(wd*dt),cos=Math.cos(wd*dt);value=target+decay*(offset*cos+b*sin);velocity=decay*((-zeta*omega)*(offset*cos+b*sin)+wd*(-offset*sin+b*cos));}
}
