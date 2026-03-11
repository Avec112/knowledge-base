package io.avec.knowledgebase.service;

import io.avec.knowledgebase.data.Article;
import io.avec.knowledgebase.data.ArticleRepository;
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

    public List<Article> findAll() {
        return articleRepository.findAllByOrderByUpdatedAtDesc();
    }

    public List<Article> findPublished() {
        return articleRepository.findByPublishedTrueOrderByUpdatedAtDesc();
    }

    public Optional<Article> findById(Long id) {
        return articleRepository.findById(id);
    }

    public Optional<Article> findBySlug(String slug) {
        return articleRepository.findBySlug(slug);
    }

    @Transactional
    public Article save(Article article) {
        // Generate slug if not set or if title changed
        if (article.getSlug() == null || article.getSlug().isEmpty()) {
            article.setSlug(generateUniqueSlug(article.getTitle(), null));
        }
        return articleRepository.save(article);
    }

    @Transactional
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
