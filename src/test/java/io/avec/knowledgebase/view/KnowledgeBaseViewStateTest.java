package io.avec.knowledgebase.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.markdown.Markdown;
import com.vaadin.flow.component.treegrid.TreeGrid;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.server.AbstractStreamResource;
import com.vaadin.flow.server.StreamResourceRegistry;
import io.avec.data.Role;
import io.avec.data.User;
import io.avec.knowledgebase.data.Article;
import io.avec.knowledgebase.data.ArticleStatus;
import io.avec.knowledgebase.service.ArticleService;
import io.avec.knowledgebase.service.CategoryService;
import io.avec.knowledgebase.service.KnowledgeBaseExportService;
import io.avec.knowledgebase.service.KnowledgeBaseImportService;
import io.avec.security.AuthenticatedUser;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;

class KnowledgeBaseViewStateTest {

    private final ArticleService articleService = mock(ArticleService.class);
    private final CategoryService categoryService = mock(CategoryService.class);
    private final KnowledgeBaseExportService exportService = mock(KnowledgeBaseExportService.class);
    private final KnowledgeBaseImportService importService = mock(KnowledgeBaseImportService.class);
    private final AuthenticatedUser authenticatedUser = mock(AuthenticatedUser.class);
    private MockedStatic<StreamResourceRegistry> streamResourceRegistry;
    private KnowledgeBaseView view;

    @BeforeEach
    void setUp() {
        streamResourceRegistry = mockStatic(StreamResourceRegistry.class);
        streamResourceRegistry.when(() -> StreamResourceRegistry.getURI(any(AbstractStreamResource.class)))
            .thenReturn(URI.create("VAADIN/dynamic/resource/export"));

        User admin = new User();
        admin.setRoles(Set.of(Role.ADMIN));
        when(authenticatedUser.get()).thenReturn(Optional.of(admin));
        when(categoryService.findRootCategories()).thenReturn(List.of());
        when(articleService.findVisibleUncategorized()).thenReturn(List.of());
        when(articleService.findVisibleBySlug("markdown-syntax")).thenReturn(Optional.empty());

        view = new KnowledgeBaseView(articleService, categoryService, exportService, importService, authenticatedUser);
    }

    @AfterEach
    void closeStaticMock() {
        streamResourceRegistry.close();
    }

    @Test
    void editingDisablesArticleNavigationControls() {
        ReflectionTestUtils.invokeMethod(view, "createNewArticle");

        TreeGrid<?> articleTree = field("articleTree");
        ComboBox<?> menuSearch = field("menuSearch");
        Button toggleCategoriesButton = field("toggleCategoriesButton");
        Button deleteButton = field("deleteButton");

        assertThat(articleTree.isEnabled()).isFalse();
        assertThat(menuSearch.isEnabled()).isFalse();
        assertThat(toggleCategoriesButton.isEnabled()).isFalse();
        assertThat(deleteButton.isVisible()).isFalse();
    }

    @Test
    void routeChangeEndsEditingAndDisplaysDestinationArticle() {
        Article destination = article("destination", "Destination", "Destination content");
        when(articleService.findVisibleBySlug("destination")).thenReturn(Optional.of(destination));
        ReflectionTestUtils.invokeMethod(view, "createNewArticle");

        view.setParameter(mock(BeforeEvent.class), "destination");

        TreeGrid<?> articleTree = field("articleTree");
        ComboBox<?> menuSearch = field("menuSearch");
        H2 titleDisplay = field("titleDisplay");
        Markdown markdownPreview = field("markdownPreview");

        assertThat(articleTree.isEnabled()).isTrue();
        assertThat(menuSearch.isEnabled()).isTrue();
        assertThat(titleDisplay.getText()).isEqualTo("Destination");
        assertThat(markdownPreview.getContent()).isEqualTo("Destination content");
    }

    @Test
    void welcomeRouteClearsPreviouslyDisplayedArticle() {
        Article previous = article("previous", "Previous", "Old content that must disappear");
        when(articleService.findVisibleBySlug("previous")).thenReturn(Optional.of(previous));
        view.setParameter(mock(BeforeEvent.class), "previous");

        view.setParameter(mock(BeforeEvent.class), "welcome-to-knowledge");

        H2 titleDisplay = field("titleDisplay");
        Markdown markdownPreview = field("markdownPreview");
        assertThat(titleDisplay.getText()).isEqualTo("Welcome to KnowledgeBase");
        assertThat(markdownPreview.getContent()).doesNotContain("Old content that must disappear");
        assertThat((Object) field("currentArticle")).isNull();
    }

    private Article article(String slug, String title, String content) {
        Article article = new Article();
        article.setSlug(slug);
        article.setTitle(title);
        article.setContent(content);
        article.setStatus(ArticleStatus.PUBLISHED);
        return article;
    }

    @SuppressWarnings("unchecked")
    private <T> T field(String name) {
        return (T) ReflectionTestUtils.getField(view, name);
    }
}
