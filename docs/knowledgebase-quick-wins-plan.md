# Implementasjonsplan for lavthengende frukt

## Formål

Legge til seks små, høyverdi-funksjoner i kunnskapsbasen ved i størst mulig grad å gjenbruke
eksisterende byggeklosser (eksporttjeneste, slug-generering, DownloadHandler-mønster,
draft-synlighetslogikk), uten arkitekturendringer eller databasemigreringer.

## Avgrensning

Planen omfatter:

1. Import av zip (motstykket til eksport).
2. Hurtigtaster i editoren (Ctrl+S / Esc).
3. Kopier lenke til artikkel.
4. Last ned enkeltartikkel som `.md`.
5. «Nylig oppdatert»-panel.
6. Dupliser artikkel.

Tags, versjonshistorikk og søke-highlighting er bevisst utelatt (vesentlig høyere innsats).

## Rekkefølge og commits

Småplukkene (punkt 2–6) implementeres først som separate små commits, importen (punkt 1)
til slutt som den største jobben. Hvert punkt verifiseres med `mvn test` grønt før commit,
pluss manuell sjekk i kjørende app.

---

## 1. Import av zip

### Kontekst: eksportformatet

Eksport-zip-en inneholder rene markdown-filer der filnavn = slug og mappestruktur =
kategoritre. Filene bærer **ingen** metadata om tittel eller status. `welcome.md` i roten
er en statisk ressurs.

### Design

- Ny service `KnowledgeBaseImportService` i `io.avec.knowledgebase.service`:
  - Tar imot zip-bytes og går gjennom entries.
  - Mapper → kategorier: finn-eller-opprett på navn + forelder via `CategoryService`.
  - `.md`-filer → artikler:
    - slug = filnavn uten `.md`
    - tittel = første `# `-overskrift i innholdet, ellers filnavnet
    - innhold = filens råtekst
    - status = **DRAFT** (eksporten bærer ikke status; draft er tryggest — ingenting
      publiseres utilsiktet)
  - `welcome.md` i roten hoppes over. Ukjente filtyper ignoreres.
- **Konflikthåndtering (avklart):** finnes slug fra før → artikkelen hoppes over.
  Eksisterende artikler røres aldri.
- Resultatobjekt `ImportResult`: antall importerte artikler, liste over hoppet over
  (slugs), antall nye kategorier.
- **Sikkerhet/robusthet:** `@PreAuthorize("hasRole('ADMIN')")`, tak på antall entries og
  filstørrelse per entry (innhold over 20 000 tegn — som matcher `@Size` på entiteten —
  avvises), defensiv håndtering av korrupt zip.

### UI

- «Import»-knapp ved siden av Export (kun admin).
- Dialog med Vaadin `Upload` begrenset til `.zip`.
- Etter import vises oppsummering (x importert, y hoppet over, z nye kategorier) og
  treet refreshes.

### Tester

- `KnowledgeBaseImportServiceTest`:
  - Round-trip: eksport → import i tom base gir samme struktur.
  - Konflikt: eksisterende slug hoppes over og rapporteres.
  - Tittelutledning fra `# `-overskrift og fallback til filnavn.
  - Nøstede kategorier gjenskapes.
  - Korrupt / for stor zip avvises uten sideeffekter.

---

## 2. Hurtigtaster i editor

- `Ctrl+S` → lagre, `Esc` → avbryt.
- Registreres når editormodus åpnes og fjernes når den lukkes: hold på
  `ShortcutRegistration`-referansene i `KnowledgeBaseView`.
- Vaadin preventer nettleserens Ctrl+S-default automatisk.
- Verifiseres manuelt (UI-oppførsel).

---

## 3. Kopier lenke

- Ikonknapp i artikkelheaderen (alle innloggede).
- Legger `<base-url>/knowledge/<slug>` på utklippstavlen via
  `navigator.clipboard.writeText`.
- Notification «Lenke kopiert» ved suksess.

---

## 4. Last ned artikkel som `.md`

- Nedlastingsknapp i artikkelvisningen (alle innloggede).
- Gjenbruker `DownloadHandler`-mønsteret fra zip-eksporten.
- Filnavn `<slug>.md`, innhold = artikkelens markdown.

---

## 5. «Nylig oppdatert»-panel

- Vises i høyrepanelet når ingen artikkel er valgt.
- De 10 sist oppdaterte synlige artiklene: tittel + oppdatert-dato, klikk navigerer til
  artikkelen.
- Nye repo-metoder: `findTop10ByOrderByUpdatedAtDesc()` og
  `findTop10ByStatusOrderByUpdatedAtDesc(status)`.
- Ny servicemetode `ArticleService.findRecentVisible()` som respekterer
  draft-synlighet (samme mønster som `findVisibleByCategory`).
- Enkel servicetest for synlighetslogikken.

---

## 6. Dupliser artikkel

- Admin-knapp på valgt artikkel.
- Ny `ArticleService.duplicate(article)`:
  - Kopierer tittel (+ « (kopi)»), innhold og kategori.
  - Status DRAFT.
  - Slug via eksisterende `generateUniqueSlug`.
- Kopien åpnes i editormodus etter opprettelse.
- Servicetest for felt-kopiering og slug-unikhet.

---

## Berørte filer

| Fil | Punkter |
|---|---|
| `knowledgebase/view/KnowledgeBaseView.java` | alle (UI) |
| `knowledgebase/service/KnowledgeBaseImportService.java` (ny) | 1 |
| `knowledgebase/service/ArticleService.java` | 5, 6 |
| `knowledgebase/data/ArticleRepository.java` | 5 |
| nye tester under `src/test/java/io/avec/knowledgebase/` | 1, 5, 6 |

## Verifisering

- `mvn test` grønt etter hvert punkt.
- Manuell sjekk i kjørende app (hot reload via JRebel på port 8080).

## Åpne beslutninger

- Importstatus DRAFT er valgt som trygg default — kan endres til PUBLISHED hvis ønskelig.
