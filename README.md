# MarketPlace

`MarketPlace` ist ein Minecraft-Plugin fuer Paper 1.20.x, das mehrere Handels- und Wirtschaftsmechaniken in einem gemeinsamen System buendelt. Statt einzelne Plugins fuer Markt, Auktionen, Jobs, Lotto und Spielertausch nebeneinander zu betreiben, stellt dieses Plugin eine zusammenhaengende Handelsoberflaeche bereit. Spieler erhalten damit einen zentralen Einstiegspunkt fuer fast alle wirtschaftlichen Aktionen auf dem Server.

Der Fokus liegt auf einer einfachen Bedienung ueber GUIs. Spieler muessen nicht mit vielen Unterbefehlen arbeiten, sondern oeffnen das System ueber das `Handelsblatt` und navigieren dann durch Menues.

## Kurzfassung

`MarketPlace` ist ein zentrales Wirtschafts- und Handelssystem fuer Minecraft-Server. Es kombiniert Markt, Auktion, Lotto, Direkthandel, Jobs, Preisorientierung, Sidebar und Abholfach in einer gemeinsamen Bedienoberflaeche. Spieler oeffnen das System ueber das `Handelsblatt`, handeln danach fast ausschliesslich ueber Menues und koennen damit ihre Items sicher verkaufen, tauschen, versteigern oder in Aufgaben und Verlosungen einbringen.

## Was das Plugin macht

Das Plugin verbindet folgende Bereiche in einem Gesamtsystem:

- einen Marktplatz fuer normale Verkaeufe zwischen Spielern
- ein Live-Auktionssystem mit Geboten im Chat
- ein Lotto-System mit taeglicher Ziehung
- ein Direkt-Handelssystem zwischen zwei Spielern
- ein Job-System fuer Farm- und Lieferaufgaben
- ein Abholfach fuer Rueckgaben, Gewinne und nicht zustellbare Items
- eine Sidebar fuer Spieler mit `Handelsblatt`, damit wichtige Infos immer sichtbar bleiben
- eine einfache Preisorientierung ueber beobachtete Marktpreise

Das Plugin versucht dabei, typische Probleme von Wirtschaftssystemen auf Minecraft-Servern abzufangen:

- Ist ein Inventar voll, landen Items im Abholfach statt verloren zu gehen.
- Laufen Angebote oder Jobs ab, werden Inhalte ebenfalls gesichert.
- Marktpreise koennen sich ueber beobachtete Verkaeufe einpendeln.
- Wichtige Fortschritte wie angepinnte Jobs oder laufende Auktionen werden sichtbar gemacht.

## Voraussetzungen

- Paper `1.20.x`
- Java `17`
- Optional `Vault`, wenn ein vorhandenes Economy-Plugin verwendet werden soll

Wenn `Vault` vorhanden ist, nutzt das Plugin die registrierte Server-Waehrung. Falls `Vault` nicht vorhanden ist, verwaltet das Plugin interne Guthaben selbst.

## Installation

1. Projekt mit Maven bauen.
2. Die erzeugte JAR in den `plugins`-Ordner des Servers legen.
3. Server starten.
4. Bei Bedarf die Datei `plugins/MarketplacePlugin/config.yml` anpassen.

Wichtige Standardwerte aus der Konfiguration:

- Lotto-Basispot: `250`
- Lotto-Ticketpreis: `25`
- Lotto-Ziehung: `18:00`
- Marktlaufzeit pro Angebot: `24 Stunden`
- Trade-Timeout: `5 Minuten`

## Einstieg fuer Spieler

Der zentrale Zugang zum Plugin ist das `Handelsblatt`.

### Handelsblatt

Das `Handelsblatt` ist ein spezielles Item, mit dem das Hauptmenue geoeffnet wird. Es wird ueber ein shapeless Rezept hergestellt:

- `1x Papier`
- `1x Holzkohle`

Verwendung:

1. `Handelsblatt` herstellen.
2. Das Item ins Inventar legen.
3. Rechtsklick auf das `Handelsblatt`.
4. Im geoeffneten Hauptmenue den gewuenschten Bereich auswaehlen.

Solange ein Spieler ein `Handelsblatt` im Inventar hat, wird ausserdem die Sidebar mit Handelsinformationen angezeigt.

## Hauptmenue

Das Hauptmenue ist die Startoberflaeche des Plugins. Von dort aus werden alle Teilbereiche geoeffnet:

- `Jobs`
- `Markt`
- `Direkthandel`
- `Auktion`
- `Lotto`
- `Abholfach`

Die Bedienung ist komplett menuebasiert. Das ist besonders fuer Spieler praktisch, die lieber klicken als Befehle auswendig zu lernen.

## Funktionen im Detail

### 1. Markt

Der Markt ist fuer klassische Spieler-zu-Spieler-Verkaeufe gedacht.

Was der Markt kann:

- Spieler koennen Angebote einstellen.
- Andere Spieler koennen bestehende Angebote direkt kaufen.
- Angebote werden nach Preis sortiert angezeigt.
- Jedes Angebot laeuft nach `24 Stunden` ab.
- Abgelaufene oder nicht zustellbare Items landen im Abholfach.
- Das System zeigt fuer das aktuelle Item eine Preisorientierung an.

So benutzt man den Markt:

1. Hauptmenue oeffnen.
2. `Markt` waehlen.
3. Zwischen `Angebote`, `Verkaufen` und `Abholfach` waehlen.

So verkauft man ein Item:

1. Im Marktmenue auf `Verkaufen` klicken.
2. Das gewuenschte Item in den vorgesehenen Slot legen oder aus dem Inventar hineinklicken.
3. Den Preis ueber die Preisbuttons anpassen.
4. Das Angebot bestaetigen.

Preissteuerung:

- Feine Anpassung: `+1`, `-1`, `+10`, `-10`
- Grobe Anpassung: `+100`, `-100`, `+1000`, `-1000`

Preislogik:

- Fuer neue Items ohne Vergleichsdaten ist der erste Preis frei waehlbar.
- Sobald Preisbeobachtungen vorhanden sind, darf der Preis nur noch im Bereich von etwa `-10% bis +10%` um den Richtwert liegen.
- Dadurch sollen Ausreisser begrenzt und Marktpreise stabilisiert werden.

So kauft man ein Angebot:

1. `Angebote` im Markt oeffnen.
2. Durch die Seiten blaettern.
3. Auf ein Angebot klicken.
4. Der Kaufpreis wird abgezogen und das Item direkt ins Inventar gelegt.
5. Wenn kein Platz frei ist, landet das Item im Abholfach.

### 2. Auktionen

Die Auktion ist ein Live-System fuer einzelne, oeffentlich sichtbare Versteigerungen. Anders als beim normalen Markt wird hier nicht zu einem Festpreis verkauft, sondern Spieler ueberbieten sich gegenseitig.

Was die Auktion kann:

- immer nur eine aktive Auktion gleichzeitig
- frei einstellbarer Startpreis
- frei waehlbare Laufzeit in Stufen
- Gebote direkt ueber den Chat
- automatische Auszahlung an den Verkaeufer nach erfolgreichem Abschluss
- automatische Rueckgabe bei Abbruch, Serverstopp oder fehlgeschlagener Zustellung

So startet man eine Auktion:

1. Hauptmenue oeffnen.
2. `Auktion` auswaehlen.
3. Item in den Auktionsslot legen.
4. Startpreis einstellen.
5. Laufzeit festlegen.
6. `Auktion starten` klicken.

Wichtige Details:

- Standard-Startpreis: `100 CT`
- Standard-Dauer: `60 Sekunden`
- Dauer erhoeht sich pro Klick um `30 Sekunden`
- Ab `180 Sekunden` springt die Auswahl wieder auf `30 Sekunden`

Wie Gebote funktionieren:

- Spieler schreiben einfach eine Zahl in den Chat.
- Das Gebot muss hoeher sein als das aktuelle Gebot.
- Der Verkaeufer darf nicht auf seine eigene Auktion bieten.
- Das System prueft, ob genug CraftTaler fuer das Gebot vorhanden sind.

Was beim Ende passiert:

- Ohne Gebot geht das Item ins Abholfach des Verkaeufers.
- Mit gueltigem Hoechstgebot werden CraftTaler vom Gewinner eingezogen.
- Der Verkaeufer erhaelt die CraftTaler.
- Der Gewinner erhaelt das Item oder, falls sein Inventar voll ist, einen Eintrag im Abholfach.

### 3. Lotto

Das Lotto ist eine taegliche Verlosung. Je nach verfuegbaren Marktangeboten wird entweder ein echtes Item verlost oder ein Basispot in CraftTalern ausgespielt.

Was das Lotto kann:

- taegliche Ziehung zu einer konfigurierbaren Uhrzeit
- Ticketkauf in mehreren Mengen
- automatische Auswahl eines Gewinners
- Item-Lotto oder CraftTaler-Basispot
- Rueckerstattung aller Tickets, wenn zu wenige Teilnehmer vorhanden sind

Wie das Lotto funktioniert:

- Das System sucht passende Marktangebote als moeglichen Tagesgewinn.
- Bevorzugt werden guenstige Angebote, fuer deren Item bereits Vergleichsdaten existieren oder die mehrfach auf dem Markt vorhanden sind.
- Wenn kein geeignetes Item verfuegbar ist, wird stattdessen ein CraftTaler-Pot verwendet.

So benutzt man das Lotto:

1. Hauptmenue oeffnen.
2. `Lotto` waehlen.
3. `1`, `5` oder `10` Tickets kaufen.
4. Auf die Ziehung warten.

Wichtige Details:

- Standard-Ticketpreis: `25 CT`
- Standard-Basispot: `250 CT`
- Standard-Ziehung: `18:00`
- Mindestens `2 Teilnehmer` sind noetig

Wenn ein Item verlost wird:

- Der Gewinner erhaelt das Item.
- Der urspruengliche Verkaeufer erhaelt den Ticketumsatz in CraftTalern.

Wenn kein Item verlost wird:

- Der Gewinner erhaelt den Basispot plus den Ticketumsatz.

### 4. Direkthandel

Der Direkthandel ist fuer sichere Spieler-zu-Spieler-Geschaefte in Echtzeit gedacht. Zwei Spieler handeln direkt miteinander, ohne Gegenstaende auf den oeffentlichen Markt zu legen.

Was der Direkthandel kann:

- Handelsanfragen zwischen Online-Spielern
- beidseitiges Item-Angebot
- zusaetzliche CraftTaler-Angebote
- beidseitige Bestaetigung
- automatischer Abbruch nach Inaktivitaet
- sichere Rueckgabe von Items bei Abbruch

So benutzt man den Direkthandel:

1. Hauptmenue oeffnen.
2. `Direkthandel` waehlen.
3. Einen Online-Spieler anklicken, um eine Anfrage zu senden.
4. Der andere Spieler nimmt die Anfrage an.
5. Beide Seiten legen ihre Angebote fest.
6. Beide bestaetigen den Handel.

Funktionen im Handelsfenster:

- `Hand-Item hinzufuegen`: nimmt das komplette Stack aus der Hand
- `Letztes eigenes Item entfernen`
- CraftTaler-Angebot anpassen mit `-10`, `+10`, `+100`
- Bestaetigen
- Abbrechen

Wichtige Sicherheitslogik:

- Aendert eine Seite ihr Angebot, werden beide Bestaetigungen zurueckgesetzt.
- Der Handel wird nur abgeschlossen, wenn beide Seiten bestaetigen.
- Vor dem Abschluss wird geprueft, ob beide Spieler genug CraftTaler haben.
- Items werden bei Platzmangel ins Abholfach verschoben.

### 5. Jobs

Das Job-System gibt Spielern wiederkehrende Farm- und Lieferaufgaben. Es ist fuer Server gedacht, auf denen Ressourcen nicht nur verkauft, sondern auch ueber Aufgaben in Umlauf gebracht werden sollen.

Was das Job-System kann:

- jeder Spieler hat gleichzeitig `3 aktive Jobs`
- Jobs bestehen aus konkreten Item-Anforderungen
- Jobs haben eine Laufzeit
- abgeschlossene Jobtypen erhalten einen Cooldown von `1 Tag`
- jeder Job besitzt eine eigene `Job-Kiste`
- ein Job kann angepinnt werden
- angepinnte Jobs koennen ueber die Sidebar verfolgt werden

Aktuell vorhandene Jobtypen:

- `Bauerngut`: Weizen und Karotten liefern
- `Frische Eier`: Eier sammeln
- `Sommerladung`: Melonenscheiben und Kuerbisse liefern
- `Hoflieferung`: Leder, weisse Wolle und Rindfleisch liefern
- `Suesser Vorrat`: Honigflaschen und Zuckerrohr liefern

So benutzt man Jobs:

1. Hauptmenue oeffnen.
2. `Jobs` waehlen.
3. Einen aktiven Job anklicken, um passende Items aus Inventar und Job-Kiste abzugeben.
4. Per Rechtsklick einen Job anpinnen oder loesen.
5. Per Shift-Klick die Job-Kiste oeffnen.

Die Job-Kiste:

- speichert passende Materialien fuer genau diesen Job
- nimmt nur benoetigte Job-Items auf
- ermoeglicht das spaetere Sammeln und Abgeben in mehreren Etappen
- wird bei Ablauf des Jobs automatisch ueber das Abholfach gesichert

Angepinnte Jobs:

- werden in der Sidebar angezeigt
- koennen automatisch passende Items aus dem Inventar einlagern, solange ein `Handelsblatt` im Inventar liegt

### 6. Abholfach

Das Abholfach ist ein Sicherheitsnetz fuer das gesamte Plugin. Immer wenn ein Item nicht direkt ausgegeben oder sauber zurueckgelegt werden kann, landet es dort.

Typische Gruende fuer Eintraege im Abholfach:

- Marktangebot abgelaufen
- Marktkauf bei vollem Inventar
- Rueckgabe nicht eingestellter Verkaufsitems
- Auktionsgewinn oder Auktionsrueckgabe
- Rueckgabe aus abgebrochenem Handel
- Job-Kisten-Inhalte nach Ablauf
- Lotto-Gewinne bei vollem Inventar oder offline Gewinnern

So benutzt man das Abholfach:

1. Hauptmenue oder Untermenue oeffnen.
2. `Abholfach` waehlen.
3. Durch die Seiten blaettern.
4. Ein Item anklicken, um es ins Inventar zu uebernehmen.

Zu jedem Eintrag werden Quelle, letzter Preis, Zusatzinfo und Zeitpunkt gespeichert.

### 7. Sidebar

Spieler mit `Handelsblatt` im Inventar erhalten eine Sidebar mit Live-Informationen.

Die Sidebar zeigt:

- die aktuell laufende Auktion
- den aktuellen Preis
- die Restzeit der Auktion
- den Fortschritt des angepinnten Jobs

Das ist besonders nuetzlich, wenn Spieler nebenbei farmen, handeln oder auf eine Auktion bieten wollen, ohne Menues staendig neu zu oeffnen.

## So nutzt man das Plugin im Spielalltag

Ein typischer Ablauf auf einem Server kann so aussehen:

1. Spieler craften sich ein `Handelsblatt`.
2. Ueber Rechtsklick wird das Hauptmenue geoeffnet.
3. Ueber `Jobs` werden Ressourcen verdient und strukturiert gesammelt.
4. Ueberschuessige Items werden im `Markt` verkauft.
5. Seltene oder wertvolle Items werden ueber `Auktion` versteigert.
6. Fuer private Geschaefte zwischen zwei Spielern wird `Direkthandel` benutzt.
7. Wer Risiko und Glueck mag, kauft Tickets im `Lotto`.
8. Nicht zustellbare Gewinne oder Rueckgaben werden jederzeit ueber das `Abholfach` abgeholt.

## Hinweise fuer Serverbetreiber

- Das Plugin speichert seine Daten in mehreren YAML-Dateien im Plugin-Datenordner.
- Preisrichtwerte entstehen dynamisch aus echten Marktbeobachtungen.
- Das System ist stark auf GUI-Nutzung ausgelegt.
- Die Wirtschaft funktioniert mit Vault-Anbindung am sinnvollsten, kann aber auch ohne Vault laufen.
- Da Auktionen und Lotto direkt in die Spielwirtschaft eingreifen, sollten Ticketpreise, Basispot und allgemeine Serverwirtschaft aufeinander abgestimmt werden.
