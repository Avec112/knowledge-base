package io.avec.knowledgebase.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.avec.knowledgebase.data.Article;
import io.avec.knowledgebase.data.ArticleRepository;
import io.avec.knowledgebase.data.ArticleStatus;
import io.avec.knowledgebase.data.Category;
import org.junit.jupiter.api.Test;

class ArticleServiceDuplicateTest {

    private final ArticleRepository articleRepository = mock(ArticleRepository.class);
    private final ArticleService articleService = new ArticleService(articleRepository);

    @Test
    void duplicateCopiesTitleContentAndCategoryWithDraftStatus() {
        Category category = new Category();
        Article original = new Article();
        original.setTitle("Original title");
        original.setContent("Original content");
        original.setCategory(category);
        original.setStatus(ArticleStatus.PUBLISHED);
        original.setSlug("original-title");
        when(articleRepository.existsBySlug("original-title-kopi")).thenReturn(false);

        Article copy = articleService.duplicate(original);

        assertThat(copy.getTitle()).isEqualTo("Original title (kopi)");
        assertThat(copy.getContent()).isEqualTo("Original content");
        assertThat(copy.getCategory()).isEqualTo(category);
        assertThat(copy.getStatus()).isEqualTo(ArticleStatus.DRAFT);
        assertThat(copy.getSlug()).isEqualTo("original-title-kopi");
    }

    @Test
    void duplicateGeneratesUniqueSlugWhenBaseSlugIsTaken() {
        Article original = new Article();
        original.setTitle("Original title");
        original.setSlug("original-title");
        when(articleRepository.existsBySlug("original-title-kopi")).thenReturn(true);

        Article copy = articleService.duplicate(original);

        assertThat(copy.getSlug()).isEqualTo("original-title-kopi-1");
    }
}
