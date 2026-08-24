package io.avec.knowledgebase.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.avec.knowledgebase.data.Article;
import io.avec.knowledgebase.data.ArticleRepository;
import io.avec.knowledgebase.data.ArticleStatus;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class ArticleServiceRobustnessTest {

    private final ArticleRepository articleRepository = mock(ArticleRepository.class);
    private final ArticleService articleService = new ArticleService(articleRepository);

    @Test
    void sequentialTitleCollisionsGetUniqueSlugs() {
        when(articleRepository.existsBySlug("same-title")).thenReturn(false, true);
        when(articleRepository.saveAndFlush(org.mockito.ArgumentMatchers.any(Article.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        Article first = article("Same title");
        Article second = article("Same title");

        assertThat(articleService.save(first).getSlug()).isEqualTo("same-title");
        assertThat(articleService.save(second).getSlug()).isEqualTo("same-title-1");
    }

    @Test
    void duplicateSlugConstraintHasUnderstandableError() {
        Article article = article("Concurrent title");
        when(articleRepository.saveAndFlush(article))
            .thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> articleService.save(article))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Article could not be saved because slug 'concurrent-title' is already in use");
    }

    @Test
    void searchEscapesLikeWildcardsAndEscapeCharacter() {
        when(articleRepository.searchByTitleOrContent("!%!_!!")).thenReturn(List.of());
        when(articleRepository.searchByTitleOrContentAndStatus("!%!_!!", ArticleStatus.PUBLISHED))
            .thenReturn(List.of());

        articleService.search(" %_! ");
        articleService.searchPublished(" %_! ");

        verify(articleRepository).searchByTitleOrContent("!%!_!!");
        verify(articleRepository).searchByTitleOrContentAndStatus("!%!_!!", ArticleStatus.PUBLISHED);
    }

    private Article article(String title) {
        Article article = new Article();
        article.setTitle(title);
        return article;
    }
}
