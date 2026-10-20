# Kalorien-App – Projektspezifikation

Private Android-App (nur für mich) zum Tracken von Kalorien, Makros und Nährwerten.
Alles läuft lokal auf dem Handy – **kein eigener Server, keine laufenden Kosten**.

## Tech-Stack
- **Kotlin + Jetpack Compose** (native Android, Material 3)
- **Room** (lokale SQLite-Datenbank) für alle Daten
- **CameraX** für die Kamera
- **ML Kit Barcode Scanning** (on-device) zur Barcode-Erkennung im Foto
- **Open Food Facts API** für Nährwerte von Barcode-Produkten
- **Gemini API** (Flash-Modell, kostenloses Kontingent) für Foto-, Text- und Videoanalyse
- **Health Connect** für Daten aus Samsung Health (aktive Kalorien)
- Kein Play Store – Installation per APK / USB-Debugging
- Gemini-API-Key in `local.properties` (NICHT ins Git-Repo), per BuildConfig in die App

## Features

### 1. Onboarding & Profil
- Eingabe: Geschlecht, Alter, Größe, Gewicht, Ziel (Abnehmen / Halten / Zunehmen), Tempo
- Frage: „Samsung Health / Health Connect verbinden?“
  - **Ja** → Grundbedarf wird mit Aktivitätsfaktor „sitzend“ berechnet, aktive Kalorien aus Health Connect kommen täglich dazu (damit Bewegung nicht doppelt zählt)
  - **Nein** → Aktivitätslevel manuell wählen
- Grundumsatz nach Mifflin-St-Jeor, moderates Defizit/Überschuss als Standard (ca. 300–500 kcal)
- Makroziele: Protein in g/kg Körpergewicht, Rest auf Fett/Kohlenhydrate verteilt; alles nachträglich anpassbar
- Einstellbar: Wie viel % der verbrannten Kalorien gutgeschrieben werden (Standard z. B. 60 %)

### 2. Tagesübersicht (Startbildschirm)
- Verbleibende Kalorien = Ziel + gutgeschriebene aktive Kalorien − Gegessenes
- Fortschrittsanzeigen für Protein, Kohlenhydrate, Fett
- Liste der heutigen Einträge, gruppiert nach Mahlzeit (Frühstück, Mittag, Abend, Snacks)
- Einträge bearbeiten / löschen

### 3. Ein-Foto-Erfassung (Kernfeature)
- Ein Kamera-Button für alles:
  1. Foto aufnehmen (oder aus Galerie wählen)
  2. Lokal mit ML Kit nach Barcode suchen
  3. **Barcode gefunden** → Open Food Facts abfragen → Produkt + Nährwerte pro 100 g, Menge eingeben
  4. **Kein Barcode** → Foto an Gemini, Antwort als strukturiertes JSON (erkannte Lebensmittel, geschätzte Menge in g, kcal, Protein, KH, Fett je Komponente)
- Ergebnis ist **immer ein bearbeitbarer Vorschlag**: Komponenten, Mengen und Werte anpassen, Komponenten hinzufügen/entfernen, dann bestätigen
- Fallback: manuelle Eingabe und Textbeschreibung („2 Scheiben Vollkornbrot mit Käse“) → Gemini schätzt
- Häufig gegessene Einträge / Favoriten schnell wieder hinzufügen

### 4. Rezepte-Tab
- Rezepte mit Zutaten, Mengen, Schritten, Portionen; Makros und kcal pro Portion automatisch berechnet
- Rezept als Mahlzeit eintragen (mit Portionsanzahl)
- **Import über Android-Teilen-Menü** (App als Share-Ziel registrieren):
  - **Stufe 1 – TikTok-Link (text/plain):** Über TikToks offiziellen öffentlichen oEmbed-Endpunkt die Videobeschreibung holen → an Gemini → Rezept mit Zutaten, Mengen, Schritten, Makros
  - **Stufe 2 – Videodatei (video/\*):** Ein über TikToks „Video speichern“ gespeichertes Video an die App teilen → Upload an Gemini (Bild + Ton) → Rezept
  - Wenn Stufe 1 kein Rezept findet: Hinweis anzeigen, das Video zu speichern und als Datei zu teilen
- Importierte Rezepte sind ebenfalls bearbeitbare Vorschläge; Quell-Link wird gespeichert
- Kein automatischer Video-Download aus TikTok

### 5. Health Connect
- Berechtigungen für aktive Kalorien (ggf. Schritte) anfragen
- Täglich die aktiven Kalorien lesen und in die Tagesübersicht einrechnen (mit eingestelltem Prozentsatz)

### 6. Kalender & Statistiken
- Kalenderansicht: Tage farbig nach Zielerreichung, Tag antippen → Details
- Gewichtseintrag + Gewichtsverlauf
- Wochen-/Monatsdurchschnitte für kcal und Makros, Proteinziel-Trefferquote
- Optional: KI-Wochenauswertung per Gemini (kurze Muster und Verbesserungsvorschläge)

### 7. Export / Import (niedrige Priorität)
- Komplette Datenbank als JSON-Datei exportieren und wieder importieren

## Umsetzungsreihenfolge
1. Projekt-Setup, Profil/Onboarding, Zielberechnung, manuelle Einträge, Tagesübersicht
2. Kamera + Barcode (ML Kit + Open Food Facts) + Gemini-Fotoanalyse mit Bearbeitungsdialog
3. Health Connect
4. Kalender, Gewicht, Statistiken
5. Rezepte-Tab (manuell), dann Share-Import (Stufe 1, dann Stufe 2)
6. Export/Import

Nach jeder Phase soll die App baubar und auf dem Handy nutzbar sein.

## Hinweise
- UI-Sprache: Deutsch
- Gemini-Antworten immer als JSON mit festem Schema anfordern und robust parsen (Fehlerfall → manuelle Eingabe)
- Kein Login, keine Cloud, keine Analytics
