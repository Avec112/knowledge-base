package io.avec.views;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.vaadin.flow.router.AfterNavigationEvent;
import com.vaadin.flow.router.Location;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.server.auth.AccessAnnotationChecker;
import com.vaadin.flow.server.menu.MenuConfiguration;
import io.avec.security.AuthenticatedUser;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/**
 * A detached window (opened via the knowledge base detach button) carries a
 * ?detached query parameter; the drawer starts collapsed there because the
 * knowledge base is the focus. Ordinary navigation never touches the drawer.
 */
class MainLayoutDetachTest {

    @Test
    void detachedLocationCollapsesTheDrawer() {
        try (MockedStatic<MenuConfiguration> menuConfiguration = mockStatic(MenuConfiguration.class)) {
            MainLayout layout = newLayout(menuConfiguration);

            assertThat(layout.isDrawerOpened()).isTrue();
            layout.afterNavigation(navigationTo(new Location("knowledge", QueryParameters.fromString("detached"))));

            assertThat(layout.isDrawerOpened()).isFalse();
        }
    }

    @Test
    void ordinaryNavigationLeavesTheDrawerAlone() {
        try (MockedStatic<MenuConfiguration> menuConfiguration = mockStatic(MenuConfiguration.class)) {
            MainLayout layout = newLayout(menuConfiguration);

            layout.afterNavigation(navigationTo(new Location("knowledge")));
            assertThat(layout.isDrawerOpened()).isTrue();

            layout.setDrawerOpened(false);
            layout.afterNavigation(navigationTo(new Location("other-stuff")));
            assertThat(layout.isDrawerOpened()).isFalse();
        }
    }

    private MainLayout newLayout(MockedStatic<MenuConfiguration> menuConfiguration) {
        menuConfiguration.when(MenuConfiguration::getMenuEntries).thenReturn(List.of());
        menuConfiguration.when(() -> MenuConfiguration.getPageHeader(any())).thenReturn(Optional.empty());
        AuthenticatedUser authenticatedUser = mock(AuthenticatedUser.class);
        when(authenticatedUser.get()).thenReturn(Optional.empty());
        return new MainLayout(authenticatedUser, mock(AccessAnnotationChecker.class));
    }

    private AfterNavigationEvent navigationTo(Location location) {
        AfterNavigationEvent event = mock(AfterNavigationEvent.class);
        when(event.getLocation()).thenReturn(location);
        return event;
    }
}
