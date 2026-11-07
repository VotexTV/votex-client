import { expect, it } from "vitest";
import { EmoteMotion } from "../apps/launcher/src/components/emotes";

it("keeps preview time at the same speed through low frame rates", () => {
  const slow = new EmoteMotion(), fast = new EmoteMotion();
  slow.request("wave");fast.request("wave");
  let a: number[] = [], b: number[] = [];
  for (let i=0;i<20;i++) a=slow.advance(.1);
  for (let i=0;i<80;i++) b=fast.advance(.025);
  for (let i=0;i<19;i++) expect(a[i]).toBeCloseTo(b[i], 6);
  expect(slow.advance(Number.NaN)).toEqual(a);
  expect(slow.advance(-1)).toEqual(a);
});
