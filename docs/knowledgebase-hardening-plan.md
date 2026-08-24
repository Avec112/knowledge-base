# Knowledge Base – plan for lavkostnadsherding

## Formål

Gjøre `io.avec.knowledgebase` tryggere og enklere å flytte inn i et eksisterende Vaadin/Spring Boot-prosjekt, uten å omskrive PoC-en eller innføre unødvendige arkitekturlag.

## Avgrensning

Planen dekker konkrete feil og forbedringer med lav kostnad. Den dekker ikke full modularisering, revisjonshistorikk, nytt rettighetssystem, større UI-redesign eller generell ytelsesoptimalisering.

## Føringer

- Behold dagens lagdeling: Vaadin view → Spring services → Spring Data repositories → JPA entities.
- Legg sikkerhets- og synlighetsregler i service-/sikkerhetslaget; skjulte knapper er bare UX.
- Gjør små, testbare endringer i stedet for å skrive om `KnowledgeBaseView`.
- Behold databasebegrensninger som siste forsvar, blant annet unike slugs og optimistisk låsing.
- Ikke flytt eller aktiver demodata i produksjon.

---

## Fase 1 – tilgang og synlighet (kritisk)

### 1.1 Sentraliser artikkelsynlighet

**Mål:** En vanlig bruker skal aldri kunne lese eller finne et utkast, heller ikke via kjent slug eller som ukategorisert artikkel.

**Endringer**

- `ArticleRepository`
  - legg til oppslag på `slug + status`
  - legg til oppslag på `category is null + status`
- `ArticleService`
  - innfør eksplisitte metoder for synlig slug og synlige ukategoriserte artikler
  - unngå at viewet selv må kjenne alle repository-kombinasjoner
- `KnowledgeBaseView`
  - bruk synlighetsmetodene i `setParameter()` og `refreshArticleList()`
  - behold dagens publiseringsfilter for kategorier og søk, men samle policyen der det er rimelig

**Akseptansekriterier**

- Admin kan åpne og finne både `DRAFT` og `PUBLISHED`.
- Ikke-admin kan bare åpne `PUBLISHED`.
- Direkte URL til et utkast gir samme «ikke funnet»-oppførsel som ukjent slug.
- Ukategoriserte utkast vises ikke for ikke-admin.

### 1.2 Autoriser alle mutasjoner i backend

**Mål:** Det skal ikke være mulig å skrive eller slette wikiinnhold bare fordi en ny klient eller et nytt view kaller tjenesten direkte.

**Endringer**

- aktiver Spring Method Security dersom det ikke allerede gjøres av enterpriseplattformen
- beskytt følgende serviceoperasjoner med administratorrolle:
  - `ArticleService.save/delete`
  - `CategoryService.save/delete/reorderRootCategories`
- behold `isAdmin` og skjulte knapper som UI-hjelp

**Akseptansekriterier**

- ADMIN kan utføre alle mutasjoner.
- USER får `AccessDeniedException` ved direkte servicekall.
- Lesemetoder er fortsatt tilgjengelige for autentiserte brukere i tråd med synlighetspolicyen.

> Ved innflytting må annotasjonene tilpasses enterpriseprosjektets eksisterende rolle-/permission-navn.

---

## Fase 2 – kategorihierarki og sikker sletting

### 2.1 Behold parent ved redigering

**Mål:** Redigering av navn eller beskrivelse skal ikke flytte en underkategori til roten.

**Endringer**

- fjern ubetinget `category.setParent(null)` ved redigering
- sett `parent = null` bare når det faktisk opprettes en rotkategori

**Akseptansekriterier**

- En underkategori beholder samme parent etter redigering.
- En ny kategori opprettet fra dagens «Create»-handling blir fortsatt en rotkategori.

### 2.2 Gjør kategorisletting eksplisitt og trygg

**Mål:** Ingen underkategorier eller artikler skal slettes som en skjult konsekvens av én handling.

**Endringer**

- flytt valideringen til `CategoryService.delete()` slik at alle klienter får samme regel
- avvis sletting dersom kategorien har:
  - direkte artikler
  - underkategorier
- legg til en enkel Vaadin-bekreftelsesdialog før gyldig sletting
- behold dagens cascade inntil datamodellen eventuelt besluttes endret; servicevalideringen hindrer utilsiktet bruk

**Akseptansekriterier**

- Kategori med artikler kan ikke slettes.
- Kategori med barn kan ikke slettes.
- Tom bladkategori kan slettes etter eksplisitt bekreftelse.
- Avbrutt dialog endrer ingen data.

### 2.3 Stabil sortering av barn

**Mål:** Underkategorier skal vises deterministisk.

**Endringer**

- sorter barn etter `sortOrder`, deretter `id`, ved trebygging, eller bruk eksisterende ordnet repository-metode
- unngå større lazy-loading-ombygging i denne fasen

**Akseptansekriterier**

- Samme data gir samme rekkefølge etter refresh og omstart.

---

## Fase 3 – korrekt og testbar eksport

### 3.1 Trekk ZIP-generering ut i egen tjeneste

**Mål:** Eksport skal være komplett, testbar og uavhengig av Vaadin-viewets tilstand.

**Ny klasse**

- `knowledgebase/service/KnowledgeBaseExportService`

**Ansvar**

- rekursiv traversering av kategorier
- eksport av artikler i alle nivåer
- forskjell mellom full admin-eksport og eksport av bare publiserte artikler
- sikre og stabile katalog-/filnavn
- kollisjonshåndtering, eksempelvis slug eller ID som fallback/suffiks
- inkludering av velkomstfilen

**View-endring**

- `KnowledgeBaseView` oppretter kun `StreamResource` og delegerer genereringen
- feil skal logges og presenteres; ikke returner en tilsynelatende vellykket tom ZIP

**Akseptansekriterier**

- Artikler i rot- og underkategorier finnes i ZIP-filen.
- Ikke-admin-eksport inneholder ikke utkast.
- Like/sanitiserte kategorinavn fører ikke til dupliserte ZIP entries.
- Genereringsfeil resulterer ikke i en stille null-byte-fil.

---

## Fase 4 – konsistent UI-tilstand

### 4.1 Synkroniser rute etter lagring og sletting

**Mål:** URL og synlig artikkel skal alltid representere samme tilstand.

**Endringer**

- etter lagring: naviger til den lagrede artikkelens slug
- etter sletting: naviger til velkomstsiden og la denne etablere state
- nullstill gammel tittel/Markdown når ingen artikkel vises

**Akseptansekriterier**

- Refresh etter opprettelse/lagring viser samme artikkel.
- Sletting etterlater ikke slettet slug eller gammelt innhold på skjermen.

### 4.2 Beskytt ulagrede endringer

**Mål:** Navigasjon skal ikke stille forkaste en påbegynt redigering.

**Lavkostvalg**

- deaktiver tree, quick-jump og andre navigasjonshandlinger i edit mode
- gjenaktiver dem etter Save eller Cancel

**Eventuell liten forbedring**

- bruk bekreftelsesdialog ved forsøk på navigasjon dersom dette kan gjøres uten omfattende state-maskin

**Akseptansekriterier**

- Det er ikke mulig å bytte artikkel og miste tekst uten eksplisitt Save, Cancel eller bekreftelse.

---

## Fase 5 – robusthet og enkel opprydding

### 5.1 Slug-kollisjoner

**Mål:** Konkurrerende opprettelser skal gi kontrollert resultat.

**Endringer**

- behold unik databaseconstraint
- håndter duplicate-key ved lagring med et lite, avgrenset retry eller en forståelig valideringsfeil
- legg test på vanlige sekvensielle kollisjoner; concurrency-test kan vente dersom måldatabasen ikke er bestemt

### 5.2 Søk med `%` og `_`

**Mål:** Brukerens søketekst skal behandles som tekst, ikke utilsiktede SQL `LIKE`-wildcards.

**Endringer**

- escape `%`, `_` og escape-tegnet i repository-query, eller dokumenter wildcard-støtte dersom det faktisk ønskes

### 5.3 Fjern ubrukt/global CSS

**Mål:** Wiki-CSS skal ikke påvirke andre enterprise-views.

**Endringer**

- fjern den ubrukte visual-editor-blokken knyttet til `.knowledge-base-view`
- fjern eller scope global `vaadin-form-layout[empty]`
- rett editorens 60 % + 40 % + gap-overflyt med flex grow-ratio
- legg til ett enkelt breakpoint som stabler editor og Markdown-hjelp ved smal bredde

### 5.4 Behold TreeGrid-virtualisering

**Mål:** Unngå unødvendig rendering av alle rader.

**Endring**

- fjern `articleTree.setAllRowsVisible(true)`

**Ikke del av lavkostfasen**

- full lazy hierarchy-provider
- pageable søk/count
- større EAGER/LAZY-omlegging

Disse tas bare dersom reell datamengde viser behov.

---

## Fase 6 – databasedistribusjon og portabilitet

### 6.1 Skill demo og produksjon

**Endringer**

- flytt `data.sql` og kjente demobrukere til en eksplisitt dev/test-profil
- deaktiver automatisk demo-initialisering som standard
- ikke inkluder demoens profilbilder eller H2-spesifikke binærliteraler i produksjonsmigreringer

### 6.2 Opprett migreringer

Ved innflytting i målprosjektet:

- opprett Flyway- eller Liquibase-migrering for `kb_category` og `kb_article`
- tilpass ID/sequence-strategi til enterpriseprosjektet
- velg eksplisitt databasekolonnetype for Markdown-innhold (`TEXT`/CLOB etter måldatabase)
- vurder om auditfelter skal bruke enterpriseprosjektets Spring Data Auditing

Denne fasen bør gjøres mot faktisk måldatabase, ikke låses til H2 i PoC-en.

---

## Testplan

Prioriter raske service-/repository-tester fremfor tung Vaadin TestBench.

### Minimum

1. Published/draft lookup for admin og vanlig bruker.
2. Ukategoriserte utkast filtreres bort.
3. Mutasjoner avvises for USER og tillates for ADMIN.
4. Kategori med artikler kan ikke slettes.
5. Kategori med barn kan ikke slettes.
6. Underkategori beholder parent ved redigering.
7. Eksport inkluderer nestede kategorier.
8. Ikke-admin-eksport utelater utkast.
9. Eksport tåler kolliderende sanitiserte navn.
10. Unike slugs genereres ved samme tittel.
11. Søk etter `%` og `_` behandles som avtalt.

### Manuell UI-smoke-test

- opprett, preview, lagre, refresh
- rediger og cancel
- forsøk navigasjon mens redigering pågår
- slett artikkel og kategori
- åpne kjent draft-URL som USER
- last ned og inspiser eksport som USER og ADMIN
- kontroller smal viewport

---

## Foreslått commit-rekkefølge

1. `fix(kb): enforce published article visibility`
2. `fix(kb): secure knowledge base mutations`
3. `fix(kb): preserve and safely delete category hierarchy`
4. `refactor(kb): extract recursive export service`
5. `fix(kb): keep route and edit state consistent`
6. `fix(kb): harden slug and search behavior`
7. `style(kb): scope and simplify knowledge base css`
8. `test(kb): cover visibility hierarchy and export`
9. `chore(kb): isolate demo seed data` (tilpasses målprosjekt/profil)

Hver commit skal bygge og testene skal være grønne før neste fase.

## Ferdigdefinisjon

Lavkostnadsherdingen er ferdig når:

- utkast ikke lekker til vanlige brukere
- backend håndhever skrivetilgang
- kategorihierarkiet ikke endres eller slettes utilsiktet
- eksporten er rekursiv og feiler tydelig
- URL, valgt artikkel og edit state er konsistente
- kritisk service-/repository-adferd har automatiserte tester
- demo-seed er skilt fra produksjonsoppsett
- `mvn test` er grønn

## Bevisst utsatt

- full oppdeling av `KnowledgeBaseView`
- fullverdig modul/plugin-SPI
- avansert permissions per kategori
- revisjonshistorikk
- lazy tree loading og søkeindeks
- generell responsiv redesign
- import/restore
