package io.avec.knowledgebase.view;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import io.avec.data.Role;
import io.avec.knowledgebase.data.Article;
import io.avec.knowledgebase.service.ArticleService;
import io.avec.security.AuthenticatedUser;
import io.avec.views.MainLayout;
import jakarta.annotation.security.PermitAll;
import org.vaadin.lineawesome.LineAwesomeIconUrl;
import com.vaadin.flow.component.markdown.Markdown;

@PageTitle("KnowledgeBase")
@Route(value = "", layout = MainLayout.class)
@Menu(order = 0, icon = LineAwesomeIconUrl.GRADUATION_CAP_SOLID)
@PermitAll
public class KnowledgeBaseView extends VerticalLayout {

    private final ArticleService articleService;
    private final AuthenticatedUser authenticatedUser;

    private final Grid<Article> articleGrid = new Grid<>(Article.class, false);
    private final TextField titleField = new TextField("Title");
    private final TextArea contentArea = new TextArea("Content (Markdown)");
    private final Markdown markdownPreview = new Markdown("");
    private final H2 titleDisplay = new H2();

    private final Button createButton = new Button("Create");
    private final Button editButton = new Button("Edit");
    private final Button saveButton = new Button("Save");
    private final Button cancelButton = new Button("Cancel");
    private final Button deleteButton = new Button("Delete");

    private Article currentArticle;
    private boolean editMode = false;
    private boolean isAdmin = false;

    public KnowledgeBaseView(ArticleService articleService, AuthenticatedUser authenticatedUser) {
        this.articleService = articleService;
        this.authenticatedUser = authenticatedUser;

        checkAdminRole();

        setSizeFull();
        setPadding(false);
        setSpacing(false);

        HorizontalLayout mainLayout = createMainLayout();
        add(mainLayout);

        refreshArticleList();
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
        layout.setSpacing(true);

        VerticalLayout leftPanel = createLeftPanel();
        VerticalLayout rightPanel = createRightPanel();

        leftPanel.setWidth("300px");
        rightPanel.setSizeFull();

        layout.add(leftPanel, rightPanel);
        layout.setFlexGrow(0, leftPanel);
        layout.setFlexGrow(1, rightPanel);

        return layout;
    }

    private VerticalLayout createLeftPanel() {
        VerticalLayout panel = new VerticalLayout();
        panel.setSpacing(true);
        panel.setPadding(true);
        panel.getStyle().set("border-right", "1px solid var(--lumo-contrast-10pct)");

        articleGrid.addColumn(Article::getTitle).setHeader("Articles");
        articleGrid.asSingleSelect().addValueChangeListener(e -> {
            if (e.getValue() != null) {
                showArticle(e.getValue());
            }
        });
        articleGrid.setHeightFull();

        panel.add(articleGrid);
        panel.setFlexGrow(1, articleGrid);

        return panel;
    }

    private VerticalLayout createRightPanel() {
        VerticalLayout panel = new VerticalLayout();
        panel.setSizeFull();
        panel.setPadding(true);
        panel.setSpacing(true);

        // Button bar
        HorizontalLayout buttonBar = new HorizontalLayout();
        buttonBar.setWidthFull();
        buttonBar.setJustifyContentMode(JustifyContentMode.END);

        createButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        createButton.addClickListener(e -> createNewArticle());
        createButton.setVisible(false);

        editButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        editButton.addClickListener(e -> enableEditMode());
        editButton.setVisible(false);

        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        saveButton.addClickListener(e -> saveArticle());
        saveButton.setVisible(false);

        cancelButton.addClickListener(e -> cancelEdit());
        cancelButton.setVisible(false);

        deleteButton.addThemeVariants(ButtonVariant.LUMO_ERROR);
        deleteButton.addClickListener(e -> deleteArticle());
        deleteButton.setVisible(false);

        buttonBar.add(createButton, editButton, saveButton, cancelButton, deleteButton);

        // Title display (read mode)
        titleDisplay.getStyle().set("margin-top", "0");

        // Title field (edit mode)
        titleField.setWidthFull();
        titleField.setVisible(false);

        // Content area (edit mode)
        contentArea.setWidthFull();
        contentArea.setHeight("400px");
        contentArea.setVisible(false);

        // Markdown preview (read mode)
        markdownPreview.getStyle()
            .set("border", "1px solid var(--lumo-contrast-10pct)")
            .set("padding", "var(--lumo-space-m)")
            .set("border-radius", "var(--lumo-border-radius)")
            .set("overflow-y", "auto")
            .set("flex-grow", "1");

        panel.add(buttonBar, titleDisplay, titleField, contentArea, markdownPreview);
        panel.setFlexGrow(1, markdownPreview);

        return panel;
    }

    private void refreshArticleList() {
        articleGrid.setItems(isAdmin ? articleService.findAll() : articleService.findPublished());
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
            articleGrid.select(currentArticle);
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
        createButton.setVisible(isAdmin && !hasArticle);
        editButton.setVisible(hasArticle && !editMode && isAdmin);
        saveButton.setVisible(editMode);
        cancelButton.setVisible(editMode);
        deleteButton.setVisible(hasId && editMode && isAdmin);

        // Update content visibility
        titleDisplay.setVisible(hasArticle && !editMode);
        titleField.setVisible(editMode);
        contentArea.setVisible(editMode);
        markdownPreview.setVisible(hasArticle && !editMode);

        if (hasArticle) {
            // Update title
            if (editMode) {
                titleField.setValue(currentArticle.getTitle() != null ? currentArticle.getTitle() : "");
                contentArea.setValue(currentArticle.getContent() != null ? currentArticle.getContent() : "");
            } else {
                titleDisplay.setText(currentArticle.getTitle() != null ? currentArticle.getTitle() : "");
                renderMarkdown(currentArticle.getContent());
            }
        } else {
            titleDisplay.setText("");
            titleField.clear();
            contentArea.clear();
            markdownPreview.setContent("");
        }
    }

    private void renderMarkdown(String markdown) {
        if (markdown != null && !markdown.isEmpty()) {
            this.markdownPreview.setContent(markdown);
        } else {
            this.markdownPreview.setContent("");
        }
    }
}
