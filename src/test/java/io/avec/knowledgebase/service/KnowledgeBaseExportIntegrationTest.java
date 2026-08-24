package io.avec.knowledgebase.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.avec.knowledgebase.data.Article;
import io.avec.knowledgebase.data.ArticleRepository;
import io.avec.knowledgebase.data.ArticleStatus;
import io.avec.knowledgebase.data.Category;
import io.avec.knowledgebase.data.CategoryRepository;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Exercises the export through the real visibility policy in {@link ArticleService} instead of mocking it,
 * so the test fails if drafts ever start leaking into a non-admin export.
 */
@DataJpaTest(properties = "spring.sql.init.mode=never", showSql = false)
class KnowledgeBaseExportIntegrationTest {

    @Autowired
    private ArticleRepository articleRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private TestEntityManager entityManager;

    private KnowledgeBaseExportService exportService;

    @BeforeEach
    void setUp() {
        ArticleService articleService = new ArticleService(articleRepository);
        CategoryService categoryService = new CategoryService(categoryRepository, articleRepository);
        exportService = new KnowledgeBaseExportService(articleService, categoryService);

        Category handbook = new Category();
        handbook.setName("Handbook");
        handbook.setSlug("handbook");
        handbook.setSortOrder(1);
        handbook = categoryRepository.saveAndFlush(handbook);

        articleRepository.saveAllAndFlush(List.of(
            article("public-note", ArticleStatus.PUBLISHED, handbook, "Published content"),
            article("secret-note", ArticleStatus.DRAFT, handbook, "Draft content"),
            article("loose-public", ArticleStatus.PUBLISHED, null, "Loose published content"),
            article("loose-secret", ArticleStatus.DRAFT, null, "Loose draft content")
        ));

        entityManager.flush();
        entityManager.clear();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void userExportOmitsDrafts() throws IOException {
        authenticate("ROLE_USER");

        Map<String, String> entries = readEntries(exportService.generateExportZipBytes());

        assertThat(entries).containsKeys("welcome.md", "Handbook/", "Handbook/public-note.md", "loose-public.md");
        assertThat(entries.get("Handbook/public-note.md")).isEqualTo("Published content");
        assertThat(entries).doesNotContainKeys("Handbook/secret-note.md", "loose-secret.md");
    }

    @Test
    void adminExportIncludesDrafts() throws IOException {
        authenticate("ROLE_ADMIN");

        Map<String, String> entries = readEntries(exportService.generateExportZipBytes());

        assertThat(entries).containsKeys(
            "Handbook/public-note.md",
            "Handbook/secret-note.md",
            "loose-public.md",
            "loose-secret.md");
        assertThat(entries.get("Handbook/secret-note.md")).isEqualTo("Draft content");
    }

    private void authenticate(String authority) {
        SecurityContextHolder.getContext().setAuthentication(
            new TestingAuthenticationToken("user", "password", authority));
    }

    private Map<String, String> readEntries(byte[] zipBytes) throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipBytes), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries.put(entry.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return entries;
    }

    private Article article(String slug, ArticleStatus status, Category category, String content) {
        Article article = new Article();
        article.setTitle(slug);
        article.setSlug(slug);
        article.setContent(content);
        article.setStatus(status);
        article.setCategory(category);
        return article;
    }
}
