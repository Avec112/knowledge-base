package io.avec.knowledgebase.view;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.*;
import io.avec.data.Role;
import io.avec.knowledgebase.data.Article;
import io.avec.knowledgebase.service.ArticleService;
import io.avec.security.AuthenticatedUser;
import io.avec.views.MainLayout;
import jakarta.annotation.security.PermitAll;
import org.vaadin.lineawesome.LineAwesomeIconUrl;
import com.vaadin.flow.component.markdown.Markdown;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import java.util.Optional;

@PageTitle("KnowledgeBase")
@Route(value = "knowledge", layout = MainLayout.class)
@Menu(order = 0, icon = LineAwesomeIconUrl.GRADUATION_CAP_SOLID)
@PermitAll
public class KnowledgeBaseView extends VerticalLayout implements HasUrlParameter<String> {

    private final ArticleService articleService;
    private final AuthenticatedUser authenticatedUser;

    private final VerticalLayout articleList = new VerticalLayout();
    private final TextField titleField = new TextField("Title");
    private final TextArea contentArea = new TextArea("Content (Markdown)");
    private final Markdown markdownPreview = new Markdown("");
    private final H2 titleDisplay = new H2();

    private Div selectedArticleDiv;

    private final Button createButton = new Button("Create");
    private final Button editButton = new Button("Edit");
    private final Button previewButton = new Button("Preview");
    private final Button saveButton = new Button("Save");
    private final Button cancelButton = new Button("Cancel");
    private final Button deleteButton = new Button("Delete");

    private Article currentArticle;
    private boolean editMode = false;
    private boolean previewMode = false;
    private boolean isAdmin = false;

    public KnowledgeBaseView(ArticleService articleService, AuthenticatedUser authenticatedUser) {
        this.articleService = articleService;
        this.authenticatedUser = authenticatedUser;

        checkAdminRole();

        setSizeFull();
        setPadding(false);
        setSpacing(false);

        if(isAdmin) {
            HorizontalLayout buttonBarLayout = createButtonBarLayout();
            add(buttonBarLayout);
        }
        HorizontalLayout mainLayout = createMainLayout();
        add(mainLayout);

        refreshArticleList();
    }

    @Override
    public void setParameter(BeforeEvent event, @OptionalParameter String slug) {
        if (slug != null && !slug.isEmpty()) {

            // Check if it's the welcome article
            if ("welcome-to-knowledge".equals(slug)) {
                showDefaultWelcome();
                highlightSelectedArticle(null);
            } else {
                // Load article by slug
                articleService.findBySlug(slug).ifPresentOrElse(
                    article -> {
                        currentArticle = article;
                        highlightSelectedArticle(article);
                        updateUI();
                    },
                    () -> {
                        Notification.show("Article not found: " + slug);
                        event.rerouteToError(NotFoundException.class);
                    }
                );
            }
        } else {
            // No slug provided - show default welcome content
            showDefaultWelcome();
            highlightSelectedArticle(null);
        }
    }

    private void showDefaultWelcome() {
        currentArticle = null;
        titleDisplay.setText("Welcome to KnowledgeBase");

        try {
            InputStream is = getClass().getClassLoader().getResourceAsStream("knowledge/welcome-to-knowledge.md");
            if (is != null) {
                String content = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))
                    .lines()
                    .collect(Collectors.joining("\n"));
                markdownPreview.setContent(content);
                markdownPreview.setVisible(true);
            }
        } catch (Exception e) {
            markdownPreview.setContent("# Welcome to KnowledgeBase\n\nSelect an article from the list to get started.");
            markdownPreview.setVisible(true);
        }

        updateUI();
    }

    private void checkAdminRole() {
        authenticatedUser.get().ifPresent(user -> {
            isAdmin = user.getRoles().contains(Role.ADMIN);
        });
    }

    private HorizontalLayout createMainLayout() {
        HorizontalLayout layout = new HorizontalLayout();
        layout.setSizeFull();
        layout.setPadding(false);
        layout.setSpacing(false);

        VerticalLayout rightPanel = createRightPanel();

        articleList.setSpacing(false);
        articleList.setPadding(false);
        articleList.setWidth("300px");
        articleList.setHeightFull();
        articleList.addClassName("kb-sidebar");

        rightPanel.setSizeFull();
        rightPanel.addClassName("kb-content-area");

        layout.add(articleList, rightPanel);

        return layout;
    }

    private void refreshArticleList() {
        articleList.removeAll();

        // Add welcome item first
        Div welcomeDiv = createArticleListItem("Velkommen", "welcome-to-knowledge", null);
        welcomeDiv.getElement().setAttribute("data-welcome", "true");
        articleList.add(welcomeDiv);

        // Add regular articles
        List<Article> articles = isAdmin ? articleService.findAll() : articleService.findPublished();
        for (Article article : articles) {
            Div articleDiv = createArticleListItem(article.getTitle(), article.getSlug(), article);
            articleList.add(articleDiv);
        }
    }

    private Div createArticleListItem(String title, String slug, Article article) {
        Div div = new Div();
        div.setText(title);
        div.setWidthFull();
        div.getStyle()
            .set("padding", "var(--lumo-space-s) var(--lumo-space-m)")
            .set("cursor", "pointer")
            .set("border-bottom", "1px solid var(--lumo-contrast-10pct)")
            .set("transition", "background-color 0.2s")
            .set("box-sizing", "border-box");

        div.getElement().addEventListener("mouseenter", e -> {
            if (selectedArticleDiv != div) {
                div.getStyle().set("background-color", "var(--lumo-contrast-5pct)");
            }
        });

        div.getElement().addEventListener("mouseleave", e -> {
            if (selectedArticleDiv != div) {
                div.getStyle().set("background-color", "transparent");
            }
        });

        div.addClickListener(e -> {
            if (article != null) {
                showArticle(article);
            } else {
                getUI().ifPresent(ui -> ui.navigate("knowledge/" + slug));
            }
        });

        return div;
    }

    private void highlightSelectedArticle(Article article) {
        // Reset previous selection
        if (selectedArticleDiv != null) {
            selectedArticleDiv.getStyle()
                .set("background-color", "transparent")
                .set("font-weight", "normal");
        }

        // Find and highlight new selection
        articleList.getChildren().forEach(component -> {
            if (component instanceof Div div) {
                boolean isMatch;
                if (article == null) {
                    // Highlight welcome item
                    isMatch = div.getElement().hasAttribute("data-welcome");
                } else {
                    // Highlight article by comparing title
                    isMatch = div.getText().equals(article.getTitle());
                }

                if (isMatch) {
                    div.getStyle()
                        .set("background-color", "var(--lumo-primary-color-10pct)")
                        .set("font-weight", "600")
                        ;
                    selectedArticleDiv = div;
                }
            }
        });
    }

    private HorizontalLayout createButtonBarLayout() {
        // Button bar
        HorizontalLayout buttonBar = new HorizontalLayout();
        buttonBar.setWidthFull();
        buttonBar.setJustifyContentMode(JustifyContentMode.END);
        buttonBar.setPadding(true);
        buttonBar.setSpacing(true);
        buttonBar.addClassName("kb-button-bar");
        buttonBar.getStyle()
            .set("padding", "2px var(--lumo-space-m)")
        ;

//        createButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        createButton.addClickListener(e -> createNewArticle());
        createButton.setVisible(false);

//        editButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        editButton.addClickListener(e -> enableEditMode());
        editButton.setVisible(false);

        previewButton.addClickListener(e -> togglePreview());
        previewButton.setVisible(false);

//        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        saveButton.addClickListener(e -> saveArticle());
        saveButton.setVisible(false);

        cancelButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        cancelButton.addClickListener(e -> cancelEdit());
        cancelButton.setVisible(false);

        deleteButton.addThemeVariants(ButtonVariant.LUMO_ERROR);
        deleteButton.addClickListener(e -> deleteArticle());
        deleteButton.setVisible(false);

        buttonBar.add(createButton, editButton, previewButton, saveButton, cancelButton, deleteButton);
        return buttonBar;
    }

    private VerticalLayout createRightPanel() {
        VerticalLayout panel = new VerticalLayout();
        panel.setId("");
        panel.setSizeFull();
        panel.setPadding(true);
        panel.setSpacing(true);
//        panel.setMargin(false);



        // Title display (read mode)
        titleDisplay.getStyle().set("margin-top", "0");

        // Title field (edit mode)
        titleField.setWidthFull();
        titleField.setVisible(false);

        // Content area (edit mode) - monospace font for Markdown editing
        contentArea.setWidthFull();
        contentArea.setHeight("500px");
        contentArea.setVisible(false);
        contentArea.getStyle()
            .set("font-family", "monospace")
            .set("font-size", "0.9em")
            .set("line-height", "1.5");

        // Markdown preview (read mode)
        markdownPreview.setWidthFull();
        markdownPreview.addClassName("wiki-content");

        panel.add(titleDisplay, titleField, contentArea, markdownPreview);
//        panel.setFlexGrow(1, markdownPreview);

        return panel;
    }

    private void createNewArticle() {
        currentArticle = new Article();
        authenticatedUser.get().ifPresent(user -> currentArticle.setCreatedBy(user));
        currentArticle.setPublished(true);
        enableEditMode();
        titleField.clear();
        contentArea.clear();
        markdownPreview.setContent("");
        titleDisplay.setText("");
    }

    private void showArticle(Article article) {
        currentArticle = article;
        editMode = false;

        // Navigate to article URL with slug
        if (article != null && article.getSlug() != null) {
            getUI().ifPresent(ui -> ui.navigate("knowledge/" + article.getSlug()));
        }

        updateUI();
    }

    private void enableEditMode() {
        if (!isAdmin) {
            Notification.show("Only administrators can edit articles");
            return;
        }
        editMode = true;
        updateUI();
    }

    private void cancelEdit() {
        editMode = false;
        if (currentArticle != null && currentArticle.getId() != null) {
            // Reload from database
            articleService.findById(currentArticle.getId()).ifPresent(this::showArticle);
        } else {
            currentArticle = null;
            updateUI();
        }
    }

    private void saveArticle() {
        if (currentArticle == null) {
            return;
        }

        currentArticle.setTitle(titleField.getValue());
        currentArticle.setContent(contentArea.getValue());

        try {
            currentArticle = articleService.save(currentArticle);
            refreshArticleList();
            highlightSelectedArticle(currentArticle);
            editMode = false;
            updateUI();
            Notification.show("Article saved successfully");
        } catch (Exception e) {
            Notification.show("Error saving article: " + e.getMessage());
        }
    }

    private void deleteArticle() {
        if (currentArticle == null || currentArticle.getId() == null) {
            return;
        }

        articleService.delete(currentArticle);
        currentArticle = null;
        refreshArticleList();
        updateUI();
        Notification.show("Article deleted");
    }

    private void updateUI() {
        boolean hasArticle = currentArticle != null;
        boolean hasId = hasArticle && currentArticle.getId() != null;

        // Update button visibility
        createButton.setVisible(isAdmin);
        editButton.setVisible(hasArticle && !editMode && isAdmin);
        previewButton.setVisible(editMode);
        saveButton.setVisible(editMode);
        cancelButton.setVisible(editMode);
        deleteButton.setVisible(hasId && isAdmin);

        // Update content visibility
        titleDisplay.setVisible(!editMode);
        titleField.setVisible(editMode);
        contentArea.setVisible(editMode);
        markdownPreview.setVisible(!editMode);

        if (hasArticle) {
            // Update title and content for articles
            if (editMode) {
                titleField.setValue(currentArticle.getTitle() != null ? currentArticle.getTitle() : "");
                contentArea.setValue(currentArticle.getContent() != null ? currentArticle.getContent() : "");
            } else {
                titleDisplay.setText(currentArticle.getTitle() != null ? currentArticle.getTitle() : "");
                renderMarkdown(currentArticle.getContent());
            }
        } else if (!editMode) {
            // Keep default welcome content visible (set by showDefaultWelcome)
            // Title and markdown are already set, don't override
        } else {
            // In edit mode but no article (shouldn't normally happen)
            titleDisplay.setText("");
            titleField.clear();
            contentArea.clear();
            markdownPreview.setContent("");
        }
    }

    private void togglePreview() {
        previewMode = !previewMode;

        if (previewMode) {
            // Show preview - render current markdown from contentArea
            titleDisplay.setText(titleField.getValue());
            renderMarkdown(contentArea.getValue());
            previewButton.setText("Edit");
        } else {
            // Back to edit mode
            previewButton.setText("Preview");
        }

        // Toggle visibility
        titleField.setVisible(!previewMode);
        contentArea.setVisible(!previewMode);
        titleDisplay.setVisible(previewMode);
        markdownPreview.setVisible(previewMode);
    }

    private void renderMarkdown(String markdown) {
        if (markdown != null && !markdown.isEmpty()) {
            this.markdownPreview.setContent(markdown);
        } else {
            this.markdownPreview.setContent("");
        }
    }
}
