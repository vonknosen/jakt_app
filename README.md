# JaktApp

JaktApp är ett hobbyprojekt för ett mindre jaktlag. Målet är ett alternativ med funktionalitet liknande WeHunt, med löpande kostnader så nära noll som praktiskt möjligt.

Android utvecklas och testas först. iPhone/iOS ska stödjas senare. Kod ska delas mellan plattformarna där det är lämpligt, med plattformsspecifika lösningar för exempelvis bakgrundspositionering och behörigheter.

## Status idag

Appen har en gemensam webbversion och en första Android-grund med Capacitor, Vite, TypeScript och npm-paketerad Leaflet 1.9.4. Den visar karta, egen position, noggrannhetscirkel, hastighet, uppdateringstid och ett spår med punkträknare. I webbläsaren startar GPS automatiskt. Android använder nu en separat PoC med Starta nytt spår/Stoppa och spara samt en lista över sparade spår. Kartan centreras vid första positionen och med centreringsknappen.

Leaflets kod och CSS ingår i bygget och laddas inte från CDN. Kartbilder hämtas fortfarande från tile.openstreetmap.de och kräver nätåtkomst.

Positionering använder webbläsarens watchPosition på webben och en lokal Capacitor-plugin med Android foreground service i GPS-PoC:n. Webbspåret finns endast i sidans minne; Android-spåren sparas löpande i privat SQLite och kan återläsas efter WebView-omladdning eller processavslut. Synkronisering, användarkonton och delning är inte implementerade. Ingen filtrering av GPS-punkter har lagts till.

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

Projektägaren har installerat och testat Android-appen på OnePlus 9 Pro och bekräftat fungerande karta och vanlig GPS utomhus. Även foreground-service-PoC:n har nu godkänts av projektägaren efter ett cirka 11 minuter och 32 sekunder långt fälttest med låst telefon; resultatet dokumenteras nedan. Längre fälttest av spårkvalitet och batteritid samt systematisk kontroll av layout och behörighetsfall på olika telefoner återstår. Bakgrunds-GPS är fälttestad. PoC:n för beständig lokal SQLite-lagring är också godkänd efter telefonprov med verifierad processdöd och återöppning av sparade spår. Supabase, kontohantering, positionsdelning och iOS-projekt ingår inte.

npm audit rapporterar tre måttliga poster i utvecklingskedjan @capacitor/cli → xcode → uuid. De godkända paketversionerna har behållits utan audit fix --force. npm audit --omit=dev rapporterade noll sårbarheter vid kontrollen.

## Dokumentation och arbetssätt

- [PROJEKTPLAN.md](PROJEKTPLAN.md): mål, rekommenderad arkitektur, öppna beslut och utvecklingsetapper.
- [AGENTS.md](AGENTS.md): arbetsinstruktioner för Codex och andra kodassistenter.

Detta repository, vonknosen/jakt_app, är projektets fortsatta utvecklingsgrund. Den tidigare fristående projektmappen är ingen parallell utvecklingskälla. Originalprototypen finns i Git-historiken; inga extra backupkopior behövs.

Arbeta stegvis och använd Git för att kunna återställa fungerande versioner. Projektägaren är inte professionell programmerare. Förklara större tekniska beslut begripligt innan stora arkitekturförändringar genomförs.

## PoC: beständiga Android-spår

GPS → TrackingService → TrackingStore (en processgemensam, serialiserad arbetstråd) → TrackingDatabase/Android SQLite. Capacitor-pluginen läser data till TypeScript/Leaflet. JavaScript/WebView styr aldrig sparningen. Webbversionen behåller sin tidigare GPS och sitt minnesspår.

- `TrackingDatabase.java`: Androids inbyggda SQLiteOpenHelper, databasversion 1, privat `tracking.db`, WAL och `synchronous=NORMAL`. Inga nya npm-/SQLite-bibliotek.
- `tracking_session`: UUID, starttid och monoton starttid, eventuell stopptid, status (`recording`, `stopped`, `interrupted`, `error`), meddelande och senaste sparade löpnummer.
- `tracking_sample`: identitet `(session_id, sequence)`, koordinater, accuracy, nullable speed, mättid, native mottagningstid, monotona mät-/mottagningstider och skärm-/låstillstånd. Foreign key med cascade-radering. Tider i UTC-millis; monotona tider jämförs endast inom samma registrering/uppstart.
- En transaktion per mottagen GPS-leverans: samtliga punkter och löpnummer sparas tillsammans utan extra tidsbuffring. Endast färdigskrivna punkter redovisas som sparade. En leverans som fortfarande väntar i minnet eller inte har committats kan förloras vid processdöd.
- GPS använder fortsatt FusedLocationProviderClient med hög noggrannhet och preliminärt 2000/1000 ms. Ingen ny GPS-filtrering, synkstatus eller nätöverföring.
- Stoppa och aviseringsknappen stänger mottagningen och väntar på tidigare accepterade skrivningar och beständig slutstatus innan stoppet bekräftas. Fel i sparningen får inte ge beskedet ”Stoppat och sparat”.
- Processägaren återställer kvarlämnad `recording` till `interrupted` vid första databasåtkomst i en ny process. WebView-omladdning och vanlig återöppning av databasen gör inte detta. Exakt sluttid efter processdöd är okänd; ingen slutlucka beräknas mot en ny uppstarts klocka. Ingen automatisk återstart av GPS.
- Lista över sparade spår med starttid, status och antal punkter. Välj ett spår för att rita det. Starta nytt spår bevarar tidigare spår. Radera valt spår kräver uttrycklig bekräftelse och tillåts inte för pågående session.
- Läsning sker i sidor om högst 500 punkter med en fast övre löpnummergräns per återläsning, så att nya GPS-punkter inte gör läsningen oändlig. Inga gränser på 30 000 punkter eller 65 sidor. Byte av spår avbryter en gammal återläsning. Hela det valda spåret finns fortfarande i WebViews minne för kartvisning; mycket stora spår behöver senare prestandaprov.
- Inga automatiska raderingar. Full lagring/skrivfel stoppar registreringen med felbesked. Om även felstatusen inte kan sparas visas fel i den levande processen; efter processdöd kan sessionen då bara identifieras som avbruten.
- Version 1 är första databasen; gamla minnesspår från tidigare appversion kan inte återskapas. Framtida versioner kräver uttryckliga migreringar. Ingen destruktiv fallback.
- Databaskatalogen, inklusive WAL/journal-filer, undantas från Android-backup och enhetsöverföring via `backup_rules.xml` (äldre Android) och `data_extraction_rules.xml` (Android 12+). Appen har inga andra databaser nu. Ingen separat databaslösenordskryptering införs.

WAL/NORMAL skyddar färdiga transaktioner vid appens processdöd. Plötsligt strömavbrott/systemkrasch kan däremot förlora nyligen färdiga transaktioner. Avinstallation och ”Rensa lagring” raderar appens data. Säkerhetskopiering/export ingår inte. GPS fortsätter inte efter processdöd. Kartbilder kräver fortfarande nät; lokal registrering gör det inte.

### Verifiering

```powershell
npm.cmd run typecheck
node --test tests/tracking-model.test.mjs
npm.cmd run build
npx.cmd cap sync android
# JDK 21 och Android SDK enligt verktygsavsnittet ovan
.\android\gradlew.bat -p android --no-daemon assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest
# Kräver ansluten telefon/emulator; uppdaterar APK:erna utan avinstallation
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb install -r android/app/build/outputs/apk/debug/app-debug.apk
& $adb install -r android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
& $adb shell am instrument -w se.jaktlaget.app.test/androidx.test.runner.AndroidJUnitRunner
```

Android-testerna använder separata slumpnamngivna testdatabaser och täcker schema/WAL/NORMAL, återöppning, rollback av en felaktig leverans, fler än 30 000 punkter/65 sidor, sessionsseparation, cascade-radering, processåterhämtning, skrivfel och stoppets skrivbarriär. De gamla JVM-testerna för minnesbufferten har ersatts. TypeScript-testerna täcker återläsning, dubbletter, mättidsordning, okänd slutlucka efter avbrott, sidvis läsning utan totalgräns och avbruten återläsning vid spårbyte. Simulerad processåterhämtning i tester ersätter inte provet med faktisk processdöd nedan.

Verifierat 2026-09-26: TypeScript, Vite-build, Capacitor-sync och Android-debugbuild passerade. Sju TypeScript-tester och elva Android-tester på OnePlus 9 Pro/Android 14 passerade (tio lagringstester och mallens befintliga test). Webbversionen och Android-gränssnittet kontrollerades även med simulerade positioner/brygganrop. Manifest, paketerade backupundantag och diffkontroll är kontrollerade. Gradles tidigare icke blockerande varningar om flatDir och SDK XML kvarstår. Det verkliga promenadprovet med processdöd godkändes därefter 2026-09-27, se resultatet nedan.

Gradles `connectedDebugAndroidTest` användes vid första verifieringen och tog bort appinstallationen efter testerna. Kör därför kommandona ovan när telefonen innehåller spår som ska bevaras. Testerna använder egna testdatabaser, men avinstallation av själva appen raderar även dess vanliga data.

### Godkänt telefonprov av beständig lagring, 2026-09-27

Projektägaren genomförde en kortare testpromenad på OnePlus 9 Pro. Skärmbilden visar mättider 06:47:26–06:49:24, 91 sparade punkter, 79 mottagna med släckt skärm, 81 med låst telefon, största mätlucka 2,9 s och slutnoggrannhet ±6 m. Räknarna för släckt och låst skärm överlappar.

Efter stopp och sparning gav första `pidof` processnummer 549. Sista `pidof` efter `adb am kill` var tom, vilket bekräftade processdöden. Efter ny start fanns det tidigare spåret kvar och kunde öppnas. Ett andra spår kunde skapas utan att det första försvann; båda gick att välja och öppna. Projektägaren har godkänt lagrings-PoC:n.

Gränssnittets rutinuppdatering bevarar nu befintliga listalternativ och statistikfält och ändrar bara innehåll som faktiskt ändrats. Laddningsmeddelandet visas inte vid varje rutinuppdatering. Rättningen mot blinkande spårlista är verifierad på telefonen. TypeScript, Vite-build, sju automatiska tester, simulerade webb-/Android-UI-tester, Capacitor-sync, Android-debugbuild och diffkontroll passerade efter rättningen. Android-bygget behövde köras med `--no-watch-fs` efter att Gradles filsystemsundersökning fastnat; ingen projektkonfiguration ändrades.

Det korta provet verifierar sparning och återläsning efter stopp och processdöd, inte heldagsdrift eller abrupt processdöd mitt under en skrivning.

### Telefonprov: spåra → spara → processdöd → återläs

1. Installera debug-APK via Android Studios Run eller `adb install -r android/app/build/outputs/apk/debug/app-debug.apk`. Avinstallera inte den tidigare versionen och använd inte Rensa lagring.
2. Tillåt exakt plats och aviseringar. Tryck Starta nytt spår utomhus. Lås skärmen och gå sicksack i 10–15 minuter.
3. Öppna appen, tryck Stoppa och spara och invänta ”Stoppat och sparat”. Kontrollera att aviseringen försvunnit. Notera sessions-ID, sparat punktantal och mättider i GPS-teststatistik; ta gärna en skärmbild.
4. Tryck Hem. Ingen debugger får vara ansluten och ingen spårning får pågå. Kör i PowerShell:

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb shell pidof se.jaktlaget.app
& $adb shell am kill se.jaktlaget.app
& $adb shell pidof se.jaktlaget.app
```

5. Första `pidof` ska ge ett processnummer, det andra ska vara tomt. Om processen finns kvar är processdöd inte verifierad; vänta kort i bakgrunden och kontrollera igen. Använd inte Force stop som ersättning. Om processen redan var borta före kommandot, öppna appen och upprepa efter Hem.
6. Öppna JaktApp från ikonen utan att starta ny GPS. Välj samma spår i listan. Kontrollera samma sessions-ID, punktantal, mättider och verkliga riktningsförändringar på kartan.
7. Starta och stoppa ett andra kort spår. Kontrollera att båda går att öppna separat. Prova därefter uttrycklig radering av det korta testspåret; det första ska finnas kvar.

Att svepa bort en aktivitet eller använda ”Behåll inte aktiviteter” bevisar inte processdöd. `am kill` dödar endast processer Android bedömer kan avslutas och är därför avsett för detta prov efter stopp och Hem. Tvångsstopp ska inte kringgås. Ett separat utvecklartest av abrupt processdöd under aktiv skrivning behövs för ytterligare kraschverifiering; senaste ej färdiga transaktionen kan förloras, tidigare sparade punkter ska finnas kvar.

### Genomfört fälttest – godkänd PoC

Projektägaren har genomfört och godkänt fälttestet av den tidigare minnesbaserade bakgrunds-GPS-PoC:n på OnePlus 9 Pro (commit 4eaf032). Detta test verifierar inte den nya SQLite-versionen. Resultaten nedan är rapporterade från telefonprovet:

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

Önskat intervall 2000 ms och minimiintervall 1000 ms behålls preliminärt. Slutliga intervall ska bestämmas senare genom fälttest av noggrannhet, spårkvalitet och batteriförbrukning. Bakgrunds-GPS-PoC:n är godkänd. Även lagrings-PoC:n är godkänd enligt det separata telefonprovet ovan.
