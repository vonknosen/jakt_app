# JaktApp

JaktApp är ett hobbyprojekt för ett mindre jaktlag. Målet är ett alternativ med funktionalitet liknande WeHunt, med löpande kostnader så nära noll som praktiskt möjligt.

Android utvecklas och testas först. iPhone/iOS ska stödjas senare. Kod ska delas mellan plattformarna där det är lämpligt, med plattformsspecifika lösningar för exempelvis bakgrundspositionering och behörigheter.

## Status idag

Appen är en webbprototyp med Vite, TypeScript och npm-paketerad Leaflet 1.9.4. Den visar karta, egen position, noggrannhetscirkel, hastighet, uppdateringstid och ett spår med punkträknare. GPS startar automatiskt. Kartan centreras vid första positionen och med centreringsknappen.

Leaflets kod och CSS ingår i bygget och laddas inte från CDN. Kartbilder hämtas fortfarande från tile.openstreetmap.de och kräver nätåtkomst.

Positionering använder fortfarande webbläsarens watchPosition. Spåret finns endast i sidans minne och försvinner vid omladdning. Bakgrundsspårning, beständig lagring, synkronisering, användarkonton och delning är inte implementerade. Ingen filtrering av GPS-punkter har lagts till.

Capacitor är nästa steg, men är inte installerat eller konfigurerat. Byggmappen dist är förberedd som framtida webbkatalog för Capacitor.

## Köra projektet

Använd Node.js 24 LTS med npm i din terminal. Node måste uppfylla projektets versionskrav i package.json. Kontrollera först:

```powershell
node --version
npm --version
```

Öppna en terminal i detta repository och kör:

```powershell
npm ci
npm run dev
```

Öppna den lokala adress Vite skriver ut. Öppna inte källfilen index.html direkt genom att dubbelklicka på den. Avsluta utvecklingsservern med Ctrl+C.

Vid PowerShell-fel om blockerade skript kan npm.cmd användas i stället för npm.

| Kommando | Funktion |
| --- | --- |
| npm ci | Installerar versionerna i package-lock.json. |
| npm run dev | Startar den lokala utvecklingsservern. |
| npm run typecheck | Kontrollerar TypeScript utan att skapa ett bygge. |
| npm run build | Kör typkontroll och bygger appen till dist. |
| npm run preview | Visar senaste bygget lokalt; kör build först. |

Webbläsarens GPS kräver platsbehörighet och en säker anslutning: localhost fungerar på datorn, men vanlig HTTP till datorns LAN-adress från telefonen räcker normalt inte. Ett senare telefontest behöver HTTPS eller den kommande mobilpaketeringen.

## Projektstruktur

| Fil | Ansvar |
| --- | --- |
| index.html | Sidans HTML: kartytan, informationsrutan och knappen. |
| src/main.ts | Startar och kopplar ihop appens delar. |
| src/config.ts | Namn, kartadress, startvy, zoom och GPS-inställningar. |
| src/styles.css | Appens utseende, flyttat från prototypen. |
| src/types.ts | Gemensamma typer för koordinater och positionsdata. |
| src/map.ts | Leaflet-karta, markör, noggrannhetscirkel, spårlinje och centrering. |
| src/location.ts | Webbläsarens positionsbevakning och GPS-fel. |
| src/track.ts | Spårpunkter i tillfälligt minne. |
| src/status.ts | Informationsrutans texter och värden. |
| package.json | Kommandon, versionskrav och beroenden. |
| package-lock.json | Låsta paketversioner för reproducerbar installation. |
| tsconfig.json | TypeScript-inställningar med strikt typkontroll. |
| .gitignore | Undantar node_modules, dist och lokala inställningsfiler från Git. |

Vite använder standardinställningar; någon separat Vite-konfigurationsfil behövs inte ännu. node_modules och dist genereras lokalt och ska inte redigeras som källkod.

## Verifiering av omstruktureringen

Kontrollerat med Node.js 24.19.0 och npm 12.1.0. I Codex användes den medföljande Node-miljön och npm hämtat till en temporär verktygsmapp; ingen global Node/npm-installation gjordes på datorn.

- npm run typecheck och npm run build har passerat.
- Det byggda gränssnittet har provats lokalt i Edge med simulerade positioner.
- Layoutmått och statusvisning jämfördes mot originalprototypen från Git-historiken.
- Positionsuppdateringar, spår, hastighet, centrering, GPS-fel, saknat GPS-stöd och omladdning kontrollerades.
- Kartbilder laddades och appen hämtade ingen Leaflet-kod från CDN.

Detta ersätter inte provning av verklig GPS på telefon eller framtida tester av bakgrundspositionering och batteritid.

## Dokumentation och arbetssätt

- [PROJEKTPLAN.md](PROJEKTPLAN.md): mål, rekommenderad arkitektur, öppna beslut och utvecklingsetapper.
- [AGENTS.md](AGENTS.md): arbetsinstruktioner för Codex och andra kodassistenter.

Detta repository, vonknosen/jakt_app, är projektets fortsatta utvecklingsgrund. Den tidigare fristående projektmappen är ingen parallell utvecklingskälla. Originalprototypen finns i Git-historiken; inga extra backupkopior behövs.

Arbeta stegvis och använd Git för att kunna återställa fungerande versioner. Projektägaren är inte professionell programmerare. Förklara större tekniska beslut begripligt innan stora arkitekturförändringar genomförs.
