# KnowledgeBase

A modern wiki/knowledge base application built with Vaadin Flow and Spring Boot. This application provides a clean, markdown-based documentation system with role-based access control.

## Features

- **Markdown Support**: Write and preview articles using Markdown syntax with live preview
- **Role-Based Access**: Admin users can create, edit, and delete articles; regular users have read-only access
- **SEO-Friendly URLs**: Article URLs use human-readable slugs instead of IDs
- **Dark Mode UI**: Modern dark theme with optimized readability for wiki content
- **Live Preview**: Toggle between edit and preview mode while writing
- **Article Management**: Full CRUD operations for managing documentation
- **Default Welcome Page**: New users are greeted with a comprehensive Markdown guide

## Running the application

Open the project in an IDE. You can download the [IntelliJ community edition](https://www.jetbrains.com/idea/download) if you do not have a suitable IDE already.
Once opened in the IDE, locate the `Application` class and run the main method using "Debug".

For more information on installing in various IDEs, see [how to import Vaadin projects to different IDEs](https://vaadin.com/docs/latest/getting-started/import).

If you install the Vaadin plugin for IntelliJ, you should instead launch the `Application` class using "Debug using HotswapAgent" to see updates in the Java code immediately reflected in the browser.

## Deploying to Production

The project is a standard Maven project. To create a production build, call 

```
./mvnw clean package -Pproduction
```

If you have Maven globally installed, you can replace `./mvnw` with `mvn`.

This will build a JAR file with all the dependencies and front-end resources,ready to be run. The file can be found in the `target` folder after the build completes.
You then launch the application using 
```
java -jar target/knowledge-base-1.0-SNAPSHOT.jar
```

## Technology Stack

- **Vaadin Flow 25.0.7**: Modern Java web framework with Material Design components
- **Spring Boot 4.0.3**: Application framework with dependency injection
- **Spring Data JPA**: Database persistence layer
- **Spring Security**: Role-based authentication and authorization
- **H2 Database**: In-memory database (easily replaceable with production database)
- **Java 21**: Modern Java features and performance

## Project Structure

### Backend (Java)
- `io.avec.knowledgebase.data.Article`: Entity representing wiki articles with slug generation
- `io.avec.knowledgebase.service.ArticleService`: Business logic for article management
- `io.avec.knowledgebase.view.KnowledgeBaseView`: Main view with article list and Markdown rendering
- `io.avec.security`: Spring Security configuration with role-based access

### Frontend (CSS)
- `src/main/frontend/themes/knowledge-base/views/knowledge-base-view.css`: Main styling for wiki content and UI components
- `src/main/resources/knowledge/welcome-to-knowledge.md`: Default welcome content with Markdown guide

## Usage

### Default Users
The application comes with two default users:
- **Admin**: `admin@avec.io` / `admin` (can create, edit, and delete articles)
- **User**: `user@avec.io` / `user` (read-only access)

### Creating Articles
1. Log in as an admin user
2. Click the "Create" button in the toolbar
3. Enter a title and content using Markdown syntax
4. Click "Preview" to see how it will look
5. Click "Save" to publish the article

### Editing Articles
1. Navigate to an article
2. Click the "Edit" button (admin only)
3. Modify the content
4. Use "Preview" to check your changes
5. Click "Save" to update or "Cancel" to discard changes

## Customization

This application is designed as a reusable module that can be integrated into larger Vaadin applications. The KnowledgeBase module is self-contained and can be easily adapted to different use cases.

## License

This project is a pilot/demonstration application.
