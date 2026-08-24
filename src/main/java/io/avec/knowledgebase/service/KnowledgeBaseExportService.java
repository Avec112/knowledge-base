package io.avec.knowledgebase.service;

import io.avec.knowledgebase.data.Article;
import io.avec.knowledgebase.data.Category;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KnowledgeBaseExportService {

    private static final String WELCOME_RESOURCE = "knowledge/welcome-to-knowledge.md";

    private final ArticleService articleService;
    private final CategoryService categoryService;

    public KnowledgeBaseExportService(ArticleService articleService, CategoryService categoryService) {
        this.articleService = articleService;
        this.categoryService = categoryService;
    }

    @Transactional(readOnly = true)
    public byte[] generateExportZipBytes() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Set<String> usedPaths = new HashSet<>();

        try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            addWelcomeEntry(zip, usedPaths);

            for (Category category : categoryService.findRootCategories()) {
                addCategoryEntries(zip, category, "", usedPaths);
            }

            addArticles(zip, articleService.findVisibleUncategorized(), "", usedPaths);
        }

        return output.toByteArray();
    }

    private void addWelcomeEntry(ZipOutputStream zip, Set<String> usedPaths) throws IOException {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(WELCOME_RESOURCE)) {
            if (input == null) {
                throw new FileNotFoundException("Missing export resource: " + WELCOME_RESOURCE);
            }
            writeZipEntry(zip, "welcome.md", input.readAllBytes(), usedPaths);
        }
    }

    private void addCategoryEntries(ZipOutputStream zip, Category category, String parentPath,
                                    Set<String> usedPaths) throws IOException {
        String segment = sanitizePathSegment(category.getName());
        if (segment.isBlank()) {
            segment = "category-" + category.getId();
        }

        String categoryPath = reserveDirectoryPath(parentPath, segment, category.getId(), usedPaths);
        ensureDirectoryEntry(zip, categoryPath);
        addArticles(zip, articleService.findVisibleByCategory(category), categoryPath, usedPaths);

        for (Category child : categoryService.findByParent(category)) {
            addCategoryEntries(zip, child, categoryPath, usedPaths);
        }
    }

    private void addArticles(ZipOutputStream zip, List<Article> articles, String parentPath,
                             Set<String> usedPaths) throws IOException {
        for (Article article : articles) {
            String baseName = sanitizePathSegment(article.getSlug());
            if (baseName.isBlank()) {
                baseName = sanitizePathSegment(article.getTitle());
            }
            if (baseName.isBlank()) {
                baseName = "article-" + article.getId();
            }

            String path = reserveFilePath(parentPath, baseName, article.getId(), usedPaths);
            writeZipEntry(zip, path,
                (article.getContent() == null ? "" : article.getContent()).getBytes(StandardCharsets.UTF_8),
                usedPaths);
        }
    }

    private String reserveDirectoryPath(String parentPath, String segment, Long id, Set<String> usedPaths) {
        String candidate = parentPath + segment + "/";
        if (usedPaths.add(candidate)) {
            return candidate;
        }

        String suffix = id == null ? "2" : id.toString();
        candidate = parentPath + segment + "-" + suffix + "/";
        int counter = 2;
        while (!usedPaths.add(candidate)) {
            candidate = parentPath + segment + "-" + suffix + "-" + counter++ + "/";
        }
        return candidate;
    }

    private String reserveFilePath(String parentPath, String baseName, Long id, Set<String> usedPaths) {
        String candidate = parentPath + baseName + ".md";
        if (!usedPaths.contains(candidate)) {
            return candidate;
        }

        String suffix = id == null ? "2" : id.toString();
        candidate = parentPath + baseName + "-" + suffix + ".md";
        int counter = 2;
        while (usedPaths.contains(candidate)) {
            candidate = parentPath + baseName + "-" + suffix + "-" + counter++ + ".md";
        }
        return candidate;
    }

    private void writeZipEntry(ZipOutputStream zip, String path, byte[] content,
                               Set<String> usedPaths) throws IOException {
        if (!usedPaths.add(path)) {
            throw new IOException("Duplicate ZIP entry: " + path);
        }
        zip.putNextEntry(new ZipEntry(path));
        zip.write(content);
        zip.closeEntry();
    }

    private void ensureDirectoryEntry(ZipOutputStream zip, String path) throws IOException {
        zip.putNextEntry(new ZipEntry(path));
        zip.closeEntry();
    }

    private String sanitizePathSegment(String input) {
        if (input == null) {
            return "";
        }
        return input
            .trim()
            .replaceAll("[\\p{Cntrl}\\\\/:*?\"<>|]", "-")
            .replaceAll("\\s+", " ")
            .replaceAll("^[. ]+|[. ]+$", "");
    }
}
