package io.avec.views;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.router.AfterNavigationEvent;
import com.vaadin.flow.server.AbstractStreamResource;
import com.vaadin.flow.server.StreamResourceRegistry;
import com.vaadin.flow.server.auth.AccessAnnotationChecker;
import com.vaadin.flow.server.menu.MenuConfiguration;
import io.avec.data.Role;
import io.avec.data.User;
import io.avec.knowledgebase.service.ArticleService;
import io.avec.knowledgebase.service.CategoryService;
import io.avec.knowledgebase.service.KnowledgeBaseExportService;
import io.avec.knowledgebase.service.KnowledgeBaseImportService;
import io.avec.knowledgebase.view.KnowledgeBaseView;
import io.avec.security.AuthenticatedUser;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/**
 * The knowledge base keeps the AppLayout shell but starts with the drawer
 * collapsed, so the view gets the width while the hamburger stays the way
 * in and out. Other views keep the drawer open.
 */
class MainLayoutDrawerTest {

    @Test
    void drawerIsClosedOnTheKnowledgeBaseAndOpenElsewhere() {
        AuthenticatedUser authenticatedUser = mock(AuthenticatedUser.class);
        User admin = new User();
        admin.setName("Alice Admin");
        admin.setRoles(Set.of(Role.ADMIN));
        when(authenticatedUser.get()).thenReturn(Optional.of(admin));

        ArticleService articleService = mock(ArticleService.class);
        CategoryService categoryService = mock(CategoryService.class);
        when(categoryService.findRootCategories()).thenReturn(List.of());
        when(articleService.findVisibleUncategorized()).thenReturn(List.of());

        try (MockedStatic<MenuConfiguration> menuConfiguration = mockStatic(MenuConfiguration.class);
             MockedStatic<StreamResourceRegistry> streamResourceRegistry = mockStatic(StreamResourceRegistry.class)) {
            menuConfiguration.when(MenuConfiguration::getMenuEntries).thenReturn(List.of());
            menuConfiguration.when(() -> MenuConfiguration.getPageHeader(any())).thenReturn(Optional.empty());
            streamResourceRegistry.when(() -> StreamResourceRegistry.getURI(any(AbstractStreamResource.class)))
                .thenReturn(URI.create("VAADIN/dynamic/resource/export"));

            MainLayout layout = new MainLayout(authenticatedUser, mock(AccessAnnotationChecker.class));
            KnowledgeBaseView knowledgeBase = new KnowledgeBaseView(articleService, categoryService,
                mock(KnowledgeBaseExportService.class), mock(KnowledgeBaseImportService.class), authenticatedUser);

            layout.setContent(knowledgeBase);
            layout.afterNavigation(mock(AfterNavigationEvent.class));
            assertThat(layout.isDrawerOpened()).isFalse();

            layout.setContent(new Div());
            layout.afterNavigation(mock(AfterNavigationEvent.class));
            assertThat(layout.isDrawerOpened()).isTrue();
        }
    }
}
