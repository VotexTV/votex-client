import { describe, expect, it } from "vitest";
import { baseTheme } from "../packages/shared-config/schema";
import { advanceMagneticState, effectActivity, interactionResponse } from "../packages/shared-config/visuals";
describe("shared hover policy", () => {
 it("turns mouse tracking off immediately for each independent off switch", () => {
  const active={...baseTheme,proximity:90,proximityEffect:true};
  expect(effectActivity(active).proximity).toBe(true);
  for(const value of [{proximityEffect:false},{hover:false},{reducedMotion:true},{proximity:0}])expect(effectActivity({...active,...value}).proximity).toBe(false);
 });
 it("settles at different frame rates with the configured response", () => {
  const response=interactionResponse({...baseTheme,speed:45,stiffness:42,damping:55});
  const simulate=(fps:number)=>{let state={x:0,y:0,velocityX:0,velocityY:0};for(let i=0;i<fps;i++)state=advanceMagneticState(state,{x:3,y:-2},1/fps,4,response);return state;};
  const a=simulate(60),b=simulate(144);expect(a.x).toBeCloseTo(b.x,9);expect(a.y).toBeCloseTo(b.y,9);
  expect(interactionResponse({...baseTheme,speed:100,stiffness:100,damping:0})).toBeGreaterThan(interactionResponse({...baseTheme,speed:0,stiffness:0,damping:100}));
 });
});
