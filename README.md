# JaktApp

JaktApp är ett hobbyprojekt för ett mindre jaktlag. Målet är ett alternativ med funktionalitet liknande WeHunt, med löpande kostnader så nära noll som praktiskt möjligt.

Android utvecklas och testas först. iPhone/iOS ska stödjas senare. Kod ska delas mellan plattformarna där det är lämpligt, med plattformsspecifika lösningar för exempelvis bakgrundspositionering och behörigheter.

## Status idag

Appen har en gemensam webbversion och en första Android-grund med Capacitor, Vite, TypeScript och npm-paketerad Leaflet 1.9.4. Den visar karta, egen position, noggrannhetscirkel, hastighet, uppdateringstid och ett spår med punkträknare. I webbläsaren startar GPS automatiskt. Android använder nu en separat PoC med Starta test/Stoppa. Kartan centreras vid första positionen och med centreringsknappen.

Leaflets kod och CSS ingår i bygget och laddas inte från CDN. Kartbilder hämtas fortfarande från tile.openstreetmap.de och kräver nätåtkomst.

Positionering använder webbläsarens watchPosition på webben och en lokal Capacitor-plugin med Android foreground service i GPS-PoC:n. Webbspåret finns endast i sidans minne; Android-testspåret ligger i en native-minnesbuffert som kan återläsas efter att WebView pausats eller laddats om, så länge Android-processen lever. Beständig lagring, synkronisering, användarkonton och delning är inte implementerade. Ingen filtrering av GPS-punkter har lagts till.

Capacitor 8.5.2 är konfigurerat och Android-projektet finns i android/. Byggmappen dist används som webbkatalog.

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

Webbläsarens GPS kräver platsbehörighet och en säker anslutning: localhost fungerar på datorn, men vanlig HTTP till datorns LAN-adress från telefonen räcker normalt inte. GPS i telefonens webbläsare behöver HTTPS. Den installerade Android-appen använder i stället det native positioneringstillägget.

## Projektstruktur

| Fil | Ansvar |
| --- | --- |
| index.html | Sidans HTML: kartytan, informationsrutan och knappen. |
| src/main.ts | Startar och kopplar ihop appens delar. |
| src/config.ts | Namn, kartadress, startvy, zoom och GPS-inställningar. |
| src/styles.css | Appens utseende, flyttat från prototypen. |
| src/types.ts | Gemensamma typer för koordinater och positionsdata. |
| src/map.ts | Leaflet-karta, markör, noggrannhetscirkel, spårlinje och centrering. |
| src/location.ts | Positionsbevakning och GPS-fel för webbläsare och Capacitor. |
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


## Android med Capacitor

Den första Android-grunden använder Capacitor 8.5.2 och @capacitor/geolocation 8.2.2. Appnamnet är JaktApp, app-ID är se.jaktlaget.app och webDir är dist. Ingen server.url används: appens webbfiler paketeras lokalt och kräver inte att Vite eller datorn är igång. Kartbilder kräver fortfarande internet.

Den tidigare implementationen i location.ts finns kvar. main.ts väljer nu Android-PoC:n i stället och startar aldrig den gamla Geolocation-bevakningen parallellt. Webbläsarflödet är bevarat. PoC:n kräver exakt plats och tillåtna aviseringar; start sker uttryckligen medan appen är synlig.

De befintliga GPS-inställningarna återanvänds. På Android behålls preliminärt interval: 2000 och minimumUpdateInterval: 1000 (millisekunder) efter utomhustest på OnePlus 9 Pro. Tidigare användes indirekt 15 sekunders önskat intervall och 5 sekunders minimiintervall. Övriga GPS-inställningar är oförändrade. Webbläsaren styr fortfarande själv sin uppdateringstakt. Projektägaren rapporterade omkring ±8 m noggrannhet under gång och ±5 m stillastående, en tydlig förbättring jämfört med föregående test. Detta är telefonens rapporterade noggrannhet, inte uppmätt verkligt positionsfel. Slutliga GPS-intervall ska bestämmas genom senare fälttester av noggrannhet, spårkvalitet och batteriförbrukning. Ingen batterioptimering för en hel jaktdag har införts.

### Verktyg och bygge

Verifierad verktygsmiljö för detta steg: Node.js 24.21.0, npm 11.19.0, Temurin JDK 21.0.12.1, Android SDK Platform 36 och Gradle 8.14.3. Projektet använder minSdk 24 och compileSdk/targetSdk 36. Gradle hämtade även Build-Tools 35.0.0 enligt Android-byggverktygets standardval; installerade Build-Tools 36.0.0 kan vara kvar.

Android Studios Java 25 kan köra IDE:n men ska inte användas för projektets Gradle 8.14.3. Välj JDK 21 under File → Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK. Använd JDK 21 även via JAVA_HOME i terminalen.

Från repositoryts rot, med Node/npm i sökvägen:

```powershell
npm.cmd ci
npm.cmd run typecheck
npm.cmd run build
npx.cmd cap sync android
npx.cmd cap open android
```

Kör inte cap sync samtidigt som Android-bygget; synkroniseringen återskapar genererade filer. Efter ändringar av webbkoden behövs både build och sync innan en ny Android-installation.

För bygge i PowerShell, kontrollera att JAVA_HOME pekar på din JDK 21-installation:

```powershell
$env:JAVA_HOME
& "$env:JAVA_HOME\bin\java.exe" -version
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
.\android\gradlew.bat -p android --no-daemon assembleDebug
```

Debug-APK skapas i android/app/build/outputs/apk/debug/app-debug.apk. SDK-sökvägen kan även anges lokalt via Android Studio i android/local.properties; den filen ska inte versionshanteras.

### Installera på Android-telefon via USB

1. Öppna repositoryts android-mapp i Android Studio, inte den gamla fristående projektmappen.
2. Välj JDK 21 som Gradle JDK och låt Gradle-synkroniseringen bli klar. Behåll projektets Gradle/Android-plugin-versioner om IDE:n erbjuder uppgradering.
3. Aktivera utvecklaralternativ och USB-felsökning på telefonen.
4. Anslut en USB-kabel med datastöd och godkänn datorns felsökningsnyckel på telefonen.
5. Välj telefonen som körmål och app som körkonfiguration. Tryck Run.
6. Tillåt platsåtkomst medan appen används. Välj exakt position för GPS-testet.
7. Kontrollera kartan, markören, noggrannheten, spåret och centreringsknappen. Prova även nekad behörighet och avstängda platstjänster.
8. Koppla ur USB och kontrollera att appen startar från sin ikon utan Vite. Telefonen behöver fortfarande nät för nya kartbilder.

Om telefonen inte visas, kontrollera anslutningen med:

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" devices
```

Vid unauthorized behöver datorn godkännas på telefonen. Om listan är tom, kontrollera kabel och USB-läge samt eventuell tillverkarspecifik Windows-drivrutin. Debug-versionen kräver inget Google Play-konto.

### Behörigheter och Git

Appens manifest begär INTERNET, ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION, FOREGROUND_SERVICE, FOREGROUND_SERVICE_LOCATION och POST_NOTIFICATIONS. TrackingService är inte exporterad och har tjänsttypen location. ACCESS_BACKGROUND_LOCATION, boot receiver och automatisk återstart ingår inte. Biblioteken lägger även till ACCESS_NETWORK_STATE och en intern signaturskyddad DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION. Kontrollera det byggda manifestet vid beroendeuppdateringar.

Versionshantera capacitor.config.ts, paketfilerna, appkoden och Android-projektets källkod, manifest, resurser och Gradle-wrapper (inklusive wrapper-JAR). De genererade Capacitor-Gradle-filer som mallen inte ignorerar följer också med i Git.

Versionshantera inte node_modules, dist, Android-byggresultat/APK, Gradle-cache, local.properties, lokala IDE-inställningar, kopierade webbassets eller privata signeringsnycklar. Mallens ikon och startbild används tills vidare.

### Kontroller och kvarvarande begränsningar

TypeScript-kontroll, Vite-bygge, cap sync android och Gradle assembleDebug har passerat. En debug-APK har byggts med JDK 21. Gradle rapporterade icke blockerande varningar om flatDir och SDK XML-versioner. Webbläsarflödet har provats i Edge med simulerad GPS. Den gemensamma GPS-funktionen har även kontrollerats med simulerade native-anrop: exakt/ungefärlig/nekad behörighet, fel och avslut medan start fortfarande väntar.

Projektägaren har installerat och testat Android-appen på OnePlus 9 Pro och bekräftat fungerande karta och vanlig GPS utomhus. Även foreground-service-PoC:n har nu godkänts av projektägaren efter ett cirka 11 minuter och 32 sekunder långt fälttest med låst telefon; resultatet dokumenteras nedan. Längre fälttest av spårkvalitet och batteritid samt systematisk kontroll av layout och behörighetsfall på olika telefoner återstår. Bakgrunds-GPS finns nu som en avgränsad PoC; SQLite, Supabase, kontohantering, positionsdelning och iOS-projekt ingår inte.

npm audit rapporterar tre måttliga poster i utvecklingskedjan @capacitor/cli → xcode → uuid. De godkända paketversionerna har behållits utan audit fix --force. npm audit --omit=dev rapporterade noll sårbarheter vid kontrollen.

## Dokumentation och arbetssätt

- [PROJEKTPLAN.md](PROJEKTPLAN.md): mål, rekommenderad arkitektur, öppna beslut och utvecklingsetapper.
- [AGENTS.md](AGENTS.md): arbetsinstruktioner för Codex och andra kodassistenter.

Detta repository, vonknosen/jakt_app, är projektets fortsatta utvecklingsgrund. Den tidigare fristående projektmappen är ingen parallell utvecklingskälla. Originalprototypen finns i Git-historiken; inga extra backupkopior behövs.

Arbeta stegvis och använd Git för att kunna återställa fungerande versioner. Projektägaren är inte professionell programmerare. Förklara större tekniska beslut begripligt innan stora arkitekturförändringar genomförs.

## PoC: Android-GPS med låst skärm

Syftet är att verifiera GPS → lås skärmen → gå sicksack → öppna appen → läs tillbaka verkliga mellanliggande mätningar. Detta är inte färdig heldagsspårning.

- Lokal Java-plugin `TestTrackingPlugin` kopplar TypeScript till `TrackingService` och `TrackingStore`. Inget nytt npm-paket krävs. Appmodulen deklarerar Google Play Services Location 21.3.0 direkt (samma version som redan används av Geolocation).
- Tjänsten startas från synlig app, använder FusedLocationProviderClient med hög noggrannhet, preliminärt 2000/1000 ms, ingen avståndsgallring, ingen avsiktlig batchfördröjning och ingen gammal initial position. Intervallen garanterar inte exakt mättakt.
- Native-koden håller högst 30 000 punkter i en synkroniserad minnesbuffert. Vid gränsen stoppas testet med ett meddelande; äldre punkter skrivs inte över. Alla punkter i inkommande batchar behandlas tills gränsen nås.
- Varje punkt innehåller sessions-ID, löpnummer, koordinater, accuracy, speed (eller null), mättid, native mottagningstid samt monotona tider för analys utan påverkan av ändrad systemklocka. screenInteractive och deviceLocked avser mottagningstillfället, inte nödvändigtvis mättillfället vid fördröjd leverans.
- API: startTracking, stopTracking, getTrackingState och readSamples. Återläsning sker i sidor om högst 500 punkter och tömmer inte bufferten. TypeScript använder löpnummer mot dubbletter och mättid för kartans ordning. Luckor fylls inte med syntetiska positioner.
- UI uppdateras varannan sekund medan sidan är synlig samt vid återkomst. Timern styr enbart återläsningen, aldrig GPS-insamlingen. Teststatistiken visar punkter mottagna med släckt skärm respektive låst telefon, största lucka mellan mättider, väntan på första mätning och luckan vid slutet/nu.
- Aviseringen erbjuder Stoppa. Testdata behålls efter stopp tills nästa test startas eller processen dör. Hela processens död, tvångsstopp eller omstart raderar bufferten. START_NOT_STICKY används; ingen automatisk återstart, disk-/SQLite-lagring, nätöverföring eller extra wake lock finns.
- Kartbilder kräver fortfarande nät. Doze, tillverkarens batterioptimering, långvarigt stillastående och batteritid måste testas separat. Skärmsläckning är inte samma test som processdöd.

### Testförfarande på telefon

1. Bygg/synkronisera enligt ovan och installera via Android Studios Run på OnePlus. Tillåt exakt plats medan appen används och aviseringar.
2. Utomhus: tryck Starta test, vänta på flera mätningar och god noggrannhet. Kontrollera aviseringen. Notera tid, batteriprocent och batterioptimeringsläge.
3. Koppla ur USB, lås skärmen och gå 10–15 minuter med flera tydliga svängar, cirka 30–50 m mellan riktningsbyten. Tänd inte skärmen under promenaden.
4. Öppna appen, vänta på återläsning och tryck Stoppa. Kontrollera att alla återlästa punkter visas, att många mottogs med släckt skärm/låst telefon och att mättiderna täcker promenaden.
5. Kontrollera spårets verkliga svängar, största tidslucka och slutluckan. En rak linje mellan före/efter är inte ett godkänt resultat. Spara gärna en skärmbild innan nästa test, som ersätter spåret.
6. Kontrollera att aviseringen försvinner och punkträknaren slutar öka efter stopp. Prova aviseringsknappen Stoppa i ett separat kort test.

### Genomfört fälttest – godkänd PoC

Projektägaren har genomfört och godkänt fälttestet på OnePlus 9 Pro. Resultaten nedan är rapporterade från telefonprovet:

| Observation | Resultat |
| --- | --- |
| Testtid | 19:59:47–20:11:19, cirka 11 min 32 s |
| GPS-punkter totalt | 514 |
| Mottagna med släckt skärm | 427 |
| Mottagna med låst telefon | 432 |
| Största lucka mellan mätningar | 5,1 s |
| Rapporterad noggrannhet vid slutet | ±5 m |
| Spår på kartan | Återgav promenaden och riktningsförändringarna även under perioden med låst telefon |

Räknarna för släckt skärm och låst telefon beskriver olika tillstånd vid mottagning och ska inte summeras. Testet verifierar att mellanliggande positioner registrerades under denna promenad med låst telefon. Det verifierar inte heldagsdrift, processöverlevnad eller alla Doze-/batterioptimeringsfall.

Batteriet gick från 21 % till 18 % (3 procentenheter). Telefonen är över fem år gammal och har ett kraftigt degraderat batteri; projektägaren behöver normalt powerbank vid jakt även utan hänsyn till JaktApp. Resultatet dokumenteras endast som en observation från detta test. Det ska inte användas för att uppskatta appens normala batteriförbrukning eller räknas om till förbrukning per timme/jaktdag, och motiverar ingen ändring av GPS-intervallet nu.

Önskat intervall 2000 ms och minimiintervall 1000 ms behålls preliminärt. Slutliga intervall ska bestämmas senare genom fälttest av noggrannhet, spårkvalitet och batteriförbrukning. PoC:n är godkänd, men beständig lagring och övriga delar av etapp 2 återstår.

### Datorverifiering av PoC

Kör `npm.cmd run typecheck`, `npm.cmd run build`, `npx.cmd cap sync android` och därefter `android\gradlew.bat -p android --no-daemon assembleDebug :app:testDebugUnitTest` med JDK 21. Buffertens enhetstester omfattar trådsäkerhet, paginering, sessionsbyte, stopp och kapacitetsgräns. Kör `node --test tests/tracking-model.test.mjs` med Node 24 för återläsning, dubbletter, mättidsordning och luckstatistik. Simulerade webbläsar-/bryggtester ersätter inte sicksacktestet på telefon.