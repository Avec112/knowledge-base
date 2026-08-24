package io.avec.knowledgebase.data;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest(properties = "spring.sql.init.mode=never", showSql = false)
class ArticleRepositorySearchTest {

    @Autowired
    private ArticleRepository articleRepository;

    @BeforeEach
    void setUp() {
        articleRepository.saveAllAndFlush(List.of(
            article("percent", "Progress is 100% complete", ArticleStatus.PUBLISHED),
            article("underscore", "Use snake_case", ArticleStatus.PUBLISHED),
            article("escape", "Important!", ArticleStatus.DRAFT),
            article("plain", "Progress is 1000 complete", ArticleStatus.PUBLISHED)
        ));
    }

    @Test
    void wildcardCharactersAreMatchedLiterally() {
        assertThat(articleRepository.searchByTitleOrContent("!%"))
            .extracting(Article::getSlug)
            .containsExactly("percent");
        assertThat(articleRepository.searchByTitleOrContent("!_"))
            .extracting(Article::getSlug)
            .containsExactly("underscore");
        assertThat(articleRepository.searchByTitleOrContent("!!"))
            .extracting(Article::getSlug)
            .containsExactly("escape");
    }

    @Test
    void literalSearchStillAppliesStatusFilter() {
        assertThat(articleRepository.searchByTitleOrContentAndStatus("!!", ArticleStatus.PUBLISHED))
            .isEmpty();
    }

    private Article article(String slug, String content, ArticleStatus status) {
        Article article = new Article();
        article.setTitle(slug);
        article.setSlug(slug);
        article.setContent(content);
        article.setStatus(status);
        return article;
    }
}
