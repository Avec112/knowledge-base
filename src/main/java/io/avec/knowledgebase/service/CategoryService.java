package io.avec.knowledgebase.service;

import io.avec.knowledgebase.data.ArticleRepository;
import io.avec.knowledgebase.data.Category;
import io.avec.knowledgebase.data.CategoryRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ArticleRepository articleRepository;

    public CategoryService(CategoryRepository categoryRepository, ArticleRepository articleRepository) {
        this.categoryRepository = categoryRepository;
        this.articleRepository = articleRepository;
    }

    public List<Category> findAll() {
        return categoryRepository.findAll();
    }

    public List<Category> findRootCategories() {
        return categoryRepository.findByParentIsNullOrderBySortOrderAscIdAsc();
    }

    public List<Category> findRootCategoriesWithChildren() {
        return categoryRepository.findRootCategoriesWithChildren();
    }

    public List<Category> findByParent(Category parent) {
        return categoryRepository.findByParentOrderBySortOrderAscIdAsc(parent);
    }

    public Optional<Category> findById(Long id) {
        return categoryRepository.findById(id);
    }

    public Optional<Category> findBySlug(String slug) {
        return categoryRepository.findBySlug(slug);
    }

    /**
     * Looks up a category by name (case-insensitive). Category names are enforced globally
     * unique by {@link #save(Category)}, so this alone is enough for a find-or-create by
     * name lookup - callers that also need to disambiguate by parent (e.g. import) can rely
     * on that uniqueness instead of a compound key.
     */
    public Optional<Category> findByName(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return categoryRepository.findByNameIgnoreCase(name.trim());
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public Category save(Category category) {
        String normalizedName = category.getName() == null ? "" : category.getName().trim();
        if (normalizedName.isEmpty()) {
            throw new IllegalArgumentException("Category name is required");
        }

        Optional<Category> existingByName = categoryRepository.findByNameIgnoreCase(normalizedName);
        if (existingByName.isPresent()) {
            boolean sameEntity = category.getId() != null && category.getId().equals(existingByName.get().getId());
            if (!sameEntity) {
                throw new IllegalArgumentException("Category name already exists");
            }
        }

        category.setName(normalizedName);

        // Generate slug if not set
        if (category.getSlug() == null || category.getSlug().isEmpty()) {
            category.setSlug(generateUniqueSlug(category.getName(), null));
        }
        return categoryRepository.save(category);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(Category category) {
        if (articleRepository.existsByCategory(category)) {
            throw new IllegalStateException("Category has articles and cannot be deleted");
        }
        if (categoryRepository.existsByParent(category)) {
            throw new IllegalStateException("Category has subcategories and cannot be deleted");
        }
        categoryRepository.delete(category);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void reorderRootCategories(List<Category> orderedRootCategories) {
        for (int i = 0; i < orderedRootCategories.size(); i++) {
            Category category = orderedRootCategories.get(i);
            category.setSortOrder(i + 1);
        }
        categoryRepository.saveAll(orderedRootCategories);
    }

    public String generateUniqueSlug(String name, Long excludeId) {
        String baseSlug = generateSlugFromName(name);
        String slug = baseSlug;
        int counter = 1;

        while (categoryRepository.existsBySlug(slug)) {
            Optional<Category> existing = categoryRepository.findBySlug(slug);
            if (existing.isPresent() && excludeId != null && existing.get().getId().equals(excludeId)) {
                break;
            }
            slug = baseSlug + "-" + counter++;
        }

        return slug;
    }

    private String generateSlugFromName(String name) {
        if (name == null || name.isEmpty()) {
            return "untitled-category";
        }

        // Normalize and remove accents
        String normalized = Normalizer.normalize(name, Normalizer.Form.NFD);
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

        return slug.isEmpty() ? "untitled-category" : slug;
    }
}
