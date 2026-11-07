import type { AppState } from "../../../../packages/shared-config/schema";
export interface PageProps {
  state: AppState;
  manageMods?: (id: string) => void;
  update: (fn: (s: AppState) => AppState) => boolean;
  act: <T>(fn: () => Promise<T>) => Promise<T | undefined>;
  busy: boolean;
  refresh: number;
  accountSkin?: string;
  accountModel?: "default" | "slim";
  reload?: () => Promise<void>;
  flush?: () => Promise<void>;
  launchProfile?: (id: string) => void;
}
