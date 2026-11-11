# Dunories

**Kalorien, Makros und Gewicht im Blick – mit einem Foto.**

<p align="center">
  <img src="docs/dunories-hauptbild.jpg" alt="Dunories – Foto machen. Fertig." width="480">
</p>

Dunories ist eine Android-App zum Tracken von Kalorien, Makro- und Nährwerten. Essen wird per Foto, Barcode oder kurzer Beschreibung erfasst; eine KI schätzt die Nährwerte, und jedes Ergebnis bleibt ein bearbeitbarer Vorschlag. Alle Daten liegen ausschließlich lokal auf dem Gerät – ohne Konto, ohne Cloud, ohne Tracking.

## Funktionen

- **Erfassen per Foto, Barcode oder Text** – Teller fotografieren, Barcode scannen (Open Food Facts), Nährwerttabelle ablesen oder einfach beschreiben bzw. einsprechen
- **„Falsch erkannt?“** – Korrektur tippen oder sprechen, die KI schätzt mit Foto und Hinweis neu
- **Tagesübersicht** mit vier konzentrischen Ringen für Kalorien, Protein, Kohlenhydrate und Fett, Mahlzeiten, Favoriten und gespeicherten Kombis
- **Persönliches Tagesziel** aus Grundumsatz (Mifflin-St-Jeor) × Alltagsfaktor ohne Sport (DGE) und Gewichtsziel; Sport wird als Aktivität eingetragen und erhöht das Budget
- **Gewichtsziel** mit Wunschgewicht und Zieldatum, empfohlenem Tempo und täglich angepasstem Plan
- **Ziel erreicht** – Feier-Bildschirm und teilbare Erfolgsbilder in sechs Designs (Story, Post, Quadrat; eigener Foto- oder transparenter Hintergrund)
- **Statistik & Kalender** – Durchschnitte, Nährwertverlauf, farbiger Kalender, KI-Wochenauswertung
- **Gewicht & Körper** – Messungen mit geglättetem Trend, Körpermaße
- **Intervallfasten**, Erinnerungen, dauerhafte Kalorien-Benachrichtigung und Homescreen-Widget
- **Vitamine & Mineralstoffe**, alle Nährwerte in Etikett-Reihenfolge
- **Datensicherung** als ZIP-Datei zum Teilen oder Speichern

## Technik

- Kotlin, Jetpack Compose, Material 3
- Room (lokale Datenbank), Navigation Compose, WorkManager
- ML Kit (Barcode), Google Gemini (Bilderkennung und Nährwertschätzung, Antworten als JSON mit festem Schema)
- Manuelle Dependency Injection, MVVM mit `StateFlow`
- minSdk 28, targetSdk 37

## Selbst bauen

1. Repository klonen und in Android Studio öffnen (JDK 21).
2. Einen eigenen Gemini-API-Schlüssel in [Google AI Studio](https://aistudio.google.com/apikey) erstellen.
3. In `local.properties` im Projektordner eintragen (die Datei ist von Git ausgeschlossen):
   ```properties
   GEMINI_API_KEY=dein_schluessel
   ```
4. Bauen und installieren:
   ```bash
   ./gradlew installDebug
   ```

Ohne Schlüssel läuft die App ebenfalls – KI-Funktionen fallen dann auf die manuelle Eingabe zurück.

## Datenschutz

Alle Einträge, Messungen und Einstellungen bleiben auf dem Gerät. Netzwerkzugriffe gibt es nur für Open Food Facts (Barcode-Daten) und Google Gemini (Fotos und Beschreibungen zur Nährwertschätzung). Es gibt kein Konto, keinen eigenen Server und keine Analyse- oder Werbedienste.

## Lizenz

© JannekHas. Alle Rechte vorbehalten. Der Code darf angesehen, aber ohne Erlaubnis nicht kopiert, verändert oder weiterverbreitet werden.

Die enthaltene Schrift *Bricolage Grotesque* steht unter der SIL Open Font License (siehe `app/src/main/assets/licenses/`).
