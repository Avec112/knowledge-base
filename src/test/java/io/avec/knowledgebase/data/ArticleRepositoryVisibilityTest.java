package io.avec.knowledgebase.data;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

@DataJpaTest(properties = "spring.sql.init.mode=never", showSql = false)
class ArticleRepositoryVisibilityTest {

    @Autowired
    private ArticleRepository articleRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Category category;

    @BeforeEach
    void setUp() {
        category = new Category();
        category.setName("Handbook");
        category.setSlug("handbook");
        category.setSortOrder(1);
        category = categoryRepository.saveAndFlush(category);
    }

    /** Forces the assertions to read from the database instead of the persistence context. */
    private void detachAll() {
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void slugLookupWithStatusReturnsPublishedArticle() {
        articleRepository.save(article("published", ArticleStatus.PUBLISHED, category, 0));
        detachAll();

        assertThat(articleRepository.findBySlugAndStatus("published", ArticleStatus.PUBLISHED))
            .get()
            .extracting(Article::getSlug)
            .isEqualTo("published");
    }

    @Test
    void slugLookupWithStatusHidesDraft() {
        articleRepository.save(article("secret", ArticleStatus.DRAFT, category, 0));
        detachAll();

        assertThat(articleRepository.findBySlugAndStatus("secret", ArticleStatus.PUBLISHED)).isEmpty();
        assertThat(articleRepository.findBySlug("secret")).isPresent();
    }

    @Test
    void uncategorizedStatusQueryReturnsOnlyMatchingStatus() {
        articleRepository.save(article("loose-draft", ArticleStatus.DRAFT, null, 0));
        articleRepository.save(article("loose-published", ArticleStatus.PUBLISHED, null, 0));
        detachAll();

        assertThat(articleRepository.findByCategoryIsNullAndStatusOrderBySortOrderAscIdAsc(ArticleStatus.PUBLISHED))
            .extracting(Article::getSlug)
            .containsExactly("loose-published");
        assertThat(articleRepository.findByCategoryIsNullOrderBySortOrderAscIdAsc())
            .extracting(Article::getSlug)
            .containsExactlyInAnyOrder("loose-draft", "loose-published");
    }

    @Test
    void categoryStatusQueryReturnsOnlyMatchingStatus() {
        articleRepository.save(article("category-draft", ArticleStatus.DRAFT, category, 0));
        articleRepository.save(article("category-published", ArticleStatus.PUBLISHED, category, 0));
        detachAll();

        Category detachedCategory = categoryRepository.findById(category.getId()).orElseThrow();

        assertThat(articleRepository
                .findByCategoryAndStatusOrderBySortOrderAscIdAsc(detachedCategory, ArticleStatus.PUBLISHED))
            .extracting(Article::getSlug)
            .containsExactly("category-published");
        assertThat(articleRepository.findByCategoryOrderBySortOrderAscIdAsc(detachedCategory))
            .extracting(Article::getSlug)
            .containsExactlyInAnyOrder("category-draft", "category-published");
    }

    @Test
    void categoryArticlesWithEqualSortOrderAreOrderedById() {
        List<Article> inserted = articleRepository.saveAllAndFlush(List.of(
            article("last", ArticleStatus.PUBLISHED, category, 2),
            article("first", ArticleStatus.PUBLISHED, category, 1),
            article("second", ArticleStatus.PUBLISHED, category, 1),
            article("third", ArticleStatus.PUBLISHED, category, 1)
        ));
        assertThat(inserted).extracting(Article::getId).doesNotContainNull();
        detachAll();

        Category detachedCategory = categoryRepository.findById(category.getId()).orElseThrow();

        assertThat(articleRepository.findByCategoryOrderBySortOrderAscIdAsc(detachedCategory))
            .extracting(Article::getSlug)
            .containsExactly("first", "second", "third", "last");
        assertThat(articleRepository
                .findByCategoryAndStatusOrderBySortOrderAscIdAsc(detachedCategory, ArticleStatus.PUBLISHED))
            .extracting(Article::getSlug)
            .containsExactly("first", "second", "third", "last");
    }

    @Test
    void uncategorizedArticlesWithEqualSortOrderAreOrderedById() {
        articleRepository.saveAllAndFlush(List.of(
            article("loose-last", ArticleStatus.PUBLISHED, null, 2),
            article("loose-first", ArticleStatus.PUBLISHED, null, 1),
            article("loose-second", ArticleStatus.PUBLISHED, null, 1)
        ));
        detachAll();

        assertThat(articleRepository.findByCategoryIsNullOrderBySortOrderAscIdAsc())
            .extracting(Article::getSlug)
            .containsExactly("loose-first", "loose-second", "loose-last");
        assertThat(articleRepository.findByCategoryIsNullAndStatusOrderBySortOrderAscIdAsc(ArticleStatus.PUBLISHED))
            .extracting(Article::getSlug)
            .containsExactly("loose-first", "loose-second", "loose-last");
    }

    private Article article(String slug, ArticleStatus status, Category category, int sortOrder) {
        Article article = new Article();
        article.setTitle(slug);
        article.setSlug(slug);
        article.setContent("Content of " + slug);
        article.setStatus(status);
        article.setCategory(category);
        article.setSortOrder(sortOrder);
        return article;
    }
}
