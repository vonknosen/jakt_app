# Projektmål, arkitektur och utvecklingsplan

Detta dokument beskriver avsikten för projektet. Rekommenderade komponenter nedan är inte implementerade enbart för att de nämns här.

## Långsiktiga mål

- Ett hobbyprojekt för ett mindre jaktlag med funktionalitet liknande WeHunt.
- Löpande kostnader så nära noll som praktiskt möjligt. Föredra lämpliga open-source-lösningar och kostnadsfria tjänstenivåer.
- Android först, därefter iOS stegvis. Återanvänd gemensam kod utan att låta Android-specifika val tvinga fram en större omskrivning senare.
- Visa egen position och jaktlagets positioner i nära realtid under jakt.
- Skapa och gå med i jaktlag samt starta och avsluta jakter.
- Skilja på roller såsom passkytt och rörlig jägare/hundförare.
- Fortsätta positionsinsamling, lagring och överföring med släckt skärm när operativsystem och nätförbindelse tillåter det.
- Tillräckligt låg batteriförbrukning för en hel jaktdag, verifierad i fälttester.
- Visa jaktområdesgränser, pass och andra relevanta punkter; senare även offlinekartor.
- Spara spår och viss historik med tydliga åtkomst- och raderingsregler.
- Hantera dålig eller utebliven mobiltäckning.
- Skydda positionsdata så att bara rätt deltagare får tillgång under avsedda förhållanden.
- Hundpositioner är en framtida funktion, beroende på tillgänglig utrustning, integrationer och kostnader.

## Produktprincip: enkelhet under jakt

Enkelhet och tydlighet under pågående jakt är en viktig produktprincip. Centrala funktioner som egen position, jaktkamraternas positioner, pass och start/stopp av positionsdelning ska vara tydliga och kräva få moment.

## Rekommenderad arkitektur

| Del | Inriktning och ansvar |
| --- | --- |
| Gemensamt gränssnitt | Webbkod i TypeScript för karta, jaktlag, jakter och inställningar. Exakt UI-ramverk är inte valt. |
| Mobilpaketering | Capacitor är huvudspåret för Android och senare iOS. |
| Bakgrundspositionering | Android foreground service med avisering; iOS Core Location med nödvändiga bakgrundsfunktioner och behörigheter. Samma gränssnitt mot gemensam appkod. |
| Lokal beständig lagring | SQLite är föreslagen grund för jakter, spårpunkter och väntande överföringar. |
| Synkronisering | Separat ansvar för sändning, återförsök och hantering av nätavbrott. Får inte kräva att webbgränssnittet är aktivt. |
| Backend | Supabase är nuvarande kandidat för inloggning, medlemskap, jakter och realtidspositioner. Valet är inte låst. |
| Karta | Leaflet kan användas först. Slutligt kartbibliotek och datakälla väljs efter tidig utvärdering av offlinebehov, licenser och kostnader. |

Grundflödet är: telefonens positionering → lokal databas → karta och synkronisering → backend → behöriga deltagares telefoner.

Kartan visar insamlade data. Den ska inte hålla insamlingen vid liv. Ett GPS-tillägg räcker inte om det endast skickar händelser till JavaScript som kan pausas. Både beständig lagring och överföring måste verifieras med vilande webbgränssnitt.

Välj i första hand ett underhållet tillägg med lämplig licens och stöd för båda plattformarna. Vilket tillägg som används, och hur mycket egen plattformskod som behövs, är öppna beslut. Om en tidig provversion visar stora hinder ska arkitekturen omprövas innan många funktioner byggs.

## Täckning och energiförbrukning

Eget spår ska kunna samlas och sparas utan nät. Andras aktuella positioner kan inte hämtas utan kommunikation. Visa därför positionens mättid, noggrannhet och om den är gammal.

När nätet återkommer prioriteras senaste positionen. Äldre spårpunkter hanteras separat och får inte ersätta en nyare liveposition. Återförsök ska inte skapa dubbletter.

Skilj mellan hur ofta positionen mäts, sparas och skickas. Anpassa efter rörelse och roll; en passkytt kan börja röra sig. Bestäm intervall genom mätning. Låsning av skärmen, nätavbrott, processavslut, tvångsstopp och omstart är olika fall som behöver dokumenterat beteende.

## Integritet och backend

Skilj mellan användare, jaktlag, medlemskap, jakt och deltagande i en jakt. Roller under jakten är inte samma sak som administrativa behörigheter.

Medlemskap ska inte innebära ständig platsdelning. Deltagaren startar och stoppar sin delning uttryckligen. Avslutad jakt ska stoppa fortsatt delning till den jakten; servern måste också avvisa otillåtna eller försenade uppdateringar.

Kontrollera åtkomst på serversidan för både lagrade data och realtidskanaler. Med Supabase används bland annat Row Level Security, alltså regler för vilka databasposter en användare får läsa och ändra. Hemliga servernycklar får inte ligga i appen eller Git.

Håll initialt detaljerade spår lokalt och dela senaste positionen. Uppladdad historik är ett separat framtida val. Bestäm vilka som får läsa historik och hur länge den sparas innan funktionen införs.

## Kostnader och externa beroenden

Kostnadsfri drift är ett mål, ingen garanti. Kontrollera aktuella gränser för databas, trafik, realtidsmeddelanden och eventuell pausning av inaktiva gratistjänster. Följ faktisk förbrukning när fler användare ansluts.

Kartbibliotek och kartleverantör är olika val. Öppna kartdata innebär inte automatiskt rätt att masshämta kartbilder. Offlinekartor kräver en tillåten datakälla och lagringslösning. Prototypen använder tile.openstreetmap.de; dess villkor ska bedömas separat.

Kontrollera även licenser för GPS-tillägg och kostnader för distribution, särskilt iOS. iOS-utveckling kräver tillgång till lämplig Mac/Xcode-miljö och iPhone-testning. Android-versionen behöver inte invänta en fullständig iOS-version.

## Utvecklingsetapper

Varje större etapp ska ge en fungerande, verifierad version som går att återställa med Git. Testa det som berörs av ändringen och dokumentera begränsningar.

| Etapp | Innehåll | Klart när |
| --- | --- | --- |
| 1. Grund | Git, projektdokumentation, därefter uppdelning av kod och lokalt paketerade webbresurser. | Prototypens funktioner är bevarade och en fungerande version går att återställa. |
| 2. Lokal Android-spårning | Capacitor, start/stopp, bakgrunds-GPS och beständig lagring. | En promenad med låst skärm registreras och spåret finns kvar när appen öppnas igen. |
| 3. Teknisk kontroll | Bakgrundsöverföring, offlinekartalternativ och en liten iOS-provversion när testmiljö finns. | Centrala teknikval är prövade; kvarvarande iOS-risker är tydliga. |
| 4. Jaktlag och delning | Inloggning, lag, jakter och skyddade realtidspositioner. | Två telefoner ser varandra även med sändarens skärm släckt; obehörig åtkomst nekas. |
| 5. Fältprov och batteri | Anpassade uppdateringar, överföringskö, återanslutning och gamla positioner. | En hel jaktdag har testats med uppmätt batteriförbrukning och nätavbrott. |
| 6. Jaktområdet och offline | Gränser, pass, övriga punkter och nedladdat kartområde. | Egen position, sparad karta och områdesinformation fungerar utan mobiltäckning. |
| 7. iOS och historik | Fullständiga iOS-flöden, distribution och vald historikfunktion. | Grundläggande jakt fungerar mellan Android och iPhone. |
| 8. Hundpositioner | Utvärdera och anslut möjlig datakälla. | Utrustningens integration, rättigheter och kostnader är verifierade. |

Etapp 1: koduppdelningen är implementerad med Vite, TypeScript och npm-paketerad Leaflet 1.9.4. HTML, CSS, karta, positionsbevakning, spår i minnet och statusvisning har separata ansvar. Appens kod och Leaflet-resurser byggs till dist utan CDN-beroende; kartbilder kräver fortfarande nät. Typkontroll, produktionsbygge och grundläggande webbläsarbeteende med simulerad GPS har verifierats. Verklig telefon-GPS har därefter verifierats i Android-steget nedan. Originalprototypen finns i Git-historiken. Första delsteget i etapp 2 är implementerat: Capacitor 8.5.2, Android-projektet med app-ID se.jaktlaget.app, appnamn JaktApp och webDir dist samt @capacitor/geolocation 8.2.2 för vanlig positionering. Webbläsarflödet är bevarat. Typkontroll, webbbygge, Android-synkronisering och debug-APK-bygge med JDK 21 har passerat. Installation och vanlig GPS utomhus har verifierats av projektägaren på OnePlus 9 Pro. Android behåller preliminärt interval: 2000 och minimumUpdateInterval: 1000 ms; rapporterad noggrannhet var omkring ±8 m under gång och ±5 m stillastående. Slutliga intervall ska bestämmas genom fälttest av noggrannhet, spårkvalitet och batteriförbrukning. Bakgrunds-GPS-delen av etapp 2 implementerades först som en avgränsad Android-PoC: lokal Capacitor-plugin, location foreground service, FusedLocationProviderClient, Starta/Stoppa och native-minnesbuffert med återläsning till kartan. Den ursprungliga bufferten innehöll sessions-ID, löpnummer, mättid, mottagningstid och skärmtillstånd, högst 30 000 punkter. Den versionen hade ingen disk-/SQLite-lagring. ACCESS_BACKGROUND_LOCATION, automatisk återstart, synkronisering och iOS-kod ingår fortfarande inte. Projektägaren har godkänt PoC:n efter ett fälttest på OnePlus 9 Pro kl. 19:59:47–20:11:19 (cirka 11 min 32 s): 514 punkter totalt, 427 mottagna med släckt skärm, 432 med låst telefon, största mätlucka 5,1 s och rapporterad slutnoggrannhet ±5 m. Spåret återgav promenaden och riktningsförändringarna även under låsperioden. Räknarna för skärmsläckning och låsning ska inte summeras. Processdöd raderade minnesversionens testdata. Slutliga intervall, spårkvalitet och heldagsbatteri kräver senare fälttest. Beständig lagring är implementerad och godkänd enligt delsteget nedan efter telefonprov med verifierad processdöd. Supabase och delning hör till senare etapper.

Batteriet gick från 21 % till 18 % under testet. Detta är endast en observation på en över fem år gammal telefon med kraftigt degraderat batteri; projektägaren behöver normalt powerbank vid jakt även utan hänsyn till JaktApp. Resultatet ska inte användas som uppskattning av appens normala batteriförbrukning eller som grund för att ändra GPS-intervallet. Önskat intervall 2000 ms och minimiintervall 1000 ms behålls preliminärt tills senare fälttest av noggrannhet, spårkvalitet och batteriförbrukning. Se README för fullständigt testresultat och kvarvarande begränsningar.

## Godkänd PoC: beständig lokal spårlagring

Androids inbyggda SQLite via ett separat native-lager är implementerat som nästa PoC. Databasversion 1 har `tracking_session` och `tracking_sample`, UUID för sessioner och `(session_id, sequence)` för punkter. Tjänsten sparar en GPS-leverans per transaktion på en processgemensam serialiserad arbetstråd, oberoende av WebView och mobiltäckning. WAL och synchronous=NORMAL används. Stopp väntar på accepterade skrivningar och beständig slutstatus.

Sessioner har recording/stopped/interrupted/error. Vid ny process markeras kvarlämnad recording som interrupted utan automatisk GPS-återstart; WebView-omladdning påverkar inte aktiv session. Sparade spår listas och kan öppnas eller uttryckligen raderas, med cascade-radering av punkter. Pågående spår får inte raderas. Ny session bevarar tidigare sessioner. Sidvis återläsning har ingen totalgräns på 30 000 punkter eller 65 sidor.

Databasen ligger privat och undantas från automatisk backup/enhetsöverföring. Ingen automatisk gallring, separat databaslösenordskryptering, synkstatus, Supabase, nya npm-/SQLite-bibliotek, jaktadministration eller iOS-implementation ingår. GPS behåller 2000/1000 ms. Hela valt spår läses fortfarande till WebViews minne för kartvisning; mycket stora spår och heldagsdrift behöver mätas.

Färdiga transaktioner ska överleva processdöd. Punkter före färdig transaktion kan förloras. Strömavbrott/systemkrasch har svagare hållbarhet med WAL/NORMAL; avinstallation/Rensa lagring raderar data. Exakt avbrottstid är okänd och gamla monotona tider jämförs inte med en ny uppstarts klocka. Framtida databasändringar kräver migreringar utan destruktiv återställning.

Projektägaren godkände telefonprovet 2026-09-27: kort promenad med låst skärm, stopp och sparning, verifierad processdöd med adb am kill (sista pidof tom) och återöppning av det sparade spåret. Ett andra spår bevarade det första och båda kunde öppnas. Bilden visar 91 punkter, 79 med släckt skärm, 81 med låst telefon, största mätlucka 2,9 s och ±6 m slutnoggrannhet. UI-rättningen som bevarar oförändrade listalternativ och statistik vid rutinuppdatering är också verifierad på telefonen. Se README för resultat och reproducerbara teststeg. Heldagsdrift och abrupt processdöd under aktiv skrivning återstår att fälttesta. Arkitekturen förbereder ett gemensamt TypeScript-gränssnitt för framtida iOS och separat server-synkning; inga sådana funktioner är implementerade nu.

## Referenser för kommande teknikval

Kontrollera aktuell dokumentation vid implementation; villkor och plattformsregler kan ändras.

- Capacitor: https://capacitorjs.com/docs
- Android platstjänster: https://developer.android.com/develop/background-work/services/fgs/service-types#location
- iOS bakgrundspositionering: https://developer.apple.com/documentation/corelocation/handling-location-updates-in-the-background
- Supabase åtkomstregler: https://supabase.com/docs/guides/database/postgres/row-level-security
- Supabase kostnader: https://supabase.com/pricing
- OSM:s standardkartservers policy (inte samma server som prototypens): https://operations.osmfoundation.org/policies/tiles/
