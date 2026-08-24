package io.avec.knowledgebase.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.avec.knowledgebase.data.ArticleRepository;
import io.avec.knowledgebase.data.Category;
import io.avec.knowledgebase.data.CategoryRepository;
import org.junit.jupiter.api.Test;

class CategoryServiceHierarchyTest {

    private final CategoryRepository categoryRepository = mock(CategoryRepository.class);
    private final ArticleRepository articleRepository = mock(ArticleRepository.class);
    private final CategoryService categoryService = new CategoryService(categoryRepository, articleRepository);

    @Test
    void savePreservesParent() {
        Category parent = category("Parent");
        Category child = category("Updated child");
        child.setParent(parent);
        when(categoryRepository.save(child)).thenReturn(child);

        Category saved = categoryService.save(child);

        assertThat(saved.getParent()).isSameAs(parent);
    }

    @Test
    void categoryWithArticlesCannotBeDeleted() {
        Category category = category("Category");
        when(articleRepository.existsByCategory(category)).thenReturn(true);

        assertThatThrownBy(() -> categoryService.delete(category))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Category has articles and cannot be deleted");
        verify(categoryRepository, never()).delete(category);
    }

    @Test
    void categoryWithChildrenCannotBeDeleted() {
        Category category = category("Category");
        when(categoryRepository.existsByParent(category)).thenReturn(true);

        assertThatThrownBy(() -> categoryService.delete(category))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Category has subcategories and cannot be deleted");
        verify(categoryRepository, never()).delete(category);
    }

    @Test
    void emptyLeafCategoryCanBeDeleted() {
        Category category = category("Category");

        categoryService.delete(category);

        verify(categoryRepository).delete(category);
    }

    private Category category(String name) {
        Category category = new Category();
        category.setName(name);
        return category;
    }
}
