package io.avec.knowledgebase.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.avec.knowledgebase.data.Article;
import io.avec.knowledgebase.data.ArticleRepository;
import io.avec.knowledgebase.data.ArticleStatus;
import io.avec.knowledgebase.data.Category;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class ArticleServiceVisibilityTest {

    private final ArticleRepository articleRepository = mock(ArticleRepository.class);
    private final ArticleService articleService = new ArticleService(articleRepository);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void userLooksUpOnlyPublishedArticleBySlug() {
        Article published = article("published", ArticleStatus.PUBLISHED);
        authenticate("ROLE_USER");
        when(articleRepository.findBySlugAndStatus("published", ArticleStatus.PUBLISHED))
            .thenReturn(Optional.of(published));
        when(articleRepository.findBySlugAndStatus("draft", ArticleStatus.PUBLISHED))
            .thenReturn(Optional.empty());

        assertThat(articleService.findVisibleBySlug("published")).contains(published);
        assertThat(articleService.findVisibleBySlug("draft")).isEmpty();
    }

    @Test
    void adminLooksUpArticleBySlugRegardlessOfStatus() {
        Article draft = article("draft", ArticleStatus.DRAFT);
        authenticate("ROLE_ADMIN");
        when(articleRepository.findBySlug("draft")).thenReturn(Optional.of(draft));

        assertThat(articleService.findVisibleBySlug("draft")).contains(draft);
        verify(articleRepository).findBySlug("draft");
    }

    @Test
    void userGetsOnlyPublishedUncategorizedArticles() {
        Article published = article("published", ArticleStatus.PUBLISHED);
        authenticate("ROLE_USER");
        when(articleRepository.findByCategoryIsNullAndStatusOrderBySortOrderAscIdAsc(ArticleStatus.PUBLISHED))
            .thenReturn(List.of(published));

        assertThat(articleService.findVisibleUncategorized()).containsExactly(published);
    }

    @Test
    void adminGetsAllUncategorizedArticles() {
        Article draft = article("draft", ArticleStatus.DRAFT);
        Article published = article("published", ArticleStatus.PUBLISHED);
        authenticate("ROLE_ADMIN");
        when(articleRepository.findByCategoryIsNullOrderBySortOrderAscIdAsc()).thenReturn(List.of(draft, published));

        assertThat(articleService.findVisibleUncategorized()).containsExactly(draft, published);
    }

    @Test
    void userGetsOnlyPublishedArticlesInCategory() {
        Category category = new Category();
        Article published = article("published", ArticleStatus.PUBLISHED);
        authenticate("ROLE_USER");
        when(articleRepository.findByCategoryAndStatusOrderBySortOrderAscIdAsc(category, ArticleStatus.PUBLISHED))
            .thenReturn(List.of(published));

        assertThat(articleService.findVisibleByCategory(category)).containsExactly(published);
    }

    @Test
    void adminGetsAllArticlesInCategory() {
        Category category = new Category();
        Article draft = article("draft", ArticleStatus.DRAFT);
        Article published = article("published", ArticleStatus.PUBLISHED);
        authenticate("ROLE_ADMIN");
        when(articleRepository.findByCategoryOrderBySortOrderAscIdAsc(category)).thenReturn(List.of(draft, published));

        assertThat(articleService.findVisibleByCategory(category)).containsExactly(draft, published);
    }

    private void authenticate(String authority) {
        SecurityContextHolder.getContext().setAuthentication(
            new TestingAuthenticationToken("user", "password", authority));
    }

    private Article article(String slug, ArticleStatus status) {
        Article article = new Article();
        article.setSlug(slug);
        article.setStatus(status);
        return article;
    }
}
