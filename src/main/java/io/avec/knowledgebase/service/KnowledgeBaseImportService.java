package io.avec.knowledgebase.service;

import io.avec.knowledgebase.data.Article;
import io.avec.knowledgebase.data.ArticleStatus;
import io.avec.knowledgebase.data.Category;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Imports a zip produced by {@link KnowledgeBaseExportService} back into the knowledge base:
 * folders become categories (found or created by name), and {@code .md} files become draft
 * articles (slug = filename, title = first {@code # } heading or the filename).
 *
 * <p>Every entry is read and validated - entry count, per-entry size, and the article content
 * length cap that matches {@link Article}'s {@code @Size} - before any category or article is
 * written, so a corrupt or oversized zip is rejected without leaving partial data behind.
 * Articles whose slug already exists are left untouched and reported as skipped.
 */
@Service
public class KnowledgeBaseImportService {

    private static final int MAX_ENTRIES = 5_000;
    private static final int MAX_CONTENT_CHARS = 20_000; // matches Article.content @Size(max = 20000)
    private static final int MAX_ENTRY_BYTES = 100_000; // generous UTF-8 headroom over MAX_CONTENT_CHARS
    private static final String MARKDOWN_EXTENSION = ".md";
    private static final String WELCOME_ENTRY = "welcome.md";
    private static final Pattern HEADING_PATTERN = Pattern.compile("(?m)^#[ \\t]+(.+)$");

    private final ArticleService articleService;
    private final CategoryService categoryService;

    public KnowledgeBaseImportService(ArticleService articleService, CategoryService categoryService) {
        this.articleService = articleService;
        this.categoryService = categoryService;
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public ImportResult importZip(byte[] zipBytes) {
        List<ZipRecord> records = readZip(zipBytes);
        return applyImport(records);
    }

    private List<ZipRecord> readZip(byte[] zipBytes) {
        // ZipInputStream silently yields zero entries for non-zip data instead of throwing
        // (it only validates local file headers as it encounters them), so check the
        // "PK" magic number up front to reject garbage input as corrupt.
        if (zipBytes == null || zipBytes.length < 4 || zipBytes[0] != 'P' || zipBytes[1] != 'K') {
            throw new IllegalArgumentException("File is not a valid zip archive");
        }

        List<ZipRecord> records = new ArrayList<>();
        int entryCount = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipBytes), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entryCount++;
                if (entryCount > MAX_ENTRIES) {
                    throw new IllegalArgumentException("Zip file has too many entries (max " + MAX_ENTRIES + ")");
                }

                String path = normalizeEntryName(entry.getName());
                if (entry.isDirectory()) {
                    records.add(new ZipRecord(path, true, null));
                } else {
                    String content = new String(readBounded(zip, MAX_ENTRY_BYTES), StandardCharsets.UTF_8);
                    if (path.endsWith(MARKDOWN_EXTENSION) && content.length() > MAX_CONTENT_CHARS) {
                        throw new IllegalArgumentException(
                            "Entry '" + path + "' exceeds the maximum content length of "
                                + MAX_CONTENT_CHARS + " characters");
                    }
                    records.add(new ZipRecord(path, false, content));
                }
                zip.closeEntry();
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read zip file: " + e.getMessage(), e);
        }
        return records;
    }

    private String normalizeEntryName(String rawName) {
        if (rawName == null) {
            throw new IllegalArgumentException("Zip file contains an entry with no name");
        }
        String name = rawName.replace('\\', '/');
        while (name.startsWith("/")) {
            name = name.substring(1);
        }
        if (name.isBlank()) {
            throw new IllegalArgumentException("Zip file contains an entry with an empty path");
        }
        for (String segment : name.split("/")) {
            if (segment.equals("..")) {
                throw new IllegalArgumentException("Zip file contains an unsafe entry path: " + rawName);
            }
        }
        return name;
    }

    private byte[] readBounded(InputStream in, int maxBytes) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int total = 0;
        int read;
        while ((read = in.read(chunk)) != -1) {
            total += read;
            if (total > maxBytes) {
                throw new IllegalArgumentException("Zip entry exceeds the maximum allowed size");
            }
            buffer.write(chunk, 0, read);
        }
        return buffer.toByteArray();
    }

    private ImportResult applyImport(List<ZipRecord> records) {
        Map<String, Category> categoriesByPath = new LinkedHashMap<>();
        int newCategories = 0;
        int imported = 0;
        List<String> skipped = new ArrayList<>();

        // Process directory entries first (shallowest first) so empty categories are recreated
        // even though they carry no articles of their own.
        List<ZipRecord> directories = records.stream()
            .filter(ZipRecord::directory)
            .sorted(Comparator.comparingInt(record -> depth(record.path())))
            .toList();
        for (ZipRecord directory : directories) {
            newCategories += resolveCategoryPath(directory.path(), categoriesByPath).created();
        }

        for (ZipRecord file : records) {
            if (file.directory()) {
                continue;
            }
            String path = file.path();
            if (!path.endsWith(MARKDOWN_EXTENSION)) {
                continue; // unknown file types are ignored
            }

            int lastSlash = path.lastIndexOf('/');
            String parentPath = lastSlash >= 0 ? path.substring(0, lastSlash + 1) : "";
            String fileName = lastSlash >= 0 ? path.substring(lastSlash + 1) : path;

            if (parentPath.isEmpty() && WELCOME_ENTRY.equals(fileName)) {
                continue; // welcome.md at the root is a static resource, not an article
            }

            String slug = fileName.substring(0, fileName.length() - MARKDOWN_EXTENSION.length());
            if (slug.isBlank()) {
                continue;
            }

            if (articleService.findBySlug(slug).isPresent()) {
                skipped.add(slug);
                continue;
            }

            Category category = null;
            if (!parentPath.isEmpty()) {
                CategoryResolution resolution = resolveCategoryPath(parentPath, categoriesByPath);
                newCategories += resolution.created();
                category = resolution.category();
            }

            String content = file.content();
            Article article = new Article();
            article.setSlug(slug);
            article.setTitle(deriveTitle(content, slug));
            article.setContent(content);
            article.setStatus(ArticleStatus.DRAFT);
            article.setCategory(category);
            articleService.save(article);
            imported++;
        }

        return new ImportResult(imported, skipped, newCategories);
    }

    private CategoryResolution resolveCategoryPath(String path, Map<String, Category> cache) {
        int created = 0;
        Category parent = null;
        StringBuilder currentPath = new StringBuilder();
        for (String segment : path.split("/")) {
            if (segment.isBlank()) {
                continue;
            }
            currentPath.append(segment).append('/');
            String key = currentPath.toString();

            Category category = cache.get(key);
            if (category == null) {
                Optional<Category> existing = categoryService.findByName(segment);
                if (existing.isPresent()) {
                    category = existing.get();
                } else {
                    Category newCategory = new Category();
                    newCategory.setName(segment);
                    newCategory.setParent(parent);
                    category = categoryService.save(newCategory);
                    created++;
                }
                cache.put(key, category);
            }
            parent = category;
        }
        return new CategoryResolution(parent, created);
    }

    private String deriveTitle(String content, String fallback) {
        if (content != null) {
            Matcher matcher = HEADING_PATTERN.matcher(content);
            if (matcher.find()) {
                String heading = matcher.group(1).trim();
                if (!heading.isEmpty()) {
                    return heading;
                }
            }
        }
        return fallback;
    }

    private int depth(String path) {
        int depth = 0;
        for (int i = 0; i < path.length(); i++) {
            if (path.charAt(i) == '/') {
                depth++;
            }
        }
        return depth;
    }

    private record ZipRecord(String path, boolean directory, String content) {
    }

    private record CategoryResolution(Category category, int created) {
    }
}
