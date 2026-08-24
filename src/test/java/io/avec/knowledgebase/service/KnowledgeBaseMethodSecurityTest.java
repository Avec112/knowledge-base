package io.avec.knowledgebase.service;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.avec.knowledgebase.data.Article;
import io.avec.knowledgebase.data.ArticleRepository;
import io.avec.knowledgebase.data.Category;
import io.avec.knowledgebase.data.CategoryRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(KnowledgeBaseMethodSecurityTest.TestConfiguration.class)
class KnowledgeBaseMethodSecurityTest {

    @Autowired
    private ArticleService articleService;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private ArticleRepository articleRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Test
    @WithMockUser(roles = "USER")
    void userCannotMutateKnowledgeBaseContent() {
        Article article = article();
        Category category = category();

        assertAll(
            () -> assertThrows(AccessDeniedException.class, () -> articleService.save(article)),
            () -> assertThrows(AccessDeniedException.class, () -> articleService.delete(article)),
            () -> assertThrows(AccessDeniedException.class, () -> categoryService.save(category)),
            () -> assertThrows(AccessDeniedException.class, () -> categoryService.delete(category)),
            () -> assertThrows(AccessDeniedException.class,
                () -> categoryService.reorderRootCategories(List.of(category)))
        );
    }

    @Test
    @WithMockUser(roles = "USER")
    void userCannotBypassVisibilityWithDraftCapableReads() {
        assertAll(
            () -> assertThrows(AccessDeniedException.class, () -> articleService.findBySlug("draft")),
            () -> assertThrows(AccessDeniedException.class, articleService::findUncategorized)
        );
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanMutateKnowledgeBaseContent() {
        Article article = article();
        Category category = category();

        articleService.save(article);
        articleService.delete(article);
        categoryService.save(category);
        categoryService.delete(category);
        categoryService.reorderRootCategories(List.of(category));

        verify(articleRepository).saveAndFlush(article);
        verify(articleRepository).delete(article);
        verify(categoryRepository).save(category);
        verify(categoryRepository).delete(category);
        verify(categoryRepository).saveAll(List.of(category));
    }

    private Article article() {
        Article article = new Article();
        article.setTitle("Article");
        return article;
    }

    private Category category() {
        Category category = new Category();
        category.setName("Category");
        return category;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class TestConfiguration {

        @Bean
        ArticleRepository articleRepository() {
            return mock(ArticleRepository.class);
        }

        @Bean
        CategoryRepository categoryRepository() {
            return mock(CategoryRepository.class);
        }

        @Bean
        ArticleService articleService(ArticleRepository articleRepository) {
            return new ArticleService(articleRepository);
        }

        @Bean
        CategoryService categoryService(CategoryRepository categoryRepository,
                                        ArticleRepository articleRepository) {
            return new CategoryService(categoryRepository, articleRepository);
        }
    }
}
