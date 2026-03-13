package io.avec.knowledgebase.service;

import io.avec.knowledgebase.data.Category;
import io.avec.knowledgebase.data.CategoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
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

    public Optional<Category> findByIdWithArticles(Long id) {
        return categoryRepository.findByIdWithArticles(id);
    }

    public Optional<Category> findBySlug(String slug) {
        return categoryRepository.findBySlug(slug);
    }

    @Transactional
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
    public void delete(Category category) {
        categoryRepository.delete(category);
    }

    @Transactional
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
