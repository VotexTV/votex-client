import { messageText } from "./messages";

export interface DeviceLogin {
  sessionId: string;
  userCode: string;
  verificationUri: string;
  expiresIn: number;
}

export function authMessage(error: unknown, de: boolean): string {
  return messageText(error, de ? "de" : "en");
}

export function codeTime(seconds: number): string {
  const value = Math.max(0, Math.floor(seconds));
  return `${Math.floor(value / 60)}:${String(value % 60).padStart(2, "0")}`;
}
