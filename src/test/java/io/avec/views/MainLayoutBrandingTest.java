package io.avec.views;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.server.auth.AccessAnnotationChecker;
import com.vaadin.flow.server.menu.MenuConfiguration;
import io.avec.security.AuthenticatedUser;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class MainLayoutBrandingTest {

    @Test
    void drawerHeaderUsesTheApplicationNameNotThePlaceholder() {
        AuthenticatedUser authenticatedUser = mock(AuthenticatedUser.class);
        when(authenticatedUser.get()).thenReturn(Optional.empty());
        AccessAnnotationChecker accessChecker = mock(AccessAnnotationChecker.class);

        try (MockedStatic<MenuConfiguration> menuConfiguration = mockStatic(MenuConfiguration.class)) {
            menuConfiguration.when(MenuConfiguration::getMenuEntries).thenReturn(List.of());
            MainLayout layout = new MainLayout(authenticatedUser, accessChecker);

            List<String> spanTexts = componentTree(layout)
                .filter(Span.class::isInstance)
                .map(component -> ((Span) component).getText())
                .toList();

            assertThat(spanTexts).contains("KnowledgeBase");
            assertThat(spanTexts).doesNotContain("App");
        }
    }

    private Stream<Component> componentTree(Component root) {
        return Stream.concat(Stream.of(root), root.getChildren().flatMap(this::componentTree));
    }
}
