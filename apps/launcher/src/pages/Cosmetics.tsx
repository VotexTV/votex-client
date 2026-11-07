import { Select } from "../components/Select";
import { Timeline } from "../components/Timeline";
import { frameAt, interpolateAt } from "../data/timeline";
import { insertEmoteFrame } from "../data/emoteEditor";
import { useEffect, useState } from "react";
import {
  Plus,
  Trash2,
  Pencil,
  Upload,
  Check,
  Play,
  Pause,
  RotateCcw,
  Search,
  X,
  Sparkles,
} from "lucide-react";
import {
  initialState,
  type CommunityVisibility,
} from "../../../../packages/shared-config/schema";
import {
  GlassPanel,
  GlassButton,
  PageHeading,
  Toggle,
  Modal,
} from "../components/Glass";
import { RangeField } from "../components/RangeField";
import { SkinPreview } from "../components/SkinPreview";
import { CapeCropper } from "../components/CapeCropper";
import type { Look } from "../components/Wearables";
import { emotes, type CustomEmote, type EmoteId } from "../components/emotes";
import {
  cosmeticCatalog,
  cosmeticSlots,
  type CosmeticItem,
} from "../data/cosmeticCatalog";
import { uid } from "../services/desktop";
import { CustomCosmeticEditor } from "../components/CustomCosmeticEditor";
import { CreatorViewport } from "../components/CreatorViewport";
import {
  CommunityShareLink,
  CommunityVisibilityField,
} from "../components/CommunityVisibility";
import {
  prepareCustomCosmeticSave,
  type CustomCosmetic,
} from "../data/customModels";
import {
  buildEmoteKeyframes,
  prepareCustomEmoteSave,
  readEmoteEditorDraft,
  type DraftTrack,
} from "../data/emoteEditor";
import {
  deleteCommunityItem,
  getCommunityShare,
  listCommunity,
  listMyCommunityItems,
  publishCommunityItem,
  reportCommunityItem,
  saveCapeVisibility,
  setCommunityVisibility,
  type CommunityItem,
  type OwnedCommunityItem,
} from "../services/community";
import type { PageProps } from "./types";
export function Cosmetics({
  state,
  update,
  act,
  accountSkin,
  accountModel,
}: PageProps) {
  const preset =
    state.cosmetics.find((x) => x.id === state.selectedCosmetic) ??
    state.cosmetics[0];
  const skin = state.skins.find(
    (x) => x.id === (preset.skin || state.selectedSkin),
  );
  const [name, setName] = useState(""),
    [mode, setMode] = useState<"new" | "rename" | "delete" | "">(""),
    [crop, setCrop] = useState(false);
  const [query, setQuery] = useState(""),
    [showAtelier, setShowAtelier] = useState(false),
    [creditsOpen, setCreditsOpen] = useState(false),
    [category, setCategory] = useState("all"),
    [onlyEquipped, setOnlyEquipped] = useState(false),
    [trying, setTrying] = useState<CosmeticItem | null>(null);
  const [shelf, setShelf] = useState<"catalog" | "community" | "mine">(
    "catalog",
  );
  const [customEditor, setCustomEditor] = useState(false);
  const [editingModel, setEditingModel] = useState<CustomCosmetic | null>(null);
  const [communityItems, setCommunityItems] = useState<CommunityItem[]>([]);
  const [communityOffset, setCommunityOffset] = useState<number | null>(null);
  const [ownedItems, setOwnedItems] = useState<OwnedCommunityItem[]>([]);
  const [shareInput, setShareInput] = useState("");
  const [shareNotice, setShareNotice] = useState("");
  const [capeName, setCapeName] = useState("Mein Cape");
  const capeVisibility: CommunityVisibility = preset.capeVisibility ?? "public";
  const cosmeticVisibility = (item: CustomCosmetic): CommunityVisibility =>
    item.visibility ?? (item.published ? "public" : "private");
  const emoteVisibility = (item: CustomEmote): CommunityVisibility =>
    item.visibility ?? (item.published ? "public" : "private");
  const change = (patch: Partial<Look>) =>
    update((s) => ({
      ...s,
      cosmetics: s.cosmetics.map((x) =>
        x.id === preset.id ? { ...x, ...patch, catalogVersion: 1 } : x,
      ),
    }));
  const previewLook = trying
    ? {
        ...preset,
        equipment: { ...preset.equipment, [trying.slot]: trying.id },
        ...(trying.slot === "cape" ? { cape: true, capeData: "" } : {}),
      }
    : preset;
  const equip = (item: CosmeticItem) => {
    const equipped = preset.equipment[item.slot] === item.id;
    change({
      equipment: { ...preset.equipment, [item.slot]: equipped ? "" : item.id },
      ...(item.slot === "cape" ? { cape: !equipped, capeData: "" } : {}),
    });
    setTrying(null);
  };
  useEffect(() => {
    if (shelf !== "community") return;
    let cancelled = false;
    setCommunityItems([]);
    setCommunityOffset(null);
    void act(() =>
      listCommunity(category === "cape" ? "cape" : "cosmetic", 0),
    ).then((result) => {
      if (!cancelled && result) {
        setCommunityItems(result.items);
        setCommunityOffset(result.nextOffset);
      }
    });
    return () => {
      cancelled = true;
    };
  }, [shelf, category, act]);
  const refreshMine = async () => {
    const result = await act(() => listMyCommunityItems());
    if (!result) return;
    setOwnedItems(result.items);
    update((s) => ({
      ...s,
      customCosmetics: s.customCosmetics.map((item) => {
        const remote = result.items.find((x) => x.id === item.communityId);
        return remote
          ? {
              ...item,
              visibility: remote.visibility,
              published: remote.published,
              communityShareUrl: remote.shareUrl ?? "",
            }
          : item;
      }),
      cosmetics: s.cosmetics.map((item) => {
        const remote = result.items.find((x) => x.id === item.capeCommunityId);
        return remote
          ? {
              ...item,
              capeVisibility: remote.visibility,
              capePublished: remote.published,
              capeShareUrl: remote.shareUrl ?? "",
            }
          : item;
      }),
    }));
  };
  const saveCustomModel = async (
    draft: CustomCosmetic,
    visibility: CommunityVisibility,
  ) => {
    const existing = editingModel ?? undefined;
    const forked = !!existing?.communityId;
    if ((!existing || forked) && state.customCosmetics.length >= 50)
      throw new Error(
        "Du kannst höchstens 50 lokale Ateliermodelle speichern.",
      );
    const { item } = prepareCustomCosmeticSave(draft, existing);
    const saved =
      visibility === "private" ? item : { ...item, communityOwned: true };
    update((s) => {
      if (
        existing &&
        !forked &&
        s.customCosmetics.some((x) => x.id === existing.id)
      )
        return {
          ...s,
          customCosmetics: s.customCosmetics.map((x) =>
            x.id === existing.id ? saved : x,
          ),
        };
      if (s.customCosmetics.length >= 50) return s;
      return { ...s, customCosmetics: [...s.customCosmetics, saved] };
    });
    if (visibility !== "private") {
      const remote = await act(() =>
        publishCommunityItem(
          "cosmetic",
          saved.name,
          { model: saved.model, primitives: saved.primitives },
          visibility,
        ),
      );
      if (remote)
        update((s) => ({
          ...s,
          customCosmetics: s.customCosmetics.map((x) =>
            x.id === saved.id
              ? {
                  ...x,
                  communityId: remote.id,
                  visibility: remote.visibility,
                  published: remote.published,
                  communityShareUrl: remote.shareUrl ?? "",
                }
              : x,
          ),
        }));
    }
    setCustomEditor(false);
    setEditingModel(null);
  };
  const setModelVisibility = async (
    item: CustomCosmetic,
    visibility: CommunityVisibility,
  ) => {
    const published = visibility === "public";
    const remote = item.communityId
      ? await act(() => setCommunityVisibility(item.communityId, visibility))
      : visibility !== "private"
        ? await act(() =>
            publishCommunityItem(
              "cosmetic",
              item.name,
              { model: item.model, primitives: item.primitives },
              visibility,
            ),
          )
        : undefined;
    update((s) => ({
      ...s,
      customCosmetics: s.customCosmetics.map((x) =>
        x.id === item.id
          ? remote
            ? {
                ...x,
                communityId: remote.id,
                visibility: remote.visibility,
                published: remote.published,
                communityShareUrl: remote.shareUrl ?? "",
              }
            : !item.communityId && visibility === "private"
              ? { ...x, visibility, published, communityShareUrl: "" }
              : x
          : x,
      ),
    }));
  };
  const removeModelCommunityEntry = async (item: CustomCosmetic) => {
    if (
      !item.communityId ||
      !(await act(() => deleteCommunityItem(item.communityId)))
    )
      return;
    update((s) => ({
      ...s,
      customCosmetics: s.customCosmetics.map((x) =>
        x.id === item.id
          ? {
              ...x,
              communityId: "",
              visibility: "private",
              published: false,
              communityShareUrl: "",
            }
          : x,
      ),
    }));
  };
  const loadMoreCommunity = async () => {
    if (communityOffset === null) return;
    const result = await act(() =>
      listCommunity(category === "cape" ? "cape" : "cosmetic", communityOffset),
    );
    if (result) {
      setCommunityItems((items) => [...items, ...result.items]);
      setCommunityOffset(result.nextOffset);
    }
  };
  const publishCurrentCape = async (visibility: CommunityVisibility) => {
    if (visibility === "private") {
      change({
        capeCommunityId: "",
        capeVisibility: "private",
        capePublished: false,
        capeShareUrl: "",
      });
      return;
    }
    const remote = await act(() =>
      publishCommunityItem(
        "cape",
        capeName.trim() || "Mein Cape",
        { image: preset.capeData },
        visibility,
      ),
    );
    if (remote)
      change({
        capeCommunityId: remote.id,
        capeVisibility: remote.visibility,
        capePublished: remote.published,
        capeShareUrl: remote.shareUrl ?? "",
      });
  };
  const setCapeVisibility = async (visibility: CommunityVisibility) => {
    const remote = await act(() =>
      saveCapeVisibility({
        id: preset.capeCommunityId,
        name: capeName,
        image: preset.capeData,
        visibility,
      }),
    );
    if (remote)
      change({
        capeVisibility: remote.visibility,
        capePublished: remote.published,
        capeShareUrl: remote.shareUrl ?? "",
      });
  };
  const importCommunityItem = (item: CommunityItem) => {
    if (item.kind === "cosmetic") {
      const remote = item.data as {
        model?: CustomCosmetic["model"];
        primitives?: CustomCosmetic["primitives"];
      };
      if (
        !remote.model ||
        !Array.isArray(remote.primitives) ||
        !remote.primitives.length
      )
        return;
      const id = `custom-${uid()}`;
      const primitives = remote.primitives.map((part) => ({
        ...part,
        id: `part-${uid()}`,
      }));
      const imported: CustomCosmetic = {
        id,
        name: item.name,
        slot: remote.model.slot,
        primitives,
        model: { ...remote.model, id, name: item.name },
        communityId: "",
        visibility: "private",
        communityShareUrl: "",
        published: false,
        authorName: item.authorName,
        communityOwned: false,
      };
      update((s) => ({
        ...s,
        customCosmetics: [...s.customCosmetics, imported],
      }));
      setShelf("mine");
    } else if (item.kind === "cape") {
      const image = typeof item.data.image === "string" ? item.data.image : "";
      if (!image) return;
      update((s) => ({
        ...s,
        cosmetics: s.cosmetics.map((look) =>
          look.id === s.selectedCosmetic
            ? {
                ...look,
                capeData: image,
                cape: true,
                capeCommunityId: "",
                capeVisibility: "private",
                capeShareUrl: "",
                capePublished: false,
                equipment: { ...look.equipment, cape: "" },
              }
            : look,
        ),
      }));
    }
  };
  const loadSharedItem = async () => {
    const token = shareInput.trim().match(/([0-9a-f-]{72})$/i)?.[1];
    if (!token) {
      setShareNotice("Der Freigabelink ist ungültig.");
      return;
    }
    const result = await act(() => getCommunityShare(token));
    if (!result?.item) {
      setShareNotice("Der Beitrag konnte nicht geladen werden.");
      return;
    }
    if (result.item.kind === "emote") {
      setShareNotice(
        "Gesten-Freigabelinks lassen sich unter Animationen laden.",
      );
      return;
    }
    importCommunityItem(result.item);
    setShareInput("");
    setShareNotice("Beitrag wurde in deine lokalen Modelle übernommen.");
  };
  const catalogItems: CosmeticItem[] = [
    ...cosmeticCatalog.filter(
      (x) =>
        (showAtelier || onlyEquipped || !!x.creator || x.slot === "cape") &&
        (category === "all" || x.slot === category) &&
        (!onlyEquipped || preset.equipment[x.slot] === x.id) &&
        `${x.name} ${x.description} ${x.collection}`
          .toLocaleLowerCase()
          .includes(query.toLocaleLowerCase()),
    ),
    ...state.customCosmetics
      .filter(
        (x) =>
          (category === "all" || x.slot === category) &&
          (shelf !== "catalog" || showAtelier) &&
          (!onlyEquipped || preset.equipment[x.slot] === x.id) &&
          `${x.name} ${x.slot}`.toLowerCase().includes(query.toLowerCase()),
      )
      .map((x) => ({
        id: x.id,
        name: x.name,
        slot: x.slot,
        description: "Dein eigenes Community-Modell.",
        collection: "Mein Atelier",
      })),
  ];
  return (
    <>
      <PageHeading
        eyebrow="KOSMETIK"
        title="Kosmetik-Galerie"
        description="Modelle anprobieren, ausrüsten oder im Atelier eigene Modelle erstellen."
        action={
          <div className="toolbar">
            <GlassButton
              onClick={() => {
                setEditingModel(null);
                setCustomEditor(true);
              }}
            >
              <Plus size={16} /> Modell gestalten
            </GlassButton>
            <GlassButton
              onClick={() => {
                setName("Mein neuer Look");
                setMode("new");
              }}
            >
              <Plus size={16} /> Neuer Look
            </GlassButton>
          </div>
        }
      />
      <div className="cosmetic-store">
        <div className="cosmetic-shelf">
          <div className="toolbar creator-switch">
            <button
              className={`btn ${!showAtelier ? "primary" : ""}`}
              aria-pressed={!showAtelier}
              onClick={() => {
                setShelf("catalog");
                setShowAtelier(false);
              }}
            >
              Creator Collection
            </button>
            <button
              className={`btn ${showAtelier ? "primary" : ""}`}
              aria-pressed={showAtelier}
              onClick={() => {
                setShelf("catalog");
                setShowAtelier(true);
              }}
            >
              Alle Modelle & Atelier
            </button>
            <button
              className={`btn ${shelf === "community" ? "primary" : ""}`}
              aria-pressed={shelf === "community"}
              onClick={() => setShelf("community")}
            >
              Community
            </button>
            <button
              className={`btn ${shelf === "mine" ? "primary" : ""}`}
              aria-pressed={shelf === "mine"}
              onClick={() => {
                setShelf("mine");
                void refreshMine();
              }}
            >
              Meine Beiträge
            </button>
            <button className="btn" onClick={() => setCreditsOpen(true)}>
              Modellquellen
            </button>
          </div>
          <div className="catalog-tools">
            <label className="search-input">
              <Search size={17} />
              <input
                aria-label="Kosmetik suchen"
                placeholder="Modelle, Kollektionen, Motive …"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
              />
            </label>
            <button
              className={`btn ${onlyEquipped ? "primary" : ""}`}
              onClick={() => setOnlyEquipped(!onlyEquipped)}
            >
              {onlyEquipped ? "Dein Outfit" : "Nur Ausgerüstete"}
            </button>
          </div>
          {shelf === "community" && (
            <GlassPanel className="community-share-import">
              <label className="form-stack">
                Nicht gelisteten Beitrag per Link laden
                <input
                  aria-label="Freigabelink"
                  placeholder="Votex-Freigabelink einfügen"
                  value={shareInput}
                  onChange={(event) => setShareInput(event.target.value)}
                  onKeyDown={(event) => {
                    if (event.key === "Enter") void loadSharedItem();
                  }}
                />
              </label>
              <GlassButton
                disabled={!shareInput.trim()}
                onClick={() => void loadSharedItem()}
              >
                Beitrag laden
              </GlassButton>
              {shareNotice && <small role="status">{shareNotice}</small>}
            </GlassPanel>
          )}
          <div
            className="cosmetic-categories"
            role="group"
            aria-label="Kosmetik-Kategorien"
          >
            {[["all", "Alle Modelle"], ...Object.entries(cosmeticSlots)].map(
              ([id, label]) => (
                <button
                  key={id}
                  aria-pressed={category === id}
                  className={category === id ? "active" : ""}
                  onClick={() => setCategory(id)}
                >
                  {label}
                </button>
              ),
            )}
          </div>
          <div className="store-count">
            <span>
              {shelf === "community"
                ? `${communityItems.length} Community-Beiträge`
                : shelf === "mine"
                  ? `${ownedItems.length} Online-Beiträge · ${state.customCosmetics.length} lokale Modelle`
                  : `${catalogItems.length} ${catalogItems.length === 1 ? "Modell" : "Modelle"}`}
            </span>
            <span>
              {showAtelier
                ? "CREATORS & VOTEX ATELIER"
                : "CREATOR COLLECTION · KOSTENLOS"}
            </span>
          </div>
          <div className="cosmetic-products">
            {shelf === "community" ? (
              communityItems
                .filter((item) =>
                  `${item.name} ${item.authorName}`
                    .toLowerCase()
                    .includes(query.toLowerCase()),
                )
                .map((item) => (
                  <article className="glass cosmetic-product" key={item.id}>
                    <div className="product-image community-product-image">
                      <Sparkles size={36} />
                      <span className="free-label">COMMUNITY</span>
                    </div>
                    <div className="product-info">
                      <small>
                        {cosmeticSlots[item.kind === "cape" ? "cape" : "wings"]}{" "}
                        · von {item.authorName}
                      </small>
                      <h3>{item.name}</h3>
                      <p>
                        Von einem Minecraft-Spieler geteilt. Du kannst den
                        Beitrag übernehmen oder melden.
                      </p>
                      <div className="product-actions">
                        <button
                          className="btn primary"
                          onClick={() => importCommunityItem(item)}
                        >
                          {item.kind === "cape"
                            ? "Cape übernehmen"
                            : "In Atelier kopieren"}
                        </button>
                        <button
                          className="btn"
                          onClick={() =>
                            void act(() =>
                              reportCommunityItem(item.id, "other"),
                            )
                          }
                        >
                          Melden
                        </button>
                      </div>
                    </div>
                  </article>
                ))
            ) : shelf === "mine" ? (
              <>
                {catalogItems.map((item) => {
                  const local = state.customCosmetics.find(
                    (x) => x.id === item.id,
                  );
                  return (
                    <article className="glass cosmetic-product" key={item.id}>
                      <div className="product-image community-product-image">
                        <Sparkles size={34} />
                        <span className="free-label">
                          {local
                            ? cosmeticVisibility(local) === "public"
                              ? "ÖFFENTLICH"
                              : cosmeticVisibility(local) === "unlisted"
                                ? "NICHT GELISTET"
                                : "PRIVAT"
                            : "LOKAL"}
                        </span>
                      </div>
                      <div className="product-info">
                        <small>
                          {item.collection} · {cosmeticSlots[item.slot]}
                        </small>
                        <h3>{item.name}</h3>
                        <p>
                          {local && cosmeticVisibility(local) === "public"
                            ? "Dieser Beitrag erscheint in der Community-Suche."
                            : local && cosmeticVisibility(local) === "unlisted"
                              ? "Nur Personen mit dem Freigabelink können den Beitrag laden."
                              : "Das Modell ist privat und bleibt auf diesem Gerät."}
                        </p>
                        <div className="product-actions">
                          {local && (
                            <button
                              className="btn"
                              onClick={() => {
                                setEditingModel(local);
                                setCustomEditor(true);
                              }}
                            >
                              <Pencil size={14} /> Modell bearbeiten
                            </button>
                          )}
                          {local?.communityId && (
                            <button
                              className="btn danger"
                              onClick={() =>
                                void removeModelCommunityEntry(local)
                              }
                            >
                              Online-Eintrag löschen
                            </button>
                          )}
                        </div>
                        {local?.communityOwned && (
                          <CommunityVisibilityField
                            value={cosmeticVisibility(local)}
                            onChange={(visibility) =>
                              void setModelVisibility(local, visibility)
                            }
                          />
                        )}
                        {local?.visibility === "unlisted" &&
                          local.communityShareUrl && (
                            <CommunityShareLink
                              value={local.communityShareUrl}
                            />
                          )}
                      </div>
                    </article>
                  );
                })}
                {ownedItems
                  .filter(
                    (item) =>
                      !state.customCosmetics.some(
                        (x) => x.communityId === item.id,
                      ) &&
                      !state.cosmetics.some(
                        (x) => x.capeCommunityId === item.id,
                      ),
                  )
                  .map((item) => (
                    <article className="glass cosmetic-product" key={item.id}>
                      <div className="product-image community-product-image">
                        <Sparkles size={34} />
                        <span className="free-label">
                          {item.visibility === "public"
                            ? "ÖFFENTLICH"
                            : item.visibility === "unlisted"
                              ? "NICHT GELISTET"
                              : "PRIVAT"}
                        </span>
                      </div>
                      <div className="product-info">
                        <small>{item.kind} · eigener Beitrag</small>
                        <h3>{item.name}</h3>
                        <p>
                          Dieser Online-Beitrag ist auf diesem Gerät nicht lokal
                          gespeichert.
                        </p>
                        <div className="product-actions">
                          <button
                            className="btn danger"
                            onClick={() =>
                              void act(() => deleteCommunityItem(item.id)).then(
                                (result) => {
                                  if (result)
                                    setOwnedItems((items) =>
                                      items.filter((x) => x.id !== item.id),
                                    );
                                },
                              )
                            }
                          >
                            Löschen
                          </button>
                        </div>
                        <CommunityVisibilityField
                          value={
                            item.visibility ??
                            (item.published ? "public" : "private")
                          }
                          onChange={(visibility) =>
                            void act(() =>
                              setCommunityVisibility(item.id, visibility),
                            ).then((result) => {
                              if (result)
                                setOwnedItems((items) =>
                                  items.map((x) =>
                                    x.id === item.id
                                      ? {
                                          ...x,
                                          visibility: result.visibility,
                                          published: result.published,
                                          shareUrl: result.shareUrl ?? "",
                                        }
                                      : x,
                                  ),
                                );
                            })
                          }
                        />
                        {item.visibility === "unlisted" && item.shareUrl && (
                          <CommunityShareLink value={item.shareUrl} />
                        )}
                      </div>
                    </article>
                  ))}
              </>
            ) : (
              catalogItems.map((item) => (
                <article
                  className={`glass cosmetic-product ${preset.equipment[item.slot] === item.id ? "equipped" : ""} ${trying?.id === item.id ? "trying" : ""}`}
                  key={item.id}
                >
                  <button
                    className={`product-image collection-${item.collection.toLowerCase().replaceAll(" ", "-")}`}
                    aria-label={`${item.name} anprobieren`}
                    onClick={() => setTrying(item)}
                  >
                    {state.customCosmetics.some((x) => x.id === item.id) ? (
                      <Sparkles size={34} />
                    ) : (
                      <img
                        src={`/assets/catalog/${item.id}.svg`}
                        alt={item.name}
                        loading="lazy"
                      />
                    )}
                    <span className="free-label">KOSTENLOS</span>
                    {preset.equipment[item.slot] === item.id && (
                      <span className="equipped-mark">
                        <Check size={13} /> Im Look
                      </span>
                    )}
                  </button>
                  <div className="product-info">
                    <small>
                      {item.collection} · {cosmeticSlots[item.slot]}
                    </small>
                    <h3>{item.name}</h3>
                    <p>{item.description}</p>
                    {item.creator && (
                      <small className="creator-credit">
                        {item.creator.name} · {item.creator.license}
                      </small>
                    )}
                    <div className="product-actions">
                      <button className="btn" onClick={() => setTrying(item)}>
                        Anprobieren
                      </button>
                      <button
                        className={`btn ${preset.equipment[item.slot] === item.id ? "" : "primary"}`}
                        onClick={() => equip(item)}
                      >
                        {preset.equipment[item.slot] === item.id
                          ? "Ablegen"
                          : "Ausrüsten"}
                      </button>
                      {state.customCosmetics.find((x) => x.id === item.id) && (
                        <button
                          className="btn"
                          onClick={() => {
                            setEditingModel(
                              state.customCosmetics.find(
                                (x) => x.id === item.id,
                              )!,
                            );
                            setCustomEditor(true);
                          }}
                        >
                          <Pencil size={14} /> Bearbeiten
                        </button>
                      )}
                    </div>
                    {state.customCosmetics.some(
                      (x) => x.id === item.id && x.communityOwned,
                    ) && (
                      <CommunityVisibilityField
                        value={cosmeticVisibility(
                          state.customCosmetics.find((x) => x.id === item.id)!,
                        )}
                        onChange={(visibility) =>
                          void setModelVisibility(
                            state.customCosmetics.find(
                              (x) => x.id === item.id,
                            )!,
                            visibility,
                          )
                        }
                      />
                    )}
                  </div>
                </article>
              ))
            )}
          </div>
          {shelf === "community" && communityItems.length === 0 && (
            <p className="notice">
              Noch keine öffentlichen Beiträge in dieser Kategorie. Erstelle ein
              Modell oder veröffentliche einen Look.
            </p>
          )}
          {shelf === "community" && communityOffset !== null && (
            <div className="toolbar">
              <GlassButton onClick={() => void loadMoreCommunity()}>
                Mehr aus der Community laden
              </GlassButton>
            </div>
          )}
          {shelf === "catalog" && !catalogItems.length && (
            <p className="notice">
              Hier ist noch nichts zu sehen. Wähle eine andere Kategorie oder
              suche nach einem anderen Begriff.
            </p>
          )}
        </div>
        <aside className="cosmetic-fitting">
          <GlassPanel className="fitting-card">
            <div className="fitting-header">
              <span className="eyebrow">
                {trying ? "ANPROBE" : "DEIN AKTUELLER LOOK"}
              </span>
              <Sparkles size={17} />
            </div>
            <SkinPreview
              skin={skin?.data || accountSkin}
              model={skin?.model || accountModel}
              idle={state.idleSkin}
              look={previewLook}
              customCosmetics={state.customCosmetics}
            />
            <div className="fitting-caption">
              <strong>{trying?.name || preset.name}</strong>
              <span>
                {skin?.name ||
                  (accountSkin ? "Dein Minecraft-Skin" : "Dein Votex-Skin")}
              </span>
              {trying && (
                <div className="toolbar">
                  <GlassButton
                    className="primary"
                    onClick={() => equip(trying)}
                  >
                    {preset.equipment[trying.slot] === trying.id
                      ? "Ablegen"
                      : "In diesen Look übernehmen"}
                  </GlassButton>
                  <button
                    className="icon-button"
                    aria-label="Anprobe beenden"
                    onClick={() => setTrying(null)}
                  >
                    <X size={17} />
                  </button>
                </div>
              )}
            </div>
          </GlassPanel>
          <GlassPanel className="control-panel">
            <div className="section-title">
              <h3>Gespeicherter Look</h3>
              <div className="toolbar">
                <button
                  className="icon-button"
                  title="Look umbenennen"
                  onClick={() => {
                    setName(preset.name);
                    setMode("rename");
                  }}
                >
                  <Pencil size={15} />
                </button>
                <button
                  className="icon-button danger"
                  title="Look löschen"
                  disabled={state.cosmetics.length <= 1}
                  onClick={() => setMode("delete")}
                >
                  <Trash2 size={15} />
                </button>
              </div>
            </div>
            <Select
              aria-label="Look auswählen"
              value={preset.id}
              onChange={(e) => {
                update((s) => ({ ...s, selectedCosmetic: e.target.value }));
                setTrying(null);
              }}
            >
              {state.cosmetics.map((x) => (
                <option key={x.id} value={x.id}>
                  {x.name}
                </option>
              ))}
            </Select>
            <div className="equipped-items">
              {Object.entries(preset.equipment)
                .filter(([, id]) => id)
                .map(([slot, id]) => (
                  <button
                    key={slot}
                    title="Accessoire ablegen"
                    onClick={() => {
                      change({
                        equipment: { ...preset.equipment, [slot]: "" },
                        ...(slot === "cape" ? { cape: false } : {}),
                      });
                      setTrying(null);
                    }}
                  >
                    {cosmeticCatalog.find((x) => x.id === id)?.name || id}
                    <X size={12} />
                  </button>
                ))}
            </div>
          </GlassPanel>
          <details className="glass wardrobe-details">
            <summary>Skin, eigenes Cape & Anpassung</summary>
            <div className="control-panel">
              <label className="form-stack">
                Skin für diesen Look
                <Select
                  value={preset.skin}
                  onChange={(e) => change({ skin: e.target.value })}
                >
                  <option value="">Von der Startseite übernehmen</option>
                  {state.skins.map((x) => (
                    <option key={x.id} value={x.id}>
                      {x.name}
                    </option>
                  ))}
                </Select>
              </label>
              <Toggle
                label="Cape tragen"
                value={preset.cape}
                onChange={(cape) => change({ cape })}
              />
              <GlassButton onClick={() => setCrop(true)}>
                <Upload size={15} /> Eigenes Cape zuschneiden
              </GlassButton>
              <label className="form-stack">
                Cape-Name
                <input
                  maxLength={48}
                  value={capeName}
                  onChange={(e) => setCapeName(e.target.value)}
                />
              </label>
              <CommunityVisibilityField
                label="Cape-Sichtbarkeit"
                value={capeVisibility}
                onChange={(visibility) => void setCapeVisibility(visibility)}
              />
              <p className="muted community-inline-note">
                Öffentliche Beiträge zeigen deinen Minecraft-Namen und das
                Cape-Bild. Ein Freigabelink lässt sich später widerrufen.
              </p>
              {preset.capeCommunityId && (
                <div className="toolbar">
                  <span className="muted">
                    {capeVisibility === "public"
                      ? "Öffentlich"
                      : capeVisibility === "unlisted"
                        ? "Nicht gelistet"
                        : "Privat"}
                  </span>
                  <GlassButton
                    className="danger"
                    onClick={() =>
                      void act(() =>
                        deleteCommunityItem(preset.capeCommunityId),
                      ).then((result) => {
                        if (result)
                          change({
                            capeCommunityId: "",
                            capeVisibility: "private",
                            capePublished: false,
                            capeShareUrl: "",
                          });
                      })
                    }
                  >
                    Online-Eintrag löschen
                  </GlassButton>
                </div>
              )}
              {capeVisibility === "unlisted" && preset.capeShareUrl && (
                <CommunityShareLink value={preset.capeShareUrl} />
              )}
              {!preset.capeCommunityId &&
                preset.capeData &&
                capeVisibility !== "private" && (
                  <GlassButton
                    className="primary"
                    onClick={() => void publishCurrentCape(capeVisibility)}
                  >
                    Cape online speichern
                  </GlassButton>
                )}
              <RangeField
                label="Modellgröße"
                value={preset.scale}
                min={50}
                max={150}
                onChange={(scale) => change({ scale })}
                hint="Modellfarben gehören zum jeweiligen Design. Du kannst Accessoires frei kombinieren."
              />
              <GlassButton
                onClick={() => {
                  change({
                    ...initialState.cosmetics[0],
                    id: preset.id,
                    name: preset.name,
                    equipment: {},
                    catalogVersion: 1,
                  });
                  setTrying(null);
                }}
              >
                <RotateCcw size={14} /> Look zurücksetzen
              </GlassButton>
            </div>
          </details>
          <p className="muted cosmetic-disclosure">
            Modelle von freien Creators und aus dem Votex Atelier, ohne Käufe
            oder Währung. Quellen und Lizenzen stehen am Modell. Im Spiel lokal
            sichtbar mit Votex Core für Fabric 26.2. Andere Spieler erhalten
            diese Darstellung nicht automatisch. Auren bestehen aus Geometrie,
            ohne Partikel-Emitter.
          </p>
        </aside>
      </div>
      {creditsOpen && (
        <Modal
          title="Creators & Modellquellen"
          onClose={() => setCreditsOpen(false)}
        >
          <p>
            Diese Modelle wurden kostenlos unter CC0 oder CC BY 3.0
            bereitgestellt. Für Votex wurden Maßstab, Ausrichtung und
            Spieler-Anbindung angepasst. Die bisherigen Atelier-Entwürfe bleiben
            unter „Alle Modelle & Atelier“ erreichbar.
          </p>
          <div className="creator-credits">
            {cosmeticCatalog
              .filter((x) => x.creator)
              .map((x) => (
                <article key={x.id}>
                  <h3>{x.name}</h3>
                  <p>
                    {x.creator!.name} · {x.creator!.license}
                  </p>
                  <a href={x.creator!.url} target="_blank" rel="noreferrer">
                    Originalmodell
                  </a>
                  {" · "}
                  <a
                    href={x.creator!.licenseUrl}
                    target="_blank"
                    rel="noreferrer"
                  >
                    Lizenz
                  </a>
                  <small>{x.creator!.changes}</small>
                </article>
              ))}
          </div>
        </Modal>
      )}
      {crop && (
        <CapeCropper
          act={act}
          onClose={() => setCrop(false)}
          onSave={(capeData) => {
            change({
              capeData,
              cape: true,
              capeCommunityId: "",
              capeVisibility,
              capeShareUrl: "",
              capePublished: false,
              equipment: { ...preset.equipment, cape: "" },
            });
            if (capeVisibility !== "private")
              void act(() =>
                publishCommunityItem(
                  "cape",
                  capeName.trim() || "Mein Cape",
                  { image: capeData },
                  capeVisibility,
                ),
              ).then((remote) => {
                if (remote)
                  change({
                    capeCommunityId: remote.id,
                    capeVisibility: remote.visibility,
                    capePublished: remote.published,
                    capeShareUrl: remote.shareUrl ?? "",
                  });
              });
          }}
        />
      )}
      {customEditor && (
        <CustomCosmeticEditor
          key={editingModel?.id ?? "new"}
          skin={skin?.data || accountSkin}
          model={skin?.model || accountModel}
          look={preset}
          savedModels={state.customCosmetics}
          initialModel={editingModel ?? undefined}
          onClose={() => {
            setCustomEditor(false);
            setEditingModel(null);
          }}
          onSave={async (item, visibility) => {
            await saveCustomModel(item, visibility);
          }}
        />
      )}
      {mode && (
        <Modal
          title={
            mode === "delete"
              ? "Look löschen?"
              : mode === "new"
                ? "Neuen Look speichern"
                : "Look umbenennen"
          }
          onClose={() => setMode("")}
        >
          {mode === "delete" ? (
            <>
              <p>Der lokale Look „{preset.name}“ wird entfernt.</p>
              <GlassButton
                className="danger"
                onClick={() => {
                  update((s) => ({
                    ...s,
                    cosmetics: s.cosmetics.filter((p) => p.id !== preset.id),
                    selectedCosmetic: s.cosmetics.find(
                      (p) => p.id !== preset.id,
                    )!.id,
                  }));
                  setMode("");
                }}
              >
                Look löschen
              </GlassButton>
            </>
          ) : (
            <>
              <input
                aria-label="Name des Looks"
                maxLength={60}
                value={name}
                onChange={(e) => setName(e.target.value)}
              />
              <GlassButton
                className="primary"
                disabled={!name.trim()}
                onClick={() => {
                  if (mode === "new") {
                    const p = {
                      ...structuredClone(initialState.cosmetics[0]),
                      id: uid(),
                      name: name.trim(),
                    };
                    update((s) => ({
                      ...s,
                      cosmetics: [...s.cosmetics, p],
                      selectedCosmetic: p.id,
                    }));
                  } else change({ name: name.trim() });
                  setMode("");
                }}
              >
                Speichern
              </GlassButton>
            </>
          )}
        </Modal>
      )}
    </>
  );
}
export function Animations({
  state,
  update,
  act,
  accountSkin,
  accountModel,
}: PageProps) {
  const [selected, setSelected] = useState<EmoteId>("wave");
  const [playing, setPlaying] = useState(false);
  const [loop, setLoop] = useState(true);
  const [speed, setSpeed] = useState(1);
  const [replay, setReplay] = useState(0);
  const [slot, setSlot] = useState(0);
  const [editorOpen, setEditorOpen] = useState(false);
  const [emoteName, setEmoteName] = useState("Meine Geste");
  const [duration, setDuration] = useState(2.4);
  const [publishVisibility, setPublishVisibility] =
    useState<CommunityVisibility>("public");
  const [shareInput, setShareInput] = useState("");
  const [shareNotice, setShareNotice] = useState("");
  const [editorNotice, setEditorNotice] = useState("");
  const [editingEmote, setEditingEmote] = useState<CustomEmote | null>(null);
  const [keyframeTimes, setKeyframeTimes] = useState([0, 0.5, 1]);
  const [selectedKeyframe, setSelectedKeyframe] = useState(1);
  const [timelineCursor, setTimelineCursor] = useState(0.5);
  const [verticalOffsets, setVerticalOffsets] = useState([0, 0, 0]);
  const [tracks, setTracks] = useState<DraftTrack[]>([
    { joint: "rightArm", axis: "z", values: [0, -135, 0] },
  ]);
  type PoseSnapshot={tracks:DraftTrack[];keyframeTimes:number[];verticalOffsets:number[]};
  const [poseUndo, setPoseUndo] = useState<PoseSnapshot[]>([]);
  const [poseRedo, setPoseRedo] = useState<PoseSnapshot[]>([]);
  const poseSnapshot=()=>structuredClone({tracks,keyframeTimes,verticalOffsets});
  const restorePose=(snapshot:PoseSnapshot)=>{setTracks(snapshot.tracks);setKeyframeTimes(snapshot.keyframeTimes);setVerticalOffsets(snapshot.verticalOffsets);const index=Math.min(selectedKeyframe,snapshot.keyframeTimes.length-1);setSelectedKeyframe(index);setTimelineCursor(snapshot.keyframeTimes[index]);setPlaying(false);};
  const [communityEmotes, setCommunityEmotes] = useState<CommunityItem[]>([]);
  const [communityEmoteOffset, setCommunityEmoteOffset] = useState<
    number | null
  >(null);
  useEffect(() => {
    let cancelled = false;
    void act(() => listCommunity("emote", 0)).then((result) => {
      if (!cancelled && result) {
        setCommunityEmotes(result.items);
        setCommunityEmoteOffset(result.nextOffset);
      }
    });
    return () => {
      cancelled = true;
    };
  }, [act]);
  const loadMoreEmotes = async () => {
    if (communityEmoteOffset === null) return;
    const result = await act(() =>
      listCommunity("emote", communityEmoteOffset),
    );
    if (result) {
      setCommunityEmotes((items) => [...items, ...result.items]);
      setCommunityEmoteOffset(result.nextOffset);
    }
  };
  const skin = state.skins.find((s) => s.id === state.selectedSkin);
  const look = state.cosmetics.find((c) => c.id === state.selectedCosmetic);
  const emoteVisibilityFor = (item: CustomEmote): CommunityVisibility =>
    item.visibility ?? (item.published ? "public" : "private");
  const availableEmotes = [
    ...emotes,
    ...state.customEmotes.map((item) => ({
      id: item.id,
      name: item.name,
      hint: "Deine eigene Keyframe-Geste",
      mood: "Community",
    })),
  ];
  const selectedName =
    availableEmotes.find((item) => item.id === selected)?.name ||
    "Eigene Geste";
  const selectWheelSlot = (index: number, id: string) => {
    setSlot(index);
    if (!id) return;
    setSelected(id as EmoteId);
    setPlaying(true);
    setReplay((n) => n + 1);
  };
  const buildKeyframes = () =>
    buildEmoteKeyframes({ keyframeTimes, tracks, verticalOffsets });
  const draftEmote: CustomEmote = {
    id: editingEmote?.id ?? "custom-emote-draft",
    name: emoteName.trim() || "Eigene Geste",
    duration,
    keyframes: buildKeyframes(),
    communityId: "",
    visibility: "private",
    communityShareUrl: "",
    published: false,
    authorName: "",
    communityOwned: true,
  };
  const previewCustomEmotes = editorOpen
    ? [
        ...state.customEmotes.filter((item) => item.id !== draftEmote.id),
        draftEmote,
      ]
    : state.customEmotes;
  const saveEmoteLabel = editingEmote
    ? publishVisibility !== "private"
      ? "Änderungen als Kopie teilen"
      : "Änderungen speichern"
    : publishVisibility !== "private"
      ? "Erstellen & teilen"
      : "Nur lokal speichern";
  const openEmoteEditor = (item?: CustomEmote) => {
    setEditorNotice("");
    setEditingEmote(item ?? null);
    if (item) {
      const draft = readEmoteEditorDraft(item);
      setEmoteName(draft.name);
      setDuration(draft.duration);
      setKeyframeTimes(draft.keyframeTimes);
      setTracks(draft.tracks);
      setVerticalOffsets(draft.verticalOffsets);
      setSelectedKeyframe(Math.min(1, draft.keyframeTimes.length - 1));
      setPublishVisibility("private");
      setSelected(item.id as EmoteId);
    } else {
      setEmoteName("Meine Geste");
      setDuration(2.4);
      setKeyframeTimes([0, 0.5, 1]);
      setSelectedKeyframe(1);
      setVerticalOffsets([0, 0, 0]);
      setTracks([{ joint: "rightArm", axis: "z", values: [0, -135, 0] }]);
      setPublishVisibility("public");
      setSelected("custom-emote-draft" as EmoteId);
    }
    setEditorOpen(true);
    setTimelineCursor(
      item?.keyframes[Math.min(1, item.keyframes.length - 1)].time ?? 0.5,
    );
    setPlaying(false);
    setPoseUndo([]);
    setPoseRedo([]);
    setReplay((n) => n + 1);
  };
  const editPose = (
    joint: DraftTrack["joint"],
    rotation: [number, number, number],
  ) => {
    setPlaying(false);
    setPoseUndo((history) => [...history.slice(-39), poseSnapshot()]);
    setPoseRedo([]);
    const draft = insertEmoteFrame(
      { keyframeTimes, tracks, verticalOffsets },
      timelineCursor,
    );
    if (
      draft.keyframeTimes.length === keyframeTimes.length &&
      frameAt(keyframeTimes, timelineCursor) < 0
    )
      return;
    setKeyframeTimes(draft.keyframeTimes);
    setVerticalOffsets(draft.verticalOffsets);
    setSelectedKeyframe(draft.index);
    setTracks([
      ...draft.tracks.filter((track) => track.joint !== joint),
      ...(["x", "y", "z"] as const).map((axis, index) => {
        const track = [...draft.tracks]
          .reverse()
          .find((t) => t.joint === joint && t.axis === axis);
        return {
          joint,
          axis,
          values: draft.keyframeTimes.map((_, frame) =>
            frame === draft.index
              ? (rotation[index] * 180) / Math.PI
              : (track?.values[frame] ?? 0),
          ),
        };
      }),
    ]);
  };
  const addKeyframe = (time = timelineCursor) => {
    if(keyframeTimes.length>=24 && frameAt(keyframeTimes,time)<0)return;
    const next = insertEmoteFrame(
      { keyframeTimes, tracks, verticalOffsets },
      time,
    );
    setKeyframeTimes(next.keyframeTimes);
    setTracks(next.tracks);
    setVerticalOffsets(next.verticalOffsets);
    setSelectedKeyframe(Math.min(next.index, next.keyframeTimes.length - 1));
    setTimelineCursor(time);
    setPlaying(false);
  };
  const removeKeyframe = () => {
    if (selectedKeyframe === 0 || selectedKeyframe === keyframeTimes.length - 1)
      return;
    const removed = selectedKeyframe;
    setKeyframeTimes((times) => times.filter((_, index) => index !== removed));
    setTracks((all) =>
      all.map((track) => ({
        ...track,
        values: track.values.filter((_, index) => index !== removed),
      })),
    );
    setVerticalOffsets((values) =>
      values.filter((_, index) => index !== removed),
    );
    setSelectedKeyframe(removed - 1);
    setTimelineCursor(keyframeTimes[removed - 1]);
  };
  const saveEmote = async () => {
    const { item } = prepareCustomEmoteSave(
      { name: emoteName.trim(), duration, keyframes: buildKeyframes() },
      editingEmote ?? undefined,
      `custom-emote-${uid()}`,
    );
    const isNew = !state.customEmotes.some((x) => x.id === item.id);
    if (isNew && state.customEmotes.length >= 50) {
      setEditorNotice(
        "Du hast bereits das Maximum von 50 lokalen Gesten gespeichert.",
      );
      return;
    }
    setEditorNotice("");
    update((s) => {
      const exists = s.customEmotes.some((x) => x.id === item.id);
      if (exists)
        return {
          ...s,
          customEmotes: s.customEmotes.map((x) =>
            x.id === item.id ? item : x,
          ),
        };
      if (s.customEmotes.length >= 50) return s;
      return { ...s, customEmotes: [...s.customEmotes, item] };
    });
    if (publishVisibility !== "private") {
      const remote = await act(() =>
        publishCommunityItem(
          "emote",
          item.name,
          { duration: item.duration, keyframes: item.keyframes },
          publishVisibility,
        ),
      );
      if (remote)
        update((s) => ({
          ...s,
          customEmotes: s.customEmotes.map((x) =>
            x.id === item.id
              ? {
                  ...x,
                  communityId: remote.id,
                  visibility: remote.visibility,
                  published: remote.published,
                  communityShareUrl: remote.shareUrl ?? "",
                }
              : x,
          ),
        }));
    }
    setSelected(item.id as EmoteId);
    setPlaying(true);
    setReplay((n) => n + 1);
    setEditorOpen(false);
    setEditingEmote(null);
  };
  const setEmoteVisibility = async (
    item: CustomEmote,
    visibility: CommunityVisibility,
  ) => {
    const published = visibility === "public";
    const remote = item.communityId
      ? await act(() => setCommunityVisibility(item.communityId, visibility))
      : visibility !== "private"
        ? await act(() =>
            publishCommunityItem(
              "emote",
              item.name,
              { duration: item.duration, keyframes: item.keyframes },
              visibility,
            ),
          )
        : undefined;
    update((s) => ({
      ...s,
      customEmotes: s.customEmotes.map((x) =>
        x.id === item.id
          ? remote
            ? {
                ...x,
                communityId: remote.id,
                visibility: remote.visibility,
                published: remote.published,
                communityShareUrl: remote.shareUrl ?? "",
              }
            : !item.communityId && visibility === "private"
              ? { ...x, visibility, published, communityShareUrl: "" }
              : x
          : x,
      ),
    }));
  };
  const importEmote = (item: CommunityItem) => {
    const data = item.data as Pick<CustomEmote, "duration" | "keyframes">;
    if (!Array.isArray(data.keyframes)) return;
    const imported: CustomEmote = {
      id: `custom-emote-${uid()}`,
      name: item.name,
      duration: data.duration,
      keyframes: data.keyframes,
      communityId: "",
      visibility: "private",
      communityShareUrl: "",
      published: false,
      authorName: item.authorName,
      communityOwned: false,
    };
    update((s) => ({ ...s, customEmotes: [...s.customEmotes, imported] }));
    setSelected(imported.id as EmoteId);
    setPlaying(true);
    setReplay((n) => n + 1);
  };
  const loadSharedEmote = async () => {
    const token = shareInput.trim().match(/([0-9a-f-]{72})$/i)?.[1];
    if (!token) {
      setShareNotice("Der Freigabelink ist ungültig.");
      return;
    }
    const result = await act(() => getCommunityShare(token));
    if (!result?.item) {
      setShareNotice("Der Beitrag konnte nicht geladen werden.");
      return;
    }
    if (result.item.kind !== "emote") {
      setShareNotice(
        "Das ist kein Gesten-Freigabelink. Lade ihn in der Kosmetik-Galerie.",
      );
      return;
    }
    importEmote(result.item);
    setShareInput("");
    setShareNotice("Geste wurde lokal gespeichert.");
  };
  return (
    <>
      <PageHeading
        eyebrow="EMOTES"
        title="Animationen und Emotes"
        description="Animationen erstellen, in der Vorschau testen und dem Emote-Rad zuweisen."
      />
      <div className="animation-layout">
        <GlassPanel className="skin-main">
          <div className="emote-preview-stage">
            <SkinPreview
              skin={skin?.data || accountSkin}
              model={skin?.model || accountModel}
              idle={playing}
              look={look}
              customCosmetics={state.customCosmetics}
              customEmotes={previewCustomEmotes}
              emote={selected}
              emoteLoop={loop}
              emoteSpeed={speed}
              replayKey={replay}
              onEmoteEnd={() => setPlaying(false)}
            />
            <div
              className="emote-wheel-orbit"
              role="group"
              aria-label="Emote-Rad"
            >
              {state.emoteWheel.map((id, index) => {
                const item = availableEmotes.find((emote) => emote.id === id);
                const angle =
                  -Math.PI / 2 +
                  (Math.PI * 2 * index) / state.emoteWheel.length;
                return (
                  <button
                    key={`${index}-${id}`}
                    type="button"
                    className={`emote-wheel-slot ${slot === index ? "selected" : ""}`}
                    style={{
                      left: `${50 + Math.cos(angle) * 34}%`,
                      top: `${50 + Math.sin(angle) * 40}%`,
                    }}
                    aria-label={`Platz ${index + 1}: ${item?.name || "leer"}`}
                    aria-pressed={slot === index}
                    title={`Platz ${index + 1} · ${item?.name || "leer"}`}
                    onClick={() => selectWheelSlot(index, id)}
                  >
                    <b>{index + 1}</b>
                    <span>{item?.name || "Leer"}</span>
                  </button>
                );
              })}
            </div>
          </div>
          <div className="preview-caption">
            <div className="motion-caption">
              <span className="eyebrow">MOTION STUDIO</span>
              <strong>{selectedName}</strong>
              <small>
                Weicher Einstieg · lebendige Geste · ruhiger Ausklang
              </small>
            </div>
            <GlassButton onClick={() => setPlaying(!playing)}>
              {playing ? <Pause size={15} /> : <Play size={15} />}{" "}
              {playing ? "Animation stoppen" : "Vorschau abspielen"}
            </GlassButton>
          </div>
          <div className="motion-settings">
            <label>
              Tempo
              <Select
                aria-label="Vorschau-Tempo"
                value={speed}
                onChange={(e) => setSpeed(Number(e.target.value))}
              >
                <option value={0.5}>0,5× · langsam</option>
                <option value={1}>1× · natürlich</option>
                <option value={1.5}>1,5× · schnell</option>
              </Select>
            </label>
            <Toggle label="Wiederholen" value={loop} onChange={setLoop} />
          </div>
        </GlassPanel>
        <div>
          <GlassPanel className="control-panel">
            <div className="section-title">
              <h3>Dein Emote-Rad</h3>
              <span className="keycap">B</span>
            </div>
            <p className="muted">
              Halte in Minecraft B, zeige auf einen Platz und lasse los. Rechte
              Umschalttaste öffnet das Votex-Menü. Beide Tasten sind in
              Minecraft umbelegbar.
            </p>
            <p className="muted">
              Platz {slot + 1} ist ausgewählt. Klicke unten bei einer Geste auf
              „Auf Platz {slot + 1} setzen“, um sie dauerhaft zuzuweisen. Die
              Auswahl wird auch vom Minecraft-Core verwendet.
            </p>
            <GlassButton
              onClick={() =>
                update((s) => ({
                  ...s,
                  emoteWheel: [...initialState.emoteWheel],
                }))
              }
            >
              <RotateCcw size={14} /> Standardbelegung
            </GlassButton>
          </GlassPanel>
          <div className="emote-grid">
            {availableEmotes.map((e) => (
              <div
                key={e.id}
                className={`glass emote-option luminous-card ${selected === e.id ? "selected" : ""}`}
              >
                <button
                  className="emote-select"
                  onClick={() => {
                    setSelected(e.id as EmoteId);
                    setPlaying(true);
                    setReplay((x) => x + 1);
                  }}
                >
                  <span className="emote-mood">{e.mood}</span>
                  <strong>{e.name}</strong>
                  <small>{e.hint}</small>
                </button>
                <button
                  className="text-action"
                  onClick={() => {
                    update((s) => ({
                      ...s,
                      emoteWheel: s.emoteWheel.map((id, i) =>
                        i === slot ? e.id : id,
                      ),
                    }));
                    setSelected(e.id as EmoteId);
                  }}
                >
                  Auf Platz {slot + 1} setzen
                </button>
                {state.customEmotes.find((item) => item.id === e.id)
                  ?.communityOwned && (
                  <CommunityVisibilityField
                    value={emoteVisibilityFor(
                      state.customEmotes.find((item) => item.id === e.id)!,
                    )}
                    onChange={(visibility) =>
                      void setEmoteVisibility(
                        state.customEmotes.find((item) => item.id === e.id)!,
                        visibility,
                      )
                    }
                  />
                )}
                {state.customEmotes.some((item) => item.id === e.id) && (
                  <button
                    className="text-action"
                    onClick={() => {
                      const item = state.customEmotes.find(
                        (entry) => entry.id === e.id,
                      );
                      if (item) openEmoteEditor(item);
                    }}
                  >
                    <Pencil size={13} /> Geste bearbeiten
                  </button>
                )}
                {state.customEmotes.find((item) => item.id === e.id)
                  ?.visibility === "unlisted" &&
                  state.customEmotes.find((item) => item.id === e.id)
                    ?.communityShareUrl && (
                    <CommunityShareLink
                      value={
                        state.customEmotes.find((item) => item.id === e.id)!
                          .communityShareUrl!
                      }
                    />
                  )}
              </div>
            ))}
          </div>
          <GlassPanel className="custom-emote-panel">
            <div className="section-title">
              <h3>
                {editingEmote
                  ? "Eigene Geste bearbeiten"
                  : "Eigenes Emote erstellen"}
              </h3>
              <GlassButton
                onClick={() => {
                  if (editorOpen) {
                    setEditorOpen(false);
                    setEditingEmote(null);
                    setPlaying(false);
                  } else openEmoteEditor();
                }}
              >
                {editorOpen ? "Editor schließen" : "Editor öffnen"}
              </GlassButton>
            </div>
            <p className="muted">
              Erstelle Posen für mehrere Körperteile auf einer
              Keyframe-Zeitleiste. Die Vorschau aktualisiert sich während der
              Bearbeitung.
            </p>
            {editorOpen && editingEmote?.communityId && (
              <p className="notice">
                Diese Geste ist bereits veröffentlicht. Deine Bearbeitung wird
                als private Kopie gespeichert; der Community-Beitrag bleibt
                unverändert.
              </p>
            )}
            {editorOpen && (
              <Modal
                title="Animationsstudio · Keyframes & Posen"
                onClose={() => {
                  setEditorOpen(false);
                  setPlaying(false);
                }}
              >
                <div className="emote-workspace">
                  <div className="emote-viewport-column">
                    <CreatorViewport
                      skin={skin?.data || accountSkin}
                      model={skin?.model || accountModel}
                      pose={Array.from({ length: 19 }, (_, i) =>
                        interpolateAt(
                          keyframeTimes,
                          buildKeyframes().map((f) => f.pose[i]),
                          timelineCursor,
                        ),
                      )}
                      frames={buildKeyframes()}
                      duration={duration}
                      playing={playing}
                      onPoseChange={editPose}
                    />
                    <div className="creator-playbar">
                      <GlassButton onClick={() => setPlaying(!playing)}>
                        {playing
                          ? "Wiedergabe pausieren"
                          : "Animation abspielen"}
                      </GlassButton>
                      <GlassButton
                        disabled={!poseUndo.length}
                        onClick={() => {
                          const previous = poseUndo.at(-1);
                          if (previous) {
                            setPoseRedo((h) => [...h, poseSnapshot()]);
                            restorePose(previous);
                            setPoseUndo((h) => h.slice(0, -1));
                            setPlaying(false);
                          }
                        }}
                      >
                        3D-Schritt zurück
                      </GlassButton>
                      <GlassButton
                        disabled={!poseRedo.length}
                        onClick={() => {
                          const next = poseRedo.at(-1);
                          if (next) {
                            setPoseUndo((h) => [...h, poseSnapshot()]);
                            restorePose(next);
                            setPoseRedo((h) => h.slice(0, -1));
                            setPlaying(false);
                          }
                        }}
                      >
                        Wiederholen
                      </GlassButton>
                    </div>
                  </div>
                  <div className="custom-emote-editor">
                    <label className="form-stack">
                      Name
                      <input
                        maxLength={48}
                        value={emoteName}
                        onChange={(e) => setEmoteName(e.target.value)}
                      />
                    </label>
                    <RangeField
                      label="Dauer"
                      min={1}
                      max={10}
                      step={0.1}
                      unit=" s"
                      value={duration}
                      onChange={setDuration}
                    />
                    <Timeline
                      times={keyframeTimes}
                      duration={duration}
                      cursor={timelineCursor}
                      selected={selectedKeyframe}
                      onCursor={(time) => {
                        setTimelineCursor(time);
                        setPlaying(false);
                      }}
                      onSelect={(index) => {
                        setSelectedKeyframe(index);
                        setTimelineCursor(keyframeTimes[index]);
                        setPlaying(false);
                      }}
                      onMove={(index, time) => {
                        setKeyframeTimes((times) =>
                          times.map((t, i) => (i === index ? time : t)),
                        );
                        setSelectedKeyframe(index);
                        setTimelineCursor(time);
                        setPlaying(false);
                      }}
                      onInsert={addKeyframe}
                    />
                    <GlassButton
                      disabled={
                        selectedKeyframe === 0 ||
                        selectedKeyframe === keyframeTimes.length - 1
                      }
                      onClick={removeKeyframe}
                    >
                      <Trash2 size={14} />
                      Ausgewählten Keyframe entfernen
                    </GlassButton>
                    <RangeField
                      label={`Vertikale Bewegung · Keyframe ${selectedKeyframe + 1}`}
                      min={-3}
                      max={3}
                      step={0.1}
                      unit=" px"
                      value={verticalOffsets[selectedKeyframe] ?? 0}
                      onChange={(value) => {setTimelineCursor(keyframeTimes[selectedKeyframe]);setPlaying(false);
                        setVerticalOffsets((all) =>
                          all.map((offset, index) =>
                            index === selectedKeyframe ? value : offset,
                          ),
                        );}
                      }
                    />
                    <div className="custom-emote-tracks">
                      {tracks.map((track, index) => (
                        <div className="custom-emote-track" key={index}>
                          <label className="form-stack">
                            Körperteil
                            <Select
                              value={track.joint}
                              onChange={(e) =>
                                setTracks((all) =>
                                  all.map((x, i) =>
                                    i === index
                                      ? {
                                          ...x,
                                          joint: e.target
                                            .value as typeof x.joint,
                                        }
                                      : x,
                                  ),
                                )
                              }
                            >
                              {[
                                ["head", "Kopf"],
                                ["body", "Körper"],
                                ["leftArm", "Linker Arm"],
                                ["rightArm", "Rechter Arm"],
                                ["leftLeg", "Linkes Bein"],
                                ["rightLeg", "Rechtes Bein"],
                              ].map(([id, label]) => (
                                <option key={id} value={id}>
                                  {label}
                                </option>
                              ))}
                            </Select>
                          </label>
                          <label className="form-stack">
                            Achse
                            <Select
                              value={track.axis}
                              onChange={(e) =>
                                setTracks((all) =>
                                  all.map((x, i) =>
                                    i === index
                                      ? {
                                          ...x,
                                          axis: e.target.value as typeof x.axis,
                                        }
                                      : x,
                                  ),
                                )
                              }
                            >
                              <option value="x">X</option>
                              <option value="y">Y</option>
                              <option value="z">Z</option>
                            </Select>
                          </label>
                          <RangeField
                            label={`Pose · Keyframe ${selectedKeyframe + 1}`}
                            min={-170}
                            max={170}
                            step={5}
                            unit="°"
                            value={track.values[selectedKeyframe]}
                            onChange={(degrees) => {setTimelineCursor(keyframeTimes[selectedKeyframe]);setPlaying(false);
                              setTracks((all) =>
                                all.map((x, i) =>
                                  i === index
                                    ? {
                                        ...x,
                                        values: x.values.map((value, frame) =>
                                          frame === selectedKeyframe
                                            ? degrees
                                            : value,
                                        ),
                                      }
                                    : x,
                                ),
                              );}
                            }
                          />
                          <button
                            className="icon-button danger"
                            aria-label="Bewegung entfernen"
                            disabled={tracks.length <= 1}
                            onClick={() =>
                              setTracks((all) =>
                                all.filter((_, i) => i !== index),
                              )
                            }
                          >
                            <Trash2 size={14} />
                          </button>
                        </div>
                      ))}
                    </div>
                    <div className="toolbar">
                      <GlassButton
                        disabled={tracks.length >= 18}
                        onClick={() =>
                          setTracks((all) => [
                            ...all,
                            {
                              joint: "leftArm",
                              axis: "z",
                              values: keyframeTimes.map((_, frame) =>
                                frame === selectedKeyframe ? 90 : 0,
                              ),
                            },
                          ])
                        }
                      >
                        <Plus size={14} /> Bewegung hinzufügen
                      </GlassButton>
                    </div>
                    <div className="community-consent glass">
                      <CommunityVisibilityField
                        value={publishVisibility}
                        onChange={setPublishVisibility}
                      />
                      <p>
                        Öffentliche Gesten erscheinen in der Suche. Bei „Nicht
                        gelistet“ kannst du den Link jederzeit widerrufen.
                      </p>
                    </div>
                    <GlassButton
                      className="primary"
                      disabled={!emoteName.trim()}
                      onClick={() => void saveEmote()}
                    >
                      {saveEmoteLabel}
                    </GlassButton>
                    {editorNotice && (
                      <small role="status">{editorNotice}</small>
                    )}
                  </div>
                </div>
              </Modal>
            )}
          </GlassPanel>
          <GlassPanel className="community-emote-shelf">
            <div className="section-title">
              <h3>Community-Gesten</h3>
              <span className="muted">
                {communityEmotes.length} öffentlich geladen
              </span>
            </div>
            <div className="community-emote-list">
              {communityEmotes.map((item) => (
                <article key={item.id}>
                  <div>
                    <strong>{item.name}</strong>
                    <small>von {item.authorName}</small>
                  </div>
                  <button
                    className="btn primary"
                    onClick={() => importEmote(item)}
                  >
                    Übernehmen
                  </button>
                  <button
                    className="btn"
                    onClick={() =>
                      void act(() => reportCommunityItem(item.id, "other"))
                    }
                  >
                    Melden
                  </button>
                </article>
              ))}
              {communityEmotes.length === 0 && (
                <p className="muted">
                  Noch keine veröffentlichten Gesten. Teile deine eigene
                  Animation als ersten Beitrag.
                </p>
              )}
            </div>
            <div className="community-share-import">
              <label className="form-stack">
                Nicht gelistete Geste per Link laden
                <input
                  aria-label="Gesten-Freigabelink"
                  placeholder="Votex-Freigabelink einfügen"
                  value={shareInput}
                  onChange={(event) => setShareInput(event.target.value)}
                  onKeyDown={(event) => {
                    if (event.key === "Enter") void loadSharedEmote();
                  }}
                />
              </label>
              <GlassButton
                disabled={!shareInput.trim()}
                onClick={() => void loadSharedEmote()}
              >
                Geste laden
              </GlassButton>
              {shareNotice && <small role="status">{shareNotice}</small>}
            </div>
            {communityEmoteOffset !== null && (
              <div className="toolbar">
                <GlassButton onClick={() => void loadMoreEmotes()}>
                  Mehr Gesten laden
                </GlassButton>
              </div>
            )}
          </GlassPanel>
          <p className="notice">
            Votex-Gesten verändern lokal die Darstellung deines Spielers, keine
            Serverbewegung oder Trefferbox. Sie sind für andere Spieler nicht
            automatisch sichtbar. Lokale Darstellung im Spiel benötigt Votex
            Core für Fabric 26.2.
          </p>
        </div>
      </div>
    </>
  );
}
