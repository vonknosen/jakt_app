# JaktApp

JaktApp är ett hobbyprojekt för ett mindre jaktlag. Målet är ett alternativ med funktionalitet liknande WeHunt, med löpande kostnader så nära noll som praktiskt möjligt.

Android utvecklas och testas först. iPhone/iOS ska stödjas senare. Kod ska delas mellan plattformarna där det är lämpligt, med plattformsspecifika lösningar för exempelvis bakgrundspositionering och behörigheter.

## Status idag

Appen är en tidig prototyp i `index.html`: en webbsida med Leaflet, OpenStreetMap-kartbilder och webbläsarens positionsfunktion. Spåret finns endast i sidans minne. Bakgrundsspårning, beständig lagring, användarkonton och delning är inte implementerade.

Prototypens struktur behöver inte bevaras om en annan struktur passar slutmålet bättre.

## Dokumentation

- [PROJEKTPLAN.md](PROJEKTPLAN.md): mål, rekommenderad arkitektur, öppna beslut och utvecklingsetapper.
- [AGENTS.md](AGENTS.md): arbetsinstruktioner för Codex och andra kodassistenter.

## Repository och arbetssätt

Detta repository, `vonknosen/jakt_app`, är projektets fortsatta utvecklingsgrund. Arbeta stegvis och använd Git för att kunna återställa fungerande versioner. Den tidigare fristående projektmappen är ingen parallell utvecklingskälla.

Projektägaren är inte professionell programmerare. Förklara större tekniska beslut begripligt innan stora arkitekturförändringar genomförs.
