# SafeAnchor

Client-seitige Minecraft-Fabric-Mod für eine **kontrollierte, einmalige Safe-Anchor-Sequenz** im PvP.

Du zielst auf einen Block, drückst den Aktivierungs-Hotkey — und die Mod führt **einmal** sauber
gesteuert aus: Anchor platzieren → Glowstone einsetzen → Schutzblock setzen/prüfen →
auslösen → zurück aufs Sicherheits-Item. Es wird **nicht** dauerhaft automatisch geklickt
und keine Aktion gespammt.

> ⚠️ **Fair Play:** Nutze die Mod nur dort, wo Automatisierung erlaubt ist (Singleplayer,
> eigene Server, Server mit ausdrücklicher Erlaubnis). Auf vielen öffentlichen Servern kann
> PvP-Automatisierung als Cheat gewertet werden und zum Bann führen.

## Versionen

| Komponente  | Version              |
|-------------|----------------------|
| Minecraft   | 1.21.11 (Java)       |
| Loader      | Fabric Loader 0.19.5 |
| Mappings    | Yarn 1.21.11+build.6 |
| Fabric API  | 0.141.6+1.21.11      |
| Loom        | 1.17.21              |
| Java        | 21                   |
| Gradle      | 9.5.1 (Wrapper)      |

## Installation

1. [Fabric Loader](https://fabricmc.net/use/) für Minecraft **1.21.11** installieren.
2. [Fabric API](https://modrinth.com/mod/fabric-api) (passend zu 1.21.11) in den `mods`-Ordner legen.
3. `safeanchor-1.0.0.jar` aus den [Releases](https://github.com/fakten60-svg/Safe-anchor/releases)
   (oder selbst gebaut, siehe unten) ebenfalls in den `mods`-Ordner legen.
4. Spiel starten — fertig. Die Mod ist **client-seitig**, der Server braucht nichts.

## Steuerung

| Aktion                    | Standard-Key | Bemerkung                                    |
|---------------------------|--------------|----------------------------------------------|
| Sequenz starten           | `V`          | Startet die Sequenz **einmalig**             |
| Sequenz abbrechen (Notfall) | `X`        | Bricht sofort ab, keine weiteren Aktionen    |

Beide Tasten erscheinen unter **Optionen → Steuerung → SafeAnchor** und sind frei
belegbar. Nochmaliges Drücken von `V` während einer laufenden Sequenz startet
**keine** zweite Sequenz (Doppelstart-Schutz).

## Funktionsweise

Ablauf nach Tastendruck (Ziel = Block im Fadenkreuz):

```
IDLE → PLACE_ANCHOR → INSERT_GLOWSTONE → PLACE_PROTECTION_BLOCK
     → SWITCH_TO_DETONATION → DETONATE → SWITCH_TO_SAFETY → FINISHED → IDLE
```

1. **PLACE_ANCHOR** — Anker-Slot wählen, Anker am anvisierten Block platzieren.
2. **INSERT_GLOWSTONE** — Glowstone-Slot wählen, Anker genau einmal aufladen.
3. **PLACE_PROTECTION_BLOCK** — Schutzblock (Standard: Obsidian) zwischen Spieler und
   Anker setzen — bzw. überspringen, wenn dort schon geeigneter Schutz steht. Geklickt
   wird dabei ein Nachbarblock, **niemals** der geladene Anker (kein Frühzünder).
4. **SWITCH_TO_DETONATION** — Auf leere Hand wechseln (Fallback: Anker-Slot), damit der
   Anker beim Auslösen garantiert nicht weiter aufgeladen wird.
5. **DETONATE** — Nur wenn alles gilt: Anker vorhanden, geladen, Dimension explodiert
   (Overworld/Ende), Schutz vorhanden (falls gefordert), Sicherheits-Item verfügbar.
6. **SWITCH_TO_SAFETY** — Zurück aufs Totem (Hotbar oder Offhand).

Jeder Übergang wartet einen **neu gewürfelten Zufalls-Delay** (Standard 60–70 ms,
monotone `nanoTime`-Messung, tick-basiert — ohne Busy-Waiting, ohne `sleep`).

Die Sequenz bricht automatisch ab bei: Tod, Welt verlassen, fehlendem Item,
verlorenem/ungültigem Ziel, Timeout, erneutem Hotkey-Druck, Schaden (optional)
oder Notfall-Taste.

## Konfiguration

Datei: `.minecraft/config/safeanchor.json` (wird beim ersten Start erzeugt).
Ungültige Werte werden automatisch durch sichere Defaults ersetzt.

```json
{
  "enabled": true,
  "minDelayMs": 60,
  "maxDelayMs": 70,
  "protectionBlockId": "minecraft:obsidian",
  "requireProtection": true,
  "safetyItemId": "minecraft:totem_of_undying",
  "showHud": true,
  "cancelOnDamage": false,
  "maximumSequenceDurationMs": 1000,
  "debugLogging": false,
  "anchorSlot": -1,
  "glowstoneSlot": -1,
  "protectionBlockSlot": -1,
  "safetyItemSlot": -1
}
```

| Einstellung | Bedeutung |
|---|---|
| `enabled` | Mod an/aus |
| `minDelayMs` / `maxDelayMs` | Zufalls-Delay pro Übergang (0–5000, max ≥ min) |
| `protectionBlockId` | Schutzblock als Block-ID |
| `requireProtection` | `true` = ohne Schutz keine Detonation |
| `safetyItemId` | Sicherheits-Item als Item-ID |
| `showHud` | Status-Overlay anzeigen |
| `cancelOnDamage` | Bei Schaden abbrechen |
| `maximumSequenceDurationMs` | Timeout (250–10000) |
| `debugLogging` | Ausführliche Logs (`[SafeAnchor] State: …`) |
| `*Slot` | Hotbar-Slot 0–8 oder `-1` = AUTO (automatisch suchen) |

Als Schutz werden zusätzlich zum konfigurierten Block immer Obsidian, Crying Obsidian,
Antiker Schrott, Netheritblock und Verstärkter Tiefenschiefer akzeptiert.

## HUD

Oben links, während der Sequenz:

```
SafeAnchor
Status: INSERT_GLOWSTONE
Delay: 64 ms
Left: 12 ms
Next: PLACE_PROTECTION_BLOCK
```

Im Leerlauf: `SafeAnchor: Ready` (kurz danach ggf. das letzte Ergebnis).

## Projektstruktur

```
safeanchor/
├── build.gradle / settings.gradle / gradle.properties
├── src/main/java/com/safeanchor/
│   ├── SafeAnchorClient.java          # Entrypoint, Tick-Handler
│   ├── SafeAnchorController.java      # State Machine
│   ├── SafeAnchorState.java           # States (Enum)
│   ├── SafeAnchorDelayManager.java    # Zufalls-Delays (nanoTime)
│   ├── SafeAnchorInventory.java       # Hotbar-/Inventar-Helfer
│   ├── SafeAnchorProtectionHelper.java# Schutzblock-Logik
│   ├── SafeAnchorActionExecutor.java  # echte Minecraft-Aktionen
│   ├── SafeAnchorConfig.java          # Einstellungen + Validierung
│   ├── SafeAnchorConfigManager.java   # JSON laden/speichern
│   ├── SafeAnchorKeybinds.java        # Hotkeys (V / X)
│   ├── SafeAnchorHud.java             # Overlay
│   └── SafeAnchorLogger.java          # Logging
└── src/main/resources/
    ├── fabric.mod.json
    └── assets/safeanchor/lang/        # en_us, de_de
```

## Bekannte Einschränkungen

- **Nur 1.21.11 + Fabric**, nur Java Edition, nur Client.
- Minecraft läuft mit ~20 TPS: 60–70 ms Delays werden auf Tick-Genauigkeit
  (~50 ms) eingehalten, nicht millisekundengenau.
- Auf Servern mit hoher Latenz wartet die Mod jeweils einen weiteren Delay auf die
  Server-Bestätigung (Platzieren/Aufladen); das Gesamt-Timeout bricht im Zweifel ab.
- Der Anker muss in einer Dimension gezündet werden, in der er explodiert
  (Overworld/Ende) — im Nether verweigert die Mod den Start.
- Das Totem wirkt aus der Hotbar-Hand oder der Offhand; die Mod legt es nicht
  automatisch in die Offhand.
- Kein automatisches Nachfüllen aus dem Inventar in die Hotbar (Items müssen in der
  Hotbar bzw. das Totem in Hotbar/Offhand liegen).

## Build-Anleitung

Voraussetzung: **Java 21**.

```bash
git clone https://github.com/fakten60-svg/Safe-anchor.git
cd Safe-anchor
./gradlew build
```

Die fertige Mod liegt danach unter `build/libs/safeanchor-1.0.0.jar`.

Zum Testen im Dev-Client: `./gradlew runClient`.

### CI & Releases

- **Build-Workflow** (`.github/workflows/build.yml`): Baut die Mod bei jedem Push und
  Pull Request; die JARs liegen anschließend als Workflow-Artefakt `safeanchor-jars`
  unter *Actions*.
- **Release-Workflow** (`.github/workflows/release.yml`): Wird durch einen Versions-Tag
  ausgelöst, baut die Mod und veröffentlicht sie automatisch als
  [GitHub-Release](https://github.com/fakten60-svg/Safe-anchor/releases) — inklusive
  Mod-JAR, Sources-JAR und `SHA256SUMS.txt`.

Neues Release erstellen:

```bash
# 1. version=X.Y.Z in gradle.properties setzen und committen
# 2. Tag anlegen und pushen (muss zur Version passen, sonst bricht der Workflow ab)
git tag vX.Y.Z
git push origin vX.Y.Z
```

## Lizenz

MIT — siehe [LICENSE](LICENSE).
