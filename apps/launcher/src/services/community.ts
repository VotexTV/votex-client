import { call } from "./desktop";
import type { CommunityVisibility } from "../../../../packages/shared-config/schema";

export type CommunityKind = "cosmetic" | "emote" | "cape";
export interface CommunityItem {
  id: string;
  authorName: string;
  kind: CommunityKind;
  name: string;
  data: Record<string, unknown>;
  createdAt: string;
}
export interface OwnedCommunityItem {
  id: string;
  kind: CommunityKind;
  name: string;
  visibility: CommunityVisibility;
  published: boolean;
  shareUrl?: string;
  createdAt: string;
  updatedAt: string;
}
export { type CommunityVisibility };
export async function listCommunity(kind: CommunityKind,offset=0) {
  return call<{items:CommunityItem[];nextOffset:number|null}>("community_list",{kind,offset});
}
export async function getCommunityShare(token: string) {
  return call<{item:CommunityItem}>("community_share",{token});
}
export async function listMyCommunityItems() {
  return call<{items:OwnedCommunityItem[]}>("community_mine");
}
export async function publishCommunityItem(kind: CommunityKind,name: string,data: Record<string,unknown>,visibility: CommunityVisibility) {
  return call<{id:string;visibility:CommunityVisibility;published:boolean;shareUrl?:string}>("community_publish",{kind,name,data,visibility,published:visibility === "public"});
}
export async function setCommunityVisibility(id: string,visibility: CommunityVisibility) {
  return call<{id:string;visibility:CommunityVisibility;published:boolean;shareUrl?:string}>("community_visibility",{id,visibility,published:visibility === "public"});
}
/** Return a committed publication state; callers keep their saved state on rejection. */
export async function saveCapeVisibility(input: {
  id: string;
  name: string;
  image: string;
  visibility: CommunityVisibility;
}) {
  if (input.id) return setCommunityVisibility(input.id, input.visibility);
  if (input.visibility === "private" || !input.image)
    return { id: "", visibility: input.visibility, published: false, shareUrl: "" };
  return publishCommunityItem(
    "cape",
    input.name.trim() || "Mein Cape",
    { image: input.image },
    input.visibility,
  );
}
export async function deleteCommunityItem(id: string) {
  return call<{deleted:boolean}>("community_delete",{id});
}
export async function reportCommunityItem(id: string,reason:"inappropriate"|"copyright"|"other",details="") {
  return call<{received:boolean}>("community_report",{id,reason,details});
}
