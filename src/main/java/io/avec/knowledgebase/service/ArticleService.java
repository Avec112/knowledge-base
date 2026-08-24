package io.avec.knowledgebase.service;

import io.avec.knowledgebase.data.Article;
import io.avec.knowledgebase.data.ArticleRepository;
import io.avec.knowledgebase.data.ArticleStatus;
import io.avec.knowledgebase.data.Category;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class ArticleService {

    private final ArticleRepository articleRepository;

    public ArticleService(ArticleRepository articleRepository) {
        this.articleRepository = articleRepository;
    }

    @PreAuthorize("hasRole('ADMIN')")
    public List<Article> findAll() {
        return articleRepository.findAllByOrderByUpdatedAtDesc();
    }

    public List<Article> findPublished() {
        return articleRepository.findByStatusOrderByUpdatedAtDesc(ArticleStatus.PUBLISHED);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public List<Article> findByCategory(Category category) {
        return articleRepository.findByCategoryOrderBySortOrderAscIdAsc(category);
    }

    public List<Article> findByCategoryAndPublished(Category category) {
        return articleRepository.findByCategoryAndStatusOrderBySortOrderAscIdAsc(category, ArticleStatus.PUBLISHED);
    }

    public List<Article> findVisibleByCategory(Category category) {
        return canViewDrafts()
            ? articleRepository.findByCategoryOrderBySortOrderAscIdAsc(category)
            : articleRepository.findByCategoryAndStatusOrderBySortOrderAscIdAsc(category, ArticleStatus.PUBLISHED);
    }

    public List<Article> findRecentVisible() {
        return canViewDrafts()
            ? articleRepository.findTop10ByOrderByUpdatedAtDesc()
            : articleRepository.findTop10ByStatusOrderByUpdatedAtDesc(ArticleStatus.PUBLISHED);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public List<Article> findUncategorized() {
        return articleRepository.findByCategoryIsNullOrderBySortOrderAscIdAsc();
    }

    public List<Article> findVisibleUncategorized() {
        return canViewDrafts()
            ? articleRepository.findByCategoryIsNullOrderBySortOrderAscIdAsc()
            : articleRepository.findByCategoryIsNullAndStatusOrderBySortOrderAscIdAsc(ArticleStatus.PUBLISHED);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public List<Article> search(String query) {
        if (query == null || query.isBlank()) return List.of();
        return articleRepository.searchByTitleOrContent(escapeLike(query.trim()));
    }

    public List<Article> searchPublished(String query) {
        if (query == null || query.isBlank()) return List.of();
        return articleRepository.searchByTitleOrContentAndStatus(escapeLike(query.trim()), ArticleStatus.PUBLISHED);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public Optional<Article> findById(Long id) {
        return articleRepository.findById(id);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public Optional<Article> findBySlug(String slug) {
        return articleRepository.findBySlug(slug);
    }

    public Optional<Article> findVisibleBySlug(String slug) {
        return canViewDrafts()
            ? articleRepository.findBySlug(slug)
            : articleRepository.findBySlugAndStatus(slug, ArticleStatus.PUBLISHED);
    }

    private boolean canViewDrafts() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
            .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public Article save(Article article) {
        if (article.getStatus() == null) {
            article.setStatus(ArticleStatus.DRAFT);
        }
        // Generate slug if not set or if title changed
        if (article.getSlug() == null || article.getSlug().isEmpty()) {
            article.setSlug(generateUniqueSlug(article.getTitle(), null));
        }
        try {
            return articleRepository.saveAndFlush(article);
        } catch (DataIntegrityViolationException exception) {
            throw new IllegalStateException(
                "Article could not be saved because slug '" + article.getSlug() + "' is already in use",
                exception);
        }
    }

    @PreAuthorize("hasRole('ADMIN')")
    public Article duplicate(Article article) {
        Article copy = new Article();
        copy.setTitle(article.getTitle() + " (kopi)");
        copy.setContent(article.getContent());
        copy.setCategory(article.getCategory());
        copy.setStatus(ArticleStatus.DRAFT);
        copy.setSlug(generateUniqueSlug(copy.getTitle(), null));
        return copy;
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(Article article) {
        articleRepository.delete(article);
    }

    public String generateUniqueSlug(String title, Long excludeId) {
        String baseSlug = generateSlugFromTitle(title);
        String slug = baseSlug;
        int counter = 1;

        while (articleRepository.existsBySlug(slug)) {
            // If the existing slug belongs to the article being updated, keep it
            Optional<Article> existing = articleRepository.findBySlug(slug);
            if (existing.isPresent() && excludeId != null && existing.get().getId().equals(excludeId)) {
                break;
            }
            slug = baseSlug + "-" + counter++;
        }

        return slug;
    }

    private String escapeLike(String query) {
        return query
            .replace("!", "!!")
            .replace("%", "!%")
            .replace("_", "!_");
    }

    private String generateSlugFromTitle(String title) {
        if (title == null || title.isEmpty()) {
            return "untitled";
        }

        // Normalize and remove accents
        String normalized = Normalizer.normalize(title, Normalizer.Form.NFD);
        normalized = normalized.replaceAll("\\p{M}", "");

        // Convert to lowercase and replace spaces/special chars with hyphens
        String slug = normalized.toLowerCase(Locale.ENGLISH)
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");

        // Limit length
        if (slug.length() > 100) {
            slug = slug.substring(0, 100).replaceAll("-[^-]*$", "");
        }

        return slug.isEmpty() ? "untitled" : slug;
    }
}
