# Arbetsinstruktioner för JaktApp

## Läs först

Läs README.md och PROJEKTPLAN.md innan större arbete. De beskriver nuläge, mål, arkitektur och etapper. Skilj alltid planerade funktioner från implementerade funktioner.

## Samarbete

- Projektägaren är inte professionell programmerare. Kommunicera på svenska och förklara tekniska beslut med konkreta exempel.
- Förklara vad en större arkitekturförändring innebär och varför den behövs innan den genomförs. Håll arbetet inom överenskommen omfattning.
- Arbeta i små steg med en fungerande version efter varje större etapp.
- Redovisa ändrade filer, verifiering och kvarvarande begränsningar.
- Om uppgiften gäller dokumentation eller analys ska appkoden inte ändras utan att det ingår i uppdraget.

## Teknisk inriktning

- Android först, senare iOS. Dela kod där det är lämpligt och isolera nödvändiga plattformsskillnader.
- Capacitor är huvudspår och Supabase backendkandidat; båda behöver verifieras mot kraven.
- Insamling, beständig lagring och synkronisering får inte vara beroende av en aktiv webbkarta.
- Beakta batteri, nätavbrott, gamla positioner, åtkomstkontroll och kostnader i berörda ändringar.
- Inför inte betalda beroenden eller externa tjänster som om kostnadsfrågan redan vore avgjord.
- Uppdatera projektdokumentationen när beslut eller faktisk implementation ändras.

## Git och verifiering

- Kontrollera arbetskatalog, remote och git status innan ändringar. Detta repository är utvecklingskällan; skriv inte i en äldre fristående kopia.
- Bevara användarens befintliga ändringar. Undvik destruktiva Git-kommandon.
- Använd begripliga, avgränsade commits som ger återställningsbara versioner.
- Push till GitHub får inte göras utan projektägarens uttryckliga godkännande. Lokala commits får göras när de ingår i en överenskommen uppgift.
- Testa relevant beteende. Bakgrunds-GPS, batteri och plattformsskillnader behöver verifieras på riktiga telefoner; en fungerande webbförhandsvisning bevisar inte detta.
- Lägg aldrig lösenord, tokens eller hemliga servernycklar i Git.
