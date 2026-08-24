package io.avec.knowledgebase.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.avec.knowledgebase.data.Article;
import io.avec.knowledgebase.data.Category;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;

class KnowledgeBaseExportServiceTest {

    private final ArticleService articleService = mock(ArticleService.class);
    private final CategoryService categoryService = mock(CategoryService.class);
    private final KnowledgeBaseExportService exportService =
        new KnowledgeBaseExportService(articleService, categoryService);

    @Test
    void exportsWelcomeNestedCategoriesAndUncategorizedArticles() throws IOException {
        Category root = category(1L, "Root");
        Category child = category(2L, "Child");
        Article rootArticle = article(10L, "root-article", "Root content");
        Article childArticle = article(11L, "child-article", "Child content");
        Article uncategorized = article(12L, "uncategorized", "Uncategorized content");

        when(categoryService.findRootCategories()).thenReturn(List.of(root));
        when(categoryService.findByParent(root)).thenReturn(List.of(child));
        when(categoryService.findByParent(child)).thenReturn(List.of());
        when(articleService.findVisibleByCategory(root)).thenReturn(List.of(rootArticle));
        when(articleService.findVisibleByCategory(child)).thenReturn(List.of(childArticle));
        when(articleService.findVisibleUncategorized()).thenReturn(List.of(uncategorized));

        Map<String, String> entries = readEntries(exportService.generateExportZipBytes());

        assertThat(entries).containsKeys(
            "welcome.md",
            "Root/",
            "Root/root-article.md",
            "Root/Child/",
            "Root/Child/child-article.md",
            "uncategorized.md");
        assertThat(entries.get("Root/Child/child-article.md")).isEqualTo("Child content");
    }

    @Test
    void resolvesCollisionsAfterSanitizingNames() throws IOException {
        Category first = category(1L, "Same/Name");
        Category second = category(2L, "Same:Name");
        Article firstArticle = article(10L, "same/name", "First");
        Article secondArticle = article(11L, "same:name", "Second");

        when(categoryService.findRootCategories()).thenReturn(List.of(first, second));
        when(categoryService.findByParent(first)).thenReturn(List.of());
        when(categoryService.findByParent(second)).thenReturn(List.of());
        when(articleService.findVisibleByCategory(first)).thenReturn(List.of(firstArticle, secondArticle));
        when(articleService.findVisibleByCategory(second)).thenReturn(List.of());
        when(articleService.findVisibleUncategorized()).thenReturn(List.of());

        Map<String, String> entries = readEntries(exportService.generateExportZipBytes());

        assertThat(entries).containsKeys(
            "Same-Name/",
            "Same-Name-2/",
            "Same-Name/same-name.md",
            "Same-Name/same-name-11.md");
    }

    @Test
    void propagatesGenerationFailure() {
        when(categoryService.findRootCategories()).thenThrow(new IllegalStateException("Database unavailable"));

        assertThatThrownBy(exportService::generateExportZipBytes)
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Database unavailable");
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

    private Category category(Long id, String name) {
        Category category = new Category();
        category.setId(id);
        category.setName(name);
        return category;
    }

    private Article article(Long id, String slug, String content) {
        Article article = new Article();
        article.setId(id);
        article.setSlug(slug);
        article.setContent(content);
        return article;
    }
}
