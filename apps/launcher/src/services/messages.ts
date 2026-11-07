type Language = "de" | "en";

// Native errors retain their original text for diagnostics. Only known messages
// are translated; service responses, filenames and crash logs are never guessed.
const german: Record<string, string> = {
  LOGIN_CANCELLED: "Die Microsoft-Anmeldung wurde abgebrochen.",
  LOGIN_EXPIRED:
    "Der Microsoft-Code ist abgelaufen. Starte die Anmeldung erneut.",
  LOGIN_DECLINED:
    "Die Anmeldung wurde bei Microsoft abgelehnt. Du kannst es erneut versuchen.",
  MICROSOFT_SETUP_REQUIRED:
    "Votex benötigt eine gültige Microsoft-App-Registrierung. Öffne die Einrichtung in den Einstellungen.",
  MICROSOFT_REAUTH_REQUIRED:
    "Bitte melde dieses Minecraft-Konto erneut mit Microsoft an.",
  MICROSOFT_UNAVAILABLE:
    "Die Microsoft-Anmeldung ist gerade nicht verfügbar. Bitte versuche es später erneut.",
  XBOX_PROFILE_REQUIRED:
    "Für dieses Microsoft-Konto fehlt ein Xbox-Profil. Richte es auf xbox.com ein und melde dich erneut an.",
  XBOX_REGION_RESTRICTED:
    "Xbox Live ist für die Region dieses Kontos nicht verfügbar.",
  XBOX_VERIFICATION_REQUIRED:
    "Xbox verlangt eine Kontobestätigung. Erledige sie im Browser und versuche es erneut.",
  XBOX_FAMILY_REQUIRED:
    "Für dieses Konto fehlt die erforderliche Xbox-Familien- oder Elterneinrichtung.",
  MINECRAFT_APP_NOT_APPROVED:
    "Minecraft hat die App-Registrierung von Votex abgelehnt. Der Herausgeber muss den API-Zugriff prüfen.",
  MINECRAFT_PROFILE_REQUIRED:
    "Dieses Konto hat noch kein Minecraft-Java-Profil. Richte es auf minecraft.net ein.",
  MINECRAFT_NOT_OWNED:
    "Für dieses Microsoft-Konto wurde keine Minecraft-Java-Berechtigung gefunden. Prüfe, ob du das richtige Konto gewählt hast.",
  CURSEFORGE_KEY_REQUIRED:
    "Hinterlege deinen eigenen CurseForge-API-Schlüssel unter Quellen. Ohne Schlüssel bleibt Modrinth verfügbar.",
  DISTRIBUTION_RESTRICTED:
    "Der Autor erlaubt für diese Datei keinen Download über Drittanbieter. Lade sie auf der offiziellen Projektseite selbst herunter.",
  INCOMPATIBLE_MOD_VERSION:
    "Eine erforderliche Mod-Abhängigkeit unterstützt diese Minecraft-Version oder diesen Modloader nicht. Prüfe die Version des Zielprofils.",
  "Minecraft or another operation is active":
    "Minecraft oder ein anderer Vorgang läuft bereits. Warte, bis er beendet ist.",
  "Profile changes require Minecraft and downloads to be stopped":
    "Warte, bis der aktuelle Download oder die Startvorbereitung abgeschlossen ist.",
  "Settings are protected after a load error":
    "Die Einstellungen sind nach einem Lesefehler geschützt. Prüfe die gespeicherte Konfiguration und starte Votex erneut.",
  "Settings are protected after a load error. Repair the original file before editing.":
    "Die Einstellungen sind nach einem Lesefehler geschützt. Prüfe die gespeicherte Konfiguration und starte Votex erneut.",
  "JSON file is too large":
    "Die JSON-Datei ist größer als 10 MB. Wähle eine kleinere Datei.",
  INVALID_JSON:
    "Die Datei enthält kein gültiges JSON. Prüfe die Datei oder wähle einen anderen Export.",
  "Image is too large": "Die Bilddatei ist zu groß. Wähle eine kleinere Datei.",
  "Could not read file":
    "Die Datei konnte nicht gelesen werden. Prüfe, ob sie noch vorhanden und zugänglich ist.",
  "Could not read image":
    "Das Bild konnte nicht gelesen werden. Wähle es erneut aus.",
  "Available in the Windows desktop app. This browser preview only stores design, skin and profile settings locally.":
    "Diese Funktion ist in der Windows-App verfügbar. Die Browser-Vorschau speichert Design-, Skin- und Profileinstellungen nur lokal.",
  "Network unavailable":
    "Die Netzwerkverbindung ist nicht verfügbar. Prüfe deine Verbindung und versuche es erneut.",
  "Failed to fetch":
    "Der Dienst ist nicht erreichbar. Prüfe deine Verbindung und versuche es erneut.",
  "Load failed":
    "Der Dienst ist nicht erreichbar. Prüfe deine Verbindung und versuche es erneut.",
  "Modrinth network unavailable":
    "Modrinth ist gerade nicht erreichbar. Prüfe deine Verbindung und versuche es erneut.",
  "Modrinth search unavailable":
    "Die Modrinth-Suche ist gerade nicht erreichbar. Versuche es erneut.",
  "Modrinth API request failed (retry later if rate limited)":
    "Modrinth konnte die Anfrage nicht abschließen. Versuche es später erneut.",
  "Modrinth API error":
    "Modrinth hat die Anfrage abgelehnt. Versuche es später erneut.",
  "Invalid Modrinth response":
    "Modrinth hat eine ungültige Antwort gesendet. Versuche es später erneut.",
  "Invalid search result":
    "Die Suchantwort ist ungültig. Versuche die Suche erneut.",
  "Invalid installed mod metadata":
    "Die Liste der installierten Mods ist ungültig. Die Dateien bleiben erhalten; prüfe den Spielordner.",
  "Select a Fabric profile to browse compatible Fabric mods":
    "Wähle ein Fabric-Profil, um passende Fabric-Mods zu suchen.",
  "Search is too long":
    "Der Suchtext ist zu lang. Verwende einen kürzeren Begriff.",
  "Dependency graph exceeds 100 mods":
    "Die Installation benötigt mehr als 100 Mod-Abhängigkeiten und wurde gestoppt.",
  "A required dependency cannot be resolved automatically":
    "Eine erforderliche Abhängigkeit konnte nicht automatisch ermittelt werden. Prüfe die Abhängigkeiten auf der Projektseite.",
  "Modrinth declares an incompatibility with an installed mod":
    "Diese Mod ist laut Modrinth mit einer bereits installierten Mod unvereinbar. Prüfe die Mods im Zielprofil.",
  "Required dependencies select conflicting versions of one project":
    "Die benötigten Mods verlangen unterschiedliche Versionen derselben Abhängigkeit. Wähle andere Mod-Versionen.",
  "Dependency plan contains an explicit incompatibility":
    "Die ausgewählten Mods sind laut ihren Metadaten miteinander unvereinbar. Die Installation wurde gestoppt.",
  "Destination mod already exists; resolve duplicate files first":
    "Die Zieldatei existiert bereits. Prüfe doppelte Mods im Profil, bevor du sie aktivierst oder deaktivierst.",
  "Mod file is missing a SHA-512 checksum":
    "Für diese Mod fehlt die SHA-512-Prüfsumme. Der Download wurde aus Sicherheitsgründen gestoppt.",
  "No downloadable mod file":
    "Für diese Mod-Version ist keine herunterladbare Datei verfügbar.",
  "Download checksum mismatch; file rejected":
    "Die heruntergeladene Datei stimmt nicht mit ihrer Prüfsumme überein und wurde verworfen. Versuche den Download erneut.",
  "Download source is not allowed":
    "Die Downloadquelle ist für Votex nicht freigegeben. Die Datei wurde nicht heruntergeladen.",
  "Download exceeds the 2 GiB file limit":
    "Die Datei überschreitet das Downloadlimit von 2 GiB. Der Download wurde gestoppt.",
  "Download failed; partial file retained for retry":
    "Der Download ist fehlgeschlagen. Bereits geladene Teile bleiben für einen erneuten Versuch erhalten.",
  "Download interrupted; retry to resume":
    "Der Download wurde unterbrochen. Starte den Vorgang erneut, um ihn fortzusetzen.",
  "Download rejected":
    "Der Downloadserver hat die Anfrage abgelehnt. Versuche es später erneut.",
  "Network request":
    "Die Netzwerkabfrage ist fehlgeschlagen. Prüfe deine Verbindung und versuche es erneut.",
  "HTTP request":
    "Der Server hat die Anfrage abgelehnt. Versuche es später erneut.",
  "Invalid server response":
    "Der Server hat eine ungültige Antwort gesendet. Versuche es später erneut.",
  "Connect to Votex Community":
    "Die Votex-Community ist gerade nicht erreichbar. Versuche es später erneut.",
  "Read Votex Community response":
    "Die Antwort der Votex-Community konnte nicht gelesen werden. Versuche es erneut.",
  "Invalid Votex Community response":
    "Die Votex-Community hat eine ungültige Antwort gesendet. Versuche es später erneut.",
  "Community response too large":
    "Die Antwort der Votex-Community überschreitet das zulässige Datenlimit.",
  "Community request failed":
    "Die Community-Anfrage ist fehlgeschlagen. Versuche es später erneut.",
  "Invalid community share link":
    "Der Freigabelink ist ungültig. Kopiere den vollständigen Votex-Link und versuche es erneut.",
  "Choose an absolute local Java path; network runtimes are not supported":
    "Wähle den vollständigen lokalen Pfad zu Java. Java aus Netzwerkordnern wird nicht unterstützt.",
  "Choose a Java runtime executable (java.exe)":
    "Wähle die Java-Programmdatei java.exe oder javaw.exe.",
  "Java runtime could not run":
    "Java konnte nicht ausgeführt werden. Prüfe den Java-Pfad unter Leistung.",
  "Cannot determine Java version":
    "Die Java-Version konnte nicht erkannt werden. Wähle eine andere Java-Installation unter Leistung.",
  "Java cache exists without a valid receipt; preserve it and repair the cache first":
    "Der Java-Zwischenspeicher hat keinen gültigen Installationsnachweis. Er bleibt erhalten und muss vor dem nächsten Start geprüft werden.",
  "Downloaded Java major version mismatch":
    "Die heruntergeladene Java-Version entspricht nicht der benötigten Version. Der Spielstart wurde gestoppt.",
  "Launcher configuration is too large":
    "Die Launcher-Einstellungen überschreiten das Datenlimit. Verkleinere eigene Hintergrunddateien oder entferne nicht benötigte lokale Entwürfe.",
  "Unsupported configuration version":
    "Diese Konfiguration benötigt eine andere Votex-Version. Die Datei bleibt erhalten.",
  "Unknown active profile":
    "Das gespeicherte aktive Profil wurde nicht gefunden. Die Konfiguration bleibt erhalten.",
  "Invalid skin or cape dimensions":
    "Das Bild hat keine unterstützten Skin- oder Cape-Abmessungen. Prüfe das PNG-Format.",
  "Invalid background image":
    "Das Hintergrundbild ist ungültig. Wähle ein unterstütztes lokales Bild.",
  "Background video exceeds the encoded size limit":
    "Das Hintergrundvideo überschreitet das Datenlimit. Wähle eine Datei bis 5 MB.",
  "Background video exceeds the local 5 MB limit":
    "Das Hintergrundvideo ist größer als 5 MB. Wähle eine kleinere Datei.",
  "Unsupported background video type":
    "Dieses Videoformat wird nicht unterstützt. Verwende MP4 oder WebM.",
  "Background video header does not match its media type":
    "Der Inhalt der Videodatei passt nicht zum angegebenen MP4-/WebM-Format.",
  "Video background is selected without a local video":
    "Wähle zuerst eine lokale Videodatei als Hintergrund aus.",
  "Background framing value out of range":
    "Ein Wert für den Hintergrund-Ausschnitt liegt außerhalb des erlaubten Bereichs.",
  "Reading official Minecraft metadata":
    "Offizielle Minecraft-Versionsdaten werden geladen …",
  "Verified game files": "Spieldateien werden heruntergeladen und geprüft …",
  "Installation verified": "Die Installation wurde geprüft und ist bereit.",
  "Verifying Minecraft account": "Das Minecraft-Konto wird geprüft …",
  "Safe Mode: isolated session, no external mods or normal worlds":
    "Abgesicherter Start: getrennte Sitzung ohne externe Mods und ohne deine normalen Welten.",
  "Downloading and verifying compatible mods":
    "Passende Mods werden heruntergeladen und geprüft …",
  "Mods installed. Changes apply at next launch.":
    "Die Mods wurden installiert. Die Änderungen gelten beim nächsten Spielstart.",
  "Client Core confirmed the main menu; stable for 45 seconds. Recovery point protected.":
    "Votex Core hat das Hauptmenü bestätigt; der Start lief 45 Sekunden stabil. Der Sicherungspunkt ist geschützt.",
  "Log evidence: a class requires a different Java version.":
    "Hinweis aus dem Protokoll: Eine Klasse benötigt eine andere Java-Version.",
  "Log evidence: Java reported an out-of-memory error.":
    "Hinweis aus dem Protokoll: Java hat einen Speichermangel gemeldet.",
  "Log evidence: Fabric reported incompatible or missing mod dependencies.":
    "Hinweis aus dem Protokoll: Fabric hat unvereinbare oder fehlende Mod-Abhängigkeiten gemeldet.",
  "Log evidence: GLFW reported a window or graphics initialization error.":
    "Hinweis aus dem Protokoll: GLFW hat einen Fehler beim Initialisieren des Fensters oder der Grafik gemeldet.",
};

const wrappers: Record<string, string> = {
  "Settings were not updated":
    "Die Änderung enthält ungültige Einstellungen und wurde nicht übernommen.",
  "Settings could not be loaded. The original file was preserved":
    "Die Einstellungen konnten nicht geladen werden. Die ursprüngliche Datei bleibt erhalten.",
  "Settings were not saved": "Die Einstellungen wurden nicht gespeichert.",
  "Mod installation failed and was rolled back":
    "Die Mod-Installation ist fehlgeschlagen und wurde zurückgesetzt.",
};

export function errorText(value: unknown): string {
  if (value instanceof Error) return value.message;
  if (typeof value === "string") return value.replace(/^Error:\s*/, "");
  if (
    value &&
    typeof value === "object" &&
    "message" in value &&
    typeof value.message === "string"
  )
    return value.message;
  return value == null ? "" : String(value);
}

function translate(text: string, depth = 0): string | undefined {
  if (depth > 4) return undefined;
  if (Object.hasOwn(german, text)) return german[text];
  const separator = text.indexOf(": ");
  if (separator >= 0) {
    const context = text.slice(0, separator);
    if (Object.hasOwn(wrappers, context)) {
      const cause = translate(text.slice(separator + 2), depth + 1);
      return `${wrappers[context]}${cause ? ` ${cause}` : ""}`;
    }
    if (Object.hasOwn(german, context)) return german[context];
  }
  const java =
    /^INVALID_JAVA_RUNTIME: This Minecraft version requires Java (\d+)\. Select that major version in Performance\.$/.exec(
      text,
    );
  if (java)
    return `Diese Minecraft-Version benötigt Java ${java[1]}. Wähle diese Java-Version unter Leistung oder lasse das Java-Feld leer, damit Votex sie automatisch verwaltet.`;
  if (
    text.startsWith(
      "INVALID_JAVA_RUNTIME: Java not found. Set a Java executable in Performance.",
    )
  )
    return "Java wurde nicht gefunden. Prüfe den Java-Pfad unter Leistung oder lasse das Feld leer, damit Votex die passende Version automatisch verwaltet.";
  if (
    /^Minecraft process (?:Some\(\d+\)|None) is running\. Startup is not confirmed yet\.$/.test(
      text,
    )
  )
    return "Der Minecraft-Prozess läuft. Ein erfolgreicher Spielstart ist noch nicht bestätigt.";
  const exited =
    /^Minecraft exited normally after (\d+) seconds( \(isolated Safe Mode\))?\.$/.exec(
      text,
    );
  if (exited)
    return `Minecraft wurde nach ${exited[1]} Sekunden regulär beendet${exited[2] ? " (getrennter abgesicherter Start)" : ""}.`;
  return undefined;
}

export function presentMessage(value: unknown, language: Language) {
  const original = errorText(value);
  const text =
    (language === "de" ? translate(original) : undefined) ?? original;
  return {
    text:
      text ||
      (language === "de"
        ? "Ein unbekannter Fehler ist aufgetreten."
        : "An unknown error occurred."),
    details: text !== original ? original : "",
  };
}

export function messageText(value: unknown, language: Language): string {
  return presentMessage(value, language).text;
}
