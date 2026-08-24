package io.avec.knowledgebase.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.avec.knowledgebase.data.Article;
import io.avec.knowledgebase.data.ArticleRepository;
import io.avec.knowledgebase.data.ArticleStatus;
import io.avec.knowledgebase.data.Category;
import io.avec.knowledgebase.data.CategoryRepository;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

/**
 * Exercises {@link KnowledgeBaseImportService} against a real (in-memory) database, the way
 * {@link KnowledgeBaseExportIntegrationTest} exercises the export side, so assertions reflect
 * real find-or-create and slug-conflict behaviour rather than mock echoes.
 */
@DataJpaTest(properties = "spring.sql.init.mode=never", showSql = false)
class KnowledgeBaseImportServiceTest {

    @Autowired
    private ArticleRepository articleRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private TestEntityManager entityManager;

    private ArticleService articleService;
    private CategoryService categoryService;
    private KnowledgeBaseImportService importService;

    @BeforeEach
    void setUp() {
        articleService = new ArticleService(articleRepository);
        categoryService = new CategoryService(categoryRepository, articleRepository);
        importService = new KnowledgeBaseImportService(articleService, categoryService);
    }

    @Test
    void roundTripReproducesStructureInEmptyBase() throws IOException {
        byte[] zip = exportedZip();

        ImportResult result = importService.importZip(zip);

        assertThat(result.importedCount()).isEqualTo(3);
        assertThat(result.skippedSlugs()).isEmpty();
        assertThat(result.newCategoriesCount()).isEqualTo(2);

        entityManager.flush();
        entityManager.clear();

        Category root = categoryRepository.findByNameIgnoreCase("Root").orElseThrow();
        assertThat(root.getParent()).isNull();
        Category child = categoryRepository.findByNameIgnoreCase("Child").orElseThrow();
        assertThat(child.getParent().getId()).isEqualTo(root.getId());

        Article rootArticle = articleRepository.findBySlug("root-article").orElseThrow();
        assertThat(rootArticle.getCategory().getId()).isEqualTo(root.getId());
        assertThat(rootArticle.getContent()).isEqualTo("Root content");
        assertThat(rootArticle.getStatus()).isEqualTo(ArticleStatus.DRAFT);

        Article childArticle = articleRepository.findBySlug("child-article").orElseThrow();
        assertThat(childArticle.getCategory().getId()).isEqualTo(child.getId());

        Article uncategorized = articleRepository.findBySlug("uncategorized").orElseThrow();
        assertThat(uncategorized.getCategory()).isNull();

        // welcome.md at the root must never become an article
        assertThat(articleRepository.findBySlug("welcome")).isEmpty();
    }

    @Test
    void existingSlugIsSkippedAndReportedWithoutTouchingTheExistingArticle() throws IOException {
        Article existing = new Article();
        existing.setTitle("Original title");
        existing.setSlug("existing-note");
        existing.setContent("Original content");
        existing.setStatus(ArticleStatus.PUBLISHED);
        articleRepository.saveAndFlush(existing);

        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("existing-note.md", "# Attempted overwrite\n\nShould not apply");
        entries.put("new-note.md", "# Brand new\n\nContent");
        byte[] zip = zipOf(entries);

        ImportResult result = importService.importZip(zip);

        assertThat(result.importedCount()).isEqualTo(1);
        assertThat(result.skippedSlugs()).containsExactly("existing-note");
        assertThat(result.newCategoriesCount()).isZero();

        entityManager.flush();
        entityManager.clear();

        Article stillOriginal = articleRepository.findBySlug("existing-note").orElseThrow();
        assertThat(stillOriginal.getTitle()).isEqualTo("Original title");
        assertThat(stillOriginal.getContent()).isEqualTo("Original content");
        assertThat(stillOriginal.getStatus()).isEqualTo(ArticleStatus.PUBLISHED);

        Article created = articleRepository.findBySlug("new-note").orElseThrow();
        assertThat(created.getTitle()).isEqualTo("Brand new");
        assertThat(created.getStatus()).isEqualTo(ArticleStatus.DRAFT);
    }

    @Test
    void titleComesFromFirstHeadingElseFallsBackToTheFilename() throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("with-heading.md", "Some intro\n\n# The Real Title\n\nBody text");
        entries.put("without-heading.md", "Just plain body text, no heading at all.");
        byte[] zip = zipOf(entries);

        importService.importZip(zip);
        entityManager.flush();
        entityManager.clear();

        assertThat(articleRepository.findBySlug("with-heading").orElseThrow().getTitle())
            .isEqualTo("The Real Title");
        assertThat(articleRepository.findBySlug("without-heading").orElseThrow().getTitle())
            .isEqualTo("without-heading");
    }

    @Test
    void nestedCategoriesAreRecreated() throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("Level1/", null);
        entries.put("Level1/Level2/", null);
        entries.put("Level1/Level2/Level3/", null);
        entries.put("Level1/Level2/Level3/deep-article.md", "# Deep article\n\nContent");
        byte[] zip = zipOf(entries);

        ImportResult result = importService.importZip(zip);

        assertThat(result.newCategoriesCount()).isEqualTo(3);
        assertThat(result.importedCount()).isEqualTo(1);

        entityManager.flush();
        entityManager.clear();

        Category level1 = categoryRepository.findByNameIgnoreCase("Level1").orElseThrow();
        Category level2 = categoryRepository.findByNameIgnoreCase("Level2").orElseThrow();
        Category level3 = categoryRepository.findByNameIgnoreCase("Level3").orElseThrow();
        assertThat(level1.getParent()).isNull();
        assertThat(level2.getParent().getId()).isEqualTo(level1.getId());
        assertThat(level3.getParent().getId()).isEqualTo(level2.getId());

        Article deep = articleRepository.findBySlug("deep-article").orElseThrow();
        assertThat(deep.getCategory().getId()).isEqualTo(level3.getId());
    }

    @Test
    void corruptZipIsRejectedWithoutSideEffects() {
        byte[] garbage = "this is not a zip file".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> importService.importZip(garbage))
            .isInstanceOf(IllegalArgumentException.class);

        assertThat(articleRepository.count()).isZero();
        assertThat(categoryRepository.count()).isZero();
    }

    @Test
    void oversizedEntryIsRejectedWithoutSideEffects() throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("Category/", null);
        entries.put("Category/small.md", "# Small\n\nfine");
        entries.put("too-big.md", "a".repeat(20_001));
        byte[] zip = zipOf(entries);

        assertThatThrownBy(() -> importService.importZip(zip))
            .isInstanceOf(IllegalArgumentException.class);

        assertThat(articleRepository.count()).isZero();
        assertThat(categoryRepository.count()).isZero();
    }

    /**
     * Builds a zip using the real export service driven by mocked data - i.e. the same shape
     * {@link KnowledgeBaseExportServiceTest} verifies - so the round-trip test exercises the
     * actual export format rather than a hand-rolled approximation of it.
     */
    private byte[] exportedZip() throws IOException {
        ArticleService mockArticles = mock(ArticleService.class);
        CategoryService mockCategories = mock(CategoryService.class);
        KnowledgeBaseExportService exportService = new KnowledgeBaseExportService(mockArticles, mockCategories);

        Category root = new Category();
        root.setId(1L);
        root.setName("Root");
        Category child = new Category();
        child.setId(2L);
        child.setName("Child");

        Article rootArticle = new Article();
        rootArticle.setId(10L);
        rootArticle.setSlug("root-article");
        rootArticle.setContent("Root content");
        Article childArticle = new Article();
        childArticle.setId(11L);
        childArticle.setSlug("child-article");
        childArticle.setContent("Child content");
        Article uncategorized = new Article();
        uncategorized.setId(12L);
        uncategorized.setSlug("uncategorized");
        uncategorized.setContent("Uncategorized content");

        when(mockCategories.findRootCategories()).thenReturn(List.of(root));
        when(mockCategories.findByParent(root)).thenReturn(List.of(child));
        when(mockCategories.findByParent(child)).thenReturn(List.of());
        when(mockArticles.findVisibleByCategory(root)).thenReturn(List.of(rootArticle));
        when(mockArticles.findVisibleByCategory(child)).thenReturn(List.of(childArticle));
        when(mockArticles.findVisibleUncategorized()).thenReturn(List.of(uncategorized));

        return exportService.generateExportZipBytes();
    }

    /**
     * Builds a zip from a map of path to content; a null value writes a directory entry.
     */
    private byte[] zipOf(Map<String, String> entries) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                if (entry.getValue() != null) {
                    zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                }
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }
}
