# Implementasjonsplan for gjenstående kodegap

## Formål

Lukke de gjenstående kodegapene etter lavkostnadsherdingen uten å innføre større arkitekturendringer, databasearbeid eller tung UI-testinfrastruktur.

## Avgrensning

Planen omfatter:

1. Fjerning av ufiltrert artikkeltilgang gjennom kategorier.
2. Deterministisk artikkelsortering.
3. Scoping av knowledge-base-CSS.
4. Målrettede regresjonstester for synlighet og eksport.

Migreringer, enterprise-auditing, concurrency-tester, større refaktorering av `KnowledgeBaseView` og omfattende Vaadin TestBench-dekning er ikke del av denne planen.

---

## 1. Fjern ufiltrert kategorilesing

### Berørte filer

- `src/main/java/io/avec/knowledgebase/service/CategoryService.java`
- `src/main/java/io/avec/knowledgebase/data/CategoryRepository.java`
- `src/main/java/io/avec/knowledgebase/data/Category.java`
- relevante service- og repository-tester

### Endringer

1. Fjern `CategoryService.findByIdWithArticles()`.
2. Fjern `CategoryRepository.findByIdWithArticles()`.
3. Fjern den ubrukte inverse `Category.articles`-relasjonen, inkludert getter og setter.
4. Behold `Article.category` som eiende relasjon.
5. Behold slettesjekken gjennom `ArticleRepository.existsByCategory()`.

### Begrunnelse

- Kategorien trenger ikke eksponere artikler direkte.
- All artikkellesing skal gå gjennom `ArticleService`, som håndhever synlighet for utkast.
- Fjerning av den ubrukte relasjonen lukker også indirekte lazy-loading gjennom `Category.getArticles()`.

### Akseptansekriterier

- Ingen kategori-service eller kategori-entitet eksponerer en ufiltrert artikkelsamling.
- Kategorisletting avvises fortsatt når artikler finnes.
- Eksport og trevisning fungerer uendret.

---

## 2. Gjør sorteringen deterministisk

### Berørte filer

- `src/main/java/io/avec/knowledgebase/data/ArticleRepository.java`
- `src/main/java/io/avec/knowledgebase/service/ArticleService.java`
- `src/test/java/io/avec/knowledgebase/service/ArticleServiceVisibilityTest.java`
- ny eller eksisterende repository-test

### Endringer

1. Endre kategorispørringer til `sortOrder ASC, id ASC`.
2. Endre spørringer for ukategoriserte artikler til `sortOrder ASC, id ASC`.
3. Oppdater alle kall i `ArticleService`.
4. Oppdater Mockito-forventninger som bruker de gamle metodenavnene.
5. Legg til en JPA-test med flere artikler som har samme `sortOrder`.

### Nye repository-metoder

```java
findByCategoryOrderBySortOrderAscIdAsc(...)
findByCategoryAndStatusOrderBySortOrderAscIdAsc(...)
findByCategoryIsNullOrderBySortOrderAscIdAsc()
findByCategoryIsNullAndStatusOrderBySortOrderAscIdAsc(...)
```

### Akseptansekriterier

- Artikler med samme `sortOrder` returneres alltid stigende etter ID.
- Samme data gir samme tre- og eksportrekkefølge etter ny spørring.
- Både admin- og brukerfiltrering beholder samme funksjonelle resultat.

---

## 3. Scope knowledge-base-CSS

### Berørte filer

- `src/main/java/io/avec/knowledgebase/view/KnowledgeBaseView.java`
- `src/main/frontend/themes/knowledge-base/views/knowledge-base-view.css`

### Endringer

1. Legg `knowledge-base-view` på rotkomponenten i viewets konstruktør:

```java
addClassName("knowledge-base-view");
```

2. Prefiks alle CSS-regler med `.knowledge-base-view`.
3. Prefiks også kombinerte selektorer, `::part`-regler og regler inne i media-queryen.
4. Behold den eksisterende globale importen i `styles.css`.
5. Kontroller at ingen generiske knowledge-base-selektorer står igjen uten root-scope.

### Eksempel

```css
.knowledge-base-view .content-editor-layout {
  /* ... */
}

.knowledge-base-view .wiki-content h2 {
  /* ... */
}

@media (max-width: 700px) {
  .knowledge-base-view .content-editor-layout {
    flex-direction: column;
  }
}
```

### Akseptansekriterier

- Knowledge-base-viewet ser ut som før.
- `.status-badge`, `.wiki-content` og lignende klasser påvirker ikke komponenter utenfor knowledge-base-viewet.
- Editor og Markdown-hjelp stables fortsatt under 700 px.

---

## 4. Styrk regresjonstestene

### Foreslåtte testfiler

- ny `src/test/java/io/avec/knowledgebase/data/ArticleRepositoryVisibilityTest.java`
- utvidet `src/test/java/io/avec/knowledgebase/service/KnowledgeBaseExportServiceTest.java`, eller en ny `KnowledgeBaseExportIntegrationTest.java`

### Repository-tester

1. `findBySlugAndStatus()` returnerer en publisert artikkel.
2. `findBySlugAndStatus()` skjuler et utkast når status er `PUBLISHED`.
3. Statusspørringen for ukategoriserte artikler returnerer bare riktig status.
4. Statusspørringen for kategoriserte artikler returnerer bare riktig status.
5. Artikler med samme `sortOrder` sorteres etter ID.
6. Testene bruker `flush()` og `clear()` slik at resultatene faktisk leses fra databasen.

### Eksporttest

1. Bruk en ekte `ArticleService`, ikke en mock av synlighetsmetodene.
2. Sett en autentisert bruker med `ROLE_USER` i `SecurityContext`.
3. Opprett både `DRAFT` og `PUBLISHED` i samme kategori.
4. Generer ZIP-filen.
5. Kontroller at den publiserte artikkelen finnes.
6. Kontroller at utkastet ikke finnes.
7. Rydd `SecurityContext` etter testen.

### Akseptansekriterier

- Synlighetsqueryene er verifisert mot JPA/H2, ikke bare Mockito.
- USER-eksport er testet gjennom den faktiske synlighetspolicyen.
- Eksporttesten feiler dersom `findVisibleByCategory()` senere begynner å returnere utkast til USER.

---

## Gjennomføringsrekkefølge

1. Fjern ufiltrert kategorilesing.
2. Innfør deterministisk artikkelsortering.
3. Legg til repository- og eksporttestene.
4. Scope CSS.
5. Kjør `mvn test`.
6. Utfør manuell kontroll av desktop og viewport under 700 px.

## Foreslåtte commits

1. `fix(kb): remove unfiltered category article access`
2. `fix(kb): stabilize article ordering`
3. `test(kb): cover repository visibility and user export`
4. `style(kb): scope knowledge base theme rules`

## Ferdigdefinisjon

Arbeidet er ferdig når:

- artikkelsamlinger ikke kan leses direkte gjennom kategori-laget
- alle kategori- og ukategoriserte artikkellister har deterministisk sekundærsortering
- knowledge-base-CSS er scopet til viewets rotklasse
- repository-tester dekker statusfiltrering og deterministisk sortering
- en USER-eksporttest bekrefter at utkast ikke inkluderes
- `mvn test` er grønn
- desktop og smal viewport er kontrollert manuelt
