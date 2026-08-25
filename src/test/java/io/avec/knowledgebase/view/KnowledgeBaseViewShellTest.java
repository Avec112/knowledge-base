package io.avec.knowledgebase.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.AbstractStreamResource;
import com.vaadin.flow.server.StreamResourceRegistry;
import io.avec.data.Role;
import io.avec.data.User;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * The knowledge base renders as a standalone shell: no surrounding AppLayout,
 * its own brand header and a user row with sign-out in the sidebar.
 */
class KnowledgeBaseViewShellTest {

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
        admin.setName("Alice Admin");
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
    void knowledgeRouteOptsOutOfTheApplicationShell() {
        Route route = KnowledgeBaseView.class.getAnnotation(Route.class);

        assertThat(route.autoLayout()).isFalse();
    }

    @Test
    void sidebarBrandShowsTheApplicationName() {
        Span brandTitle = field("brandTitle");

        assertThat(brandTitle.getText()).isEqualTo("KnowledgeBase");
    }

    @Test
    void sidebarUserRowShowsTheSignedInUserName() {
        Span userNameLabel = field("userNameLabel");

        assertThat(userNameLabel.getText()).isEqualTo("Alice Admin");
    }

    @Test
    void signOutButtonLogsTheUserOut() {
        Button signOutButton = field("signOutButton");

        signOutButton.click();

        verify(authenticatedUser).logout();
    }

    @Test
    void sidebarLinksBackToTheRestOfTheApplication() {
        Anchor otherStuffLink = field("otherStuffLink");

        assertThat(otherStuffLink.getHref()).isEqualTo("other-stuff");
    }

    @Test
    void statusFieldShowsReadableLabelsNotEnumNames() {
        ComboBox<ArticleStatus> statusField = field("statusField");

        assertThat(statusField.getItemLabelGenerator().apply(ArticleStatus.DRAFT)).isEqualTo("Draft");
        assertThat(statusField.getItemLabelGenerator().apply(ArticleStatus.PUBLISHED)).isEqualTo("Published");
    }

    @SuppressWarnings("unchecked")
    private <T> T field(String name) {
        return (T) ReflectionTestUtils.getField(view, name);
    }
}
