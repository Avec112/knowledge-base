# Knowledge Base – migrering til et annet Vaadin-prosjekt

Dette dokumentet beskriver steg for steg hvordan `io.avec.knowledgebase`-modulen
flyttes inn i et annet Vaadin/Spring Boot-prosjekt. Se også
`knowledgebase-hardening-plan.md` (forenkling før flytting) og
`knowledgebase-database-migration.md` (databasekontrakt).

## Forutsetninger i målprosjektet

Modulen er skrevet mot følgende stack, og målprosjektet bør ligge på samme nivå:

- **Java 21** og **Spring Boot 4.x** (Jakarta-navnerom)
- **Vaadin 25** – koden bruker API-er som ikke finnes i eldre versjoner:
  - `com.vaadin.flow.component.markdown.Markdown` (Markdown-komponenten)
  - `com.vaadin.flow.server.streams.DownloadHandler` / `DownloadResponse` (eksport)
  - `MenuConfiguration` / `@Menu` for automatisk meny
  - `@StyleSheet(Lumo.STYLESHEET)`-mønsteret for tema
- **Spring Data JPA** med en relasjonsdatabase (H2 i dev, se databasekontrakten for prod)
- **Spring Security** med rollene `USER` og `ADMIN`, og metodesikkerhet aktivert

## Hva modulen består av

| Del | Filer |
|---|---|
| Java-pakken | `src/main/java/io/avec/knowledgebase/` – `data/` (Article, ArticleStatus, Category, ArticleRepository, CategoryRepository), `service/` (ArticleService, CategoryService, KnowledgeBaseExportService), `view/` (KnowledgeBaseView) |
| Ressurser | `src/main/resources/knowledge/welcome-to-knowledge.md` (velkomstinnhold, lastes fra classpath av både view og eksporttjeneste) |
| CSS | `src/main/resources/META-INF/resources/views/knowledge-base-view.css` |
| Tester | `src/test/java/io/avec/knowledgebase/` (repository-, service-, sikkerhets- og eksporttester) |
| Demodata (valgfritt) | `src/main/resources/demo/data.sql` + `application-demo.properties` (H2-spesifikk, kun `demo`-profil) |

### Koblinger ut av modulen (må håndteres i målprosjektet)

Modulen importerer disse klassene fra resten av appen:

- `io.avec.data.AbstractEntity` – felles JPA-superklasse (id + `@Version`)
- `io.avec.data.User` – `Article.createdBy` / `Article.updatedBy` er `@ManyToOne` mot denne
- `io.avec.data.Role` – rollene `USER`/`ADMIN`
- `io.avec.security.AuthenticatedUser` – gir innlogget bruker til view/tjenester
- `io.avec.views.MainLayout` – `@Route(value = "knowledge", layout = MainLayout.class)`

## Migreringsprosess

### Steg 1 – Kopier Java-pakken

Kopier hele `src/main/java/io/avec/knowledgebase/` til målprosjektet og bytt
`io.avec.knowledgebase` til ønsket pakkenavn (søk/erstatt i package- og
import-linjer).

### Steg 2 – Koble mot målprosjektets bruker- og sikkerhetsklasser

Dette er den eneste reelle tilpasningsjobben:

1. **`AbstractEntity`**: Har målprosjektet en tilsvarende basisklasse, la
   `Article`/`Category` arve fra den. Hvis ikke, kopier `io.avec.data.AbstractEntity`.
2. **`User`**: Pek `Article.createdBy`/`updatedBy` mot målprosjektets brukerentitet.
   Feltene brukes kun til visning av navn og eierskap – alternativt kan de endres
   til en `String username` hvis målprosjektet ikke har en JPA-brukerentitet.
3. **`AuthenticatedUser`**: Kopier klassen, eller erstatt kallene med målprosjektets
   måte å hente innlogget bruker på. Viewet bruker den til å avgjøre om
   admin-funksjoner skal vises.
4. **Roller**: Tjenestelaget er låst med `@PreAuthorize("hasRole('ADMIN')")` på alle
   muterende metoder. Målprosjektet må ha en `ADMIN`-rolle (eller uttrykkene må
   endres til målprosjektets rollenavn), og sikkerhetskonfigurasjonen må ha
   `@EnableMethodSecurity` – uten den er skrivetilgangen åpen for alle innloggede.
5. **`@PermitAll`** på `KnowledgeBaseView` betyr «alle innloggede». Juster ved behov.

### Steg 3 – Koble viewet inn i målprosjektets layout og meny

1. Endre `@Route(value = "knowledge", layout = MainLayout.class)` til å peke på
   målprosjektets hovedlayout.
2. `@Menu(order = 0, icon = LineAwesomeIconUrl.GRADUATION_CAP_SOLID)` krever
   `org.parttio:line-awesome` (2.1.0). Bruker ikke målprosjektet Line Awesome,
   bytt til et annet ikon eller fjern icon-attributtet.
3. Viewet navigerer internt til `knowledge` og `knowledge/welcome-to-knowledge`
   (slug som valgfri ruteparameter). Beholdes rutenavnet, trengs ingen endringer.

### Steg 4 – Kopier ressurser og CSS

1. Kopier `src/main/resources/knowledge/welcome-to-knowledge.md` til samme
   classpath-sti i målprosjektet (stien `knowledge/welcome-to-knowledge.md` er
   hardkodet i `KnowledgeBaseView` og `KnowledgeBaseExportService`).
2. Kopier `META-INF/resources/views/knowledge-base-view.css`. Reglene er scopet
   til viewets egne klassenavn (`kb-*`) og kolliderer ikke med annen styling.
3. Last CSS-en «the Vaadin 25 way»: i dette prosjektet gjøres det via
   `@StyleSheet("styles.css")` på `Application` (AppShellConfigurator), der
   `styles.css` har `@import url('./views/knowledge-base-view.css');`.
   Gjenta mønsteret i målprosjektet – enten ved å importere filen i en
   eksisterende global stylesheet, eller ved å legge til en egen
   `@StyleSheet("views/knowledge-base-view.css")` på app-shellen.

### Steg 5 – Avhengigheter i pom.xml

Sjekk at målprosjektet har (via `vaadin-bom`):

- `com.vaadin:vaadin` eller `vaadin-core` (Markdown-komponenten er del av core)
- `spring-boot-starter-data-jpa`, `spring-boot-starter-security`,
  `spring-boot-starter-validation`
- `org.parttio:line-awesome:2.1.0` (kun hvis meny-ikonet beholdes, jf. steg 3)
- En JDBC-driver (H2 i dev)

### Steg 6 – Konfigurasjon

I målprosjektets `application.properties`:

1. `vaadin.allowed-packages` – legg til den nye pakke-roten (f.eks.
   `com.vaadin,org.vaadin,<din.pakke>`), ellers skannes ikke viewet.
2. `spring.sql.init.mode=never` som default – databasen eies av deployment;
   demodata skal aldri lastes implisitt (se databasekontrakten).

### Steg 7 – Database

1. Entitetene bruker tabellene `kb_article` og `kb_category` – prefikset gjør
   navnekollisjon usannsynlig, men verifiser mot målprosjektets skjema.
2. `Article.content` er Markdown-tekst (LOB) – sjekk kolonnetype mot måldatabasen.
3. Følg `knowledgebase-database-migration.md` for skjema i produksjon
   (Flyway/Liquibase i målprosjektet, ikke `ddl-auto`).
4. Valgfritt: kopier `demo/data.sql` + `application-demo.properties` for demodata.
   Merk at `data.sql` også seeder brukere (`application_user`/`user_roles`) –
   den delen må skrives om eller fjernes mot målprosjektets brukertabeller,
   og fila er H2-spesifikk (binærliteraler).

### Steg 8 – Kopier testene

Kopier `src/test/java/io/avec/knowledgebase/` og oppdater pakkenavn og
referanser til bruker-/sikkerhetsklassene (samme mapping som i steg 2).
Testene dekker synlighetsregler, hierarki, metodesikkerhet og eksport – de er
den raskeste måten å verifisere at koblingen mot målprosjektet er riktig.

### Steg 9 – Bygg og verifiser

1. `mvn vaadin:build-frontend` (eller vanlig `mvn package`) – bekrefter at
   frontend-bundelen bygger med Markdown-komponenten.
2. `mvn test` – alle knowledgebase-testene grønne.
3. Manuell røyktest i browser:
   - `/knowledge` viser velkomstsiden med tre-navigasjon
   - Artikkelvisning rendrer Markdown
   - Som `ADMIN`: opprett/rediger/slett kategori og artikkel, dra-og-slipp
     sortering, split-view-editor med forhåndsvisning
   - Som vanlig `USER`: ingen admin-knapper, og muterende kall avvises
   - Eksport-knappen laster ned arkivet

## Sjekkliste

- [ ] Java-pakken kopiert og ompakket
- [ ] `AbstractEntity`, `User`, `Role`, `AuthenticatedUser` koblet mot målprosjektet
- [ ] `@EnableMethodSecurity` + `ADMIN`-rolle på plass
- [ ] `@Route`-layout og `@Menu`-ikon tilpasset
- [ ] `knowledge/welcome-to-knowledge.md` på classpath
- [ ] `knowledge-base-view.css` kopiert og lastet via app-shellens stylesheet
- [ ] Avhengigheter i pom.xml verifisert
- [ ] `vaadin.allowed-packages` oppdatert, `spring.sql.init.mode=never`
- [ ] Databaseskjema (`kb_article`, `kb_category`) håndtert via migreringsverktøy
- [ ] Tester kopiert og grønne
- [ ] Manuell røyktest gjennomført
