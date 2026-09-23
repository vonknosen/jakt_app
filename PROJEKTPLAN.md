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

GitHub-repositoryt är klonat och originalprototypen finns i historiken. Dokumentationsarbetet inleder etapp 1; koduppdelning och Capacitor är ännu inte genomförda.

## Referenser för kommande teknikval

Kontrollera aktuell dokumentation vid implementation; villkor och plattformsregler kan ändras.

- Capacitor: https://capacitorjs.com/docs
- Android platstjänster: https://developer.android.com/develop/background-work/services/fgs/service-types#location
- iOS bakgrundspositionering: https://developer.apple.com/documentation/corelocation/handling-location-updates-in-the-background
- Supabase åtkomstregler: https://supabase.com/docs/guides/database/postgres/row-level-security
- Supabase kostnader: https://supabase.com/pricing
- OSM:s standardkartservers policy (inte samma server som prototypens): https://operations.osmfoundation.org/policies/tiles/
