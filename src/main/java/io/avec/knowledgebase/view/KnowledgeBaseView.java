package io.avec.knowledgebase.view;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.treegrid.TreeGrid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.grid.dnd.GridDropLocation;
import com.vaadin.flow.component.grid.dnd.GridDropMode;
import com.vaadin.flow.data.provider.DataProvider;
import com.vaadin.flow.data.provider.hierarchy.TreeData;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.*;
import io.avec.data.Role;
import io.avec.knowledgebase.data.Article;
import io.avec.knowledgebase.data.ArticleStatus;
import io.avec.knowledgebase.data.Category;
import io.avec.knowledgebase.service.ArticleService;
import io.avec.knowledgebase.service.CategoryService;
import io.avec.knowledgebase.service.KnowledgeBaseExportService;
import io.avec.security.AuthenticatedUser;
import io.avec.views.MainLayout;
import jakarta.annotation.security.PermitAll;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vaadin.lineawesome.LineAwesomeIconUrl;
import com.vaadin.flow.component.markdown.Markdown;
import com.vaadin.flow.server.streams.DownloadHandler;
import com.vaadin.flow.server.streams.DownloadResponse;

import java.io.ByteArrayInputStream;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.function.Consumer;


@PageTitle("KnowledgeBase")
@Route(value = "knowledge", layout = MainLayout.class)
@Menu(order = 0, icon = LineAwesomeIconUrl.GRADUATION_CAP_SOLID)
@PermitAll
public class KnowledgeBaseView extends VerticalLayout implements HasUrlParameter<String>, BeforeLeaveObserver {

    private static final Logger LOGGER = LoggerFactory.getLogger(KnowledgeBaseView.class);

    private final ArticleService articleService;
    private final CategoryService categoryService;
    private final KnowledgeBaseExportService exportService;
    private final AuthenticatedUser authenticatedUser;

    private final TreeGrid<WikiType> articleTree = new TreeGrid<>();
    private final TextField titleField = new TextField("Title");
    private final ComboBox<Category> categoryField = new ComboBox<>("Category");
    private final ComboBox<ArticleStatus> statusField = new ComboBox<>("Status");
    private final TextArea contentArea = new TextArea();
    private final HorizontalLayout contentHeader = new HorizontalLayout();
    private final Button markdownHelpToggleButton = new Button(VaadinIcon.INFO_CIRCLE_O.create());
    private final HorizontalLayout contentEditorLayout = new HorizontalLayout();
    private final Markdown markdownHelpPreview = new Markdown("");
    private final Markdown markdownPreview = new Markdown("");
    private final Markdown editorPreview = new Markdown("");
    private final H2 titleDisplay = new H2();
    private final Div metadataDisplay = new Div();
    private final Div markdownHelpPanel = new Div();
    private final Div editorPreviewPanel = new Div();
    private final ComboBox<WikiType> menuSearch = new ComboBox<>();
    private final Button toggleCategoriesButton = new Button();
    private final Map<String, WikiType> nodeBySlug = new HashMap<>();
    private final Map<WikiType, WikiType> parentByNode = new HashMap<>();
    private final List<WikiType> quickJumpItems = new ArrayList<>();
    private final List<WikiType> categoryNodes = new ArrayList<>();
    private WikiType welcomeNode;
    private WikiType draggedNode;

    private final Button createButton = new Button("New article");
    private final Button createCategoryButton = new Button("New category");
    private final Button editButton = new Button("Edit");
    private final Button editCategoryButton = new Button("Edit category");
    private final Button previewButton = new Button("Preview");
    private final Button saveButton = new Button("Save");
    private final Button cancelButton = new Button("Cancel");
    private final Button deleteButton = new Button("Delete");
    private final Button deleteCategoryButton = new Button("Delete category");
    private final Button exportButton = new Button("Export");

    private final VerticalLayout sidebar = new VerticalLayout();
    private final VerticalLayout editorLayout = new VerticalLayout();
    private final Div articleColumn = new Div();
    private final HorizontalLayout crumbs = new HorizontalLayout();
    private final Span editingBadge = new Span("Editing");
    private final Span headerDivider = new Span();
    private Anchor exportLink;

    private Article currentArticle;
    private Category currentCategory;
    private boolean editMode = false;
    private boolean previewMode = false;
    private boolean isAdmin = false;
    private String currentSearchQuery = "";
    private static final String WELCOME_SLUG = "welcome-to-knowledge";
    private static final String MARKDOWN_HELP_SLUG = "markdown-syntax";
    private static final String CREATE_CATEGORY_SLUG = "__create_category__";
    private static final DateTimeFormatter METADATA_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter EXPORT_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private boolean markdownHelpVisible = false;

    public KnowledgeBaseView(ArticleService articleService, CategoryService categoryService,
                             KnowledgeBaseExportService exportService, AuthenticatedUser authenticatedUser) {
        this.articleService = articleService;
        this.categoryService = categoryService;
        this.exportService = exportService;
        this.authenticatedUser = authenticatedUser;

        checkAdminRole();

        addClassName("knowledge-base-view");
        setSizeFull();
        setPadding(false);
        setSpacing(false);

        add(createMainLayout());

        refreshArticleList();
    }

    @Override
    public void setParameter(BeforeEvent event, @OptionalParameter String slug) {
        editMode = false;
        previewMode = false;
        markdownHelpVisible = false;
        if (slug != null && !slug.isEmpty()) {

            // Check if it's the welcome article
            if (WELCOME_SLUG.equals(slug)) {
                showDefaultWelcome();
                highlightSelectedArticle(null);
            } else {
                // Load article by slug
                articleService.findVisibleBySlug(slug).ifPresentOrElse(
                    article -> {
                        currentArticle = article;
                        currentCategory = null;
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
        currentCategory = null;
        titleDisplay.setText("Welcome to KnowledgeBase");
        metadataDisplay.getElement().setProperty("innerHTML", "");
        metadataDisplay.setVisible(false);
        markdownPreview.setContent("# Welcome to KnowledgeBase\n\nSelect an article from the list to get started.");
        markdownPreview.setVisible(true);

        try (InputStream is = getClass().getClassLoader().getResourceAsStream("knowledge/welcome-to-knowledge.md")) {
            if (is != null) {
                String content = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))
                    .lines()
                    .collect(Collectors.joining("\n"));
                markdownPreview.setContent(content);
                markdownPreview.setVisible(true);
            }
        } catch (Exception e) {
            LOGGER.warn("Could not load the knowledge base welcome content", e);
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

        configureArticleTree();
        VerticalLayout sidebarLayout = createSidebar();
        VerticalLayout rightPanel = createRightPanel();

        layout.add(sidebarLayout, rightPanel);
        layout.setFlexGrow(0, sidebarLayout);
        layout.setFlexGrow(1, rightPanel);

        return layout;
    }

    private VerticalLayout createSidebar() {
        sidebar.addClassName("kb-sidebar");
        sidebar.setPadding(false);
        sidebar.setSpacing(false);
        sidebar.setHeightFull();
        sidebar.getStyle().set("flex", "0 1 clamp(220px, 26vw, 360px)");

        VerticalLayout top = new VerticalLayout();
        top.addClassName("kb-sidebar-top");
        top.setPadding(false);
        top.setSpacing(false);
        top.setWidthFull();
        top.add(menuSearch, toggleCategoriesButton);

        articleTree.addClassName("kb-tree");
        articleTree.setWidthFull();
        articleTree.addThemeVariants(GridVariant.LUMO_NO_BORDER, GridVariant.LUMO_NO_ROW_BORDERS, GridVariant.LUMO_COMPACT);

        HorizontalLayout footer = new HorizontalLayout();
        footer.addClassName("kb-sidebar-footer");
        footer.setWidthFull();
        footer.setPadding(false);
        footer.setSpacing(false);

        createButton.setIcon(VaadinIcon.PLUS.create());
        createButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        createButton.addClickListener(e -> createNewArticle());
        createButton.setVisible(false);

        createCategoryButton.setIcon(VaadinIcon.PLUS.create());
        createCategoryButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        createCategoryButton.addClickListener(e -> openCreateCategoryDialog());
        createCategoryButton.setVisible(false);

        footer.add(createButton, createCategoryButton);

        sidebar.add(top, articleTree, footer);
        sidebar.setFlexGrow(1, articleTree);
        return sidebar;
    }

    private HorizontalLayout createContentHeader() {
        HorizontalLayout header = new HorizontalLayout();
        header.addClassName("kb-content-header");
        header.setWidthFull();
        header.setPadding(false);
        header.setSpacing(false);
        header.setAlignItems(Alignment.CENTER);
        header.setJustifyContentMode(JustifyContentMode.BETWEEN);

        crumbs.addClassName("kb-crumbs");
        crumbs.setPadding(false);
        crumbs.setSpacing(false);
        crumbs.setAlignItems(Alignment.CENTER);

        editingBadge.addClassName("status-badge");
        editingBadge.addClassName("status-badge-neutral");
        editingBadge.addClassName("kb-editing-badge");

        HorizontalLayout actions = new HorizontalLayout();
        actions.addClassName("kb-header-actions");
        actions.setPadding(false);
        actions.setSpacing(false);
        actions.setAlignItems(Alignment.CENTER);

        editButton.setIcon(VaadinIcon.PENCIL.create());
        editButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        editButton.addClickListener(e -> enableEditMode());
        editButton.setVisible(false);

        deleteButton.setIcon(VaadinIcon.TRASH.create());
        deleteButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR);
        deleteButton.addClickListener(e -> deleteArticle());
        deleteButton.setVisible(false);

        editCategoryButton.setIcon(VaadinIcon.FOLDER_O.create());
        editCategoryButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        editCategoryButton.addClickListener(e -> openEditCategoryDialog());
        editCategoryButton.setVisible(false);

        deleteCategoryButton.setIcon(VaadinIcon.FOLDER_O.create());
        deleteCategoryButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR);
        deleteCategoryButton.addClickListener(e -> deleteCategory());
        deleteCategoryButton.setVisible(false);

        previewButton.setIcon(VaadinIcon.EYE.create());
        previewButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        previewButton.addClickListener(e -> togglePreview());
        previewButton.setVisible(false);

        cancelButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        cancelButton.addClickListener(e -> cancelEdit());
        cancelButton.setVisible(false);

        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
        saveButton.addClickListener(e -> saveArticle());
        saveButton.setVisible(false);

        headerDivider.addClassName("kb-header-divider");
        headerDivider.setVisible(false);

        exportButton.setIcon(VaadinIcon.DOWNLOAD_ALT.create());
        exportButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        exportLink = new Anchor(createExportHandler(), "");
        exportLink.getElement().setAttribute("download", true);
        exportLink.add(exportButton);

        actions.add(editCategoryButton, deleteCategoryButton, editButton, deleteButton,
            headerDivider, exportLink, previewButton, cancelButton, saveButton);

        header.add(crumbs, actions);
        return header;
    }

    private void refreshArticleList() {
        nodeBySlug.clear();
        parentByNode.clear();
        quickJumpItems.clear();
        categoryNodes.clear();
        TreeData<WikiType> treeData = new TreeData<>();
        refreshCategoryFieldItems();

        welcomeNode = WikiType.welcome("Velkommen", WELCOME_SLUG);
        treeData.addItem(null, welcomeNode);
        parentByNode.put(welcomeNode, null);
        nodeBySlug.put(WELCOME_SLUG, welcomeNode);

        try {
            // Get root categories
            List<Category> rootCategories = categoryService.findRootCategories();
            for (Category category : rootCategories) {
                WikiType categoryNode = WikiType.category(category);
                treeData.addItem(null, categoryNode);
                parentByNode.put(categoryNode, null);
                quickJumpItems.add(categoryNode);
                categoryNodes.add(categoryNode);
                addCategoryChildren(treeData, categoryNode, category);
            }

            // Add uncategorized articles at the end
            List<Article> uncategorized = articleService.findVisibleUncategorized();
            if (!uncategorized.isEmpty()) {
                WikiType uncategorizedNode = WikiType.section("Uncategorized");
                treeData.addItem(null, uncategorizedNode);
                parentByNode.put(uncategorizedNode, null);
                for (Article article : uncategorized) {
                    WikiType articleNode = WikiType.article(article);
                    treeData.addItem(uncategorizedNode, articleNode);
                    parentByNode.put(articleNode, uncategorizedNode);
                    nodeBySlug.put(article.getSlug(), articleNode);
                    quickJumpItems.add(articleNode);
                }
            }

            articleTree.setTreeData(treeData);
            menuSearch.getDataProvider().refreshAll();
            menuSearch.clear();
            updateCategoryToggleButton();
        } catch (Exception e) {
            System.err.println("Error refreshing article list: " + e.getMessage());
            e.printStackTrace();
            Notification.show("Error loading articles: " + e.getMessage());
        }
    }

    private void highlightSelectedArticle(Article article) {
        if (article == null) {
            if (welcomeNode != null) {
                articleTree.select(welcomeNode);
            } else {
                articleTree.deselectAll();
            }
            return;
        }

        WikiType selectedNode = nodeBySlug.get(article.getSlug());
        if (selectedNode != null) {
            articleTree.select(selectedNode);
        } else {
            articleTree.deselectAll();
        }
    }

    private DownloadHandler createExportHandler() {
        String exportFileName = LocalDate.now().format(EXPORT_DATE_FORMAT) + "-knowledge-base-export.zip";
        return DownloadHandler.fromInputStream(event -> {
            try {
                byte[] content = exportService.generateExportZipBytes();
                return new DownloadResponse(
                    new ByteArrayInputStream(content), exportFileName, "application/zip", content.length);
            } catch (Exception e) {
                LOGGER.error("Could not generate knowledge base export", e);
                return DownloadResponse.error(500, "Could not generate knowledge base export", e);
            }
        });
    }

    private void refreshCategoryFieldItems() {
        List<Category> options = new ArrayList<>();
        if (isAdmin) {
            Category createOption = new Category();
            createOption.setSlug(CREATE_CATEGORY_SLUG);
            createOption.setName("Create");
            options.add(createOption);
        }
        options.addAll(categoryService.findRootCategories());
        categoryField.setItems(options);
    }

    private VerticalLayout createRightPanel() {
        VerticalLayout panel = new VerticalLayout();
        panel.addClassName("kb-content");
        panel.setSizeFull();
        panel.setPadding(false);
        panel.setSpacing(false);

        panel.add(createContentHeader());

        Div body = new Div();
        body.addClassName("kb-article");
        body.setWidthFull();

        // Read mode: article column with title, one-line metadata and wiki content
        articleColumn.addClassName("kb-article-col");
        titleDisplay.addClassName("article-title");
        metadataDisplay.addClassName("kb-meta-row");
        markdownPreview.setWidthFull();
        markdownPreview.addClassName("wiki-content");
        articleColumn.add(titleDisplay, metadataDisplay, markdownPreview);

        buildEditor();

        body.add(articleColumn, editorLayout);
        panel.add(body);
        panel.setFlexGrow(1, body);

        return panel;
    }

    private void buildEditor() {
        editorLayout.addClassName("kb-editor");
        editorLayout.setPadding(false);
        editorLayout.setSpacing(false);
        editorLayout.setWidthFull();
        editorLayout.setVisible(false);

        // Title field (edit mode)
        titleField.setWidthFull();

        // Category field (edit mode)
        categoryField.setWidthFull();
        refreshCategoryFieldItems();
        categoryField.setItemLabelGenerator(this::categoryOptionLabel);
        categoryField.setRenderer(new ComponentRenderer<>(category -> {
            if (isCreateCategoryOption(category)) {
                Span createBadge = new Span("Create");
                createBadge.getStyle()
                    .set("font-size", "var(--lumo-font-size-xs)")
                    .set("padding", "2px 8px")
                    .set("border-radius", "var(--lumo-border-radius-m)")
                    .set("background", "var(--lumo-contrast-10pct)")
                    .set("color", "var(--lumo-primary-text-color)");
                return createBadge;
            }
            return new Span(categoryOptionLabel(category));
        }));
        categoryField.addValueChangeListener(event -> {
            Category selected = event.getValue();
            if (!isCreateCategoryOption(selected)) {
                return;
            }
            Category previous = currentArticle != null ? currentArticle.getCategory() : null;
            categoryField.clear();
            openCreateCategoryDialog(createdCategory -> {
                refreshCategoryFieldItems();
                if (createdCategory != null) {
                    categoryField.setValue(createdCategory);
                } else if (previous != null) {
                    categoryField.setValue(previous);
                }
            });
        });
        categoryField.setPlaceholder("Select a category (optional)");
        categoryField.setClearButtonVisible(true);

        // Status field (edit mode)
        statusField.setWidthFull();
        statusField.setItems(ArticleStatus.DRAFT, ArticleStatus.PUBLISHED);
        statusField.setItemLabelGenerator(ArticleStatus::name);

        HorizontalLayout fieldRow = new HorizontalLayout(categoryField, statusField);
        fieldRow.addClassName("kb-field-row");
        fieldRow.setWidthFull();
        fieldRow.setPadding(false);

        // Content label row with markdown help toggle
        contentHeader.setWidthFull();
        contentHeader.setPadding(false);
        contentHeader.setSpacing(true);
        contentHeader.setMargin(false);
        contentHeader.setAlignItems(Alignment.CENTER);
        Span contentHeaderLabel = new Span("Content (Markdown)");
        contentHeaderLabel.getStyle().set("font-weight", "600");
        markdownHelpToggleButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE, ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ICON);
        markdownHelpToggleButton.getElement().setProperty("title", "Markdown syntax help");
        markdownHelpToggleButton.getElement().setAttribute("aria-label", "Markdown syntax help");
        markdownHelpToggleButton.addClickListener(event -> toggleMarkdownHelp());
        contentHeader.add(contentHeaderLabel, markdownHelpToggleButton);

        // Content area (edit mode) - monospace font for Markdown editing
        contentArea.setLabel(null);
        contentArea.setAriaLabel("Content (Markdown)");
        contentArea.setWidthFull();
        contentArea.setValueChangeMode(ValueChangeMode.LAZY);
        contentArea.addValueChangeListener(event -> {
            if (editorPreviewPanel.isVisible()) {
                editorPreview.setContent(event.getValue());
            }
        });
        contentArea.getStyle()
            .set("font-family", "monospace")
            .set("font-size", "0.9em")
            .set("line-height", "1.5");

        // Side-by-side live preview panel (edit mode)
        Span previewCaption = new Span("Preview");
        previewCaption.addClassName("kb-preview-caption");
        Div previewBody = new Div();
        previewBody.addClassName("kb-preview-body");
        editorPreview.addClassName("wiki-content");
        previewBody.add(editorPreview);
        editorPreviewPanel.addClassName("kb-preview-panel");
        editorPreviewPanel.add(previewCaption, previewBody);
        editorPreviewPanel.setVisible(false);

        // Markdown help panel (edit mode, toggled by the info icon)
        markdownHelpPreview.setContent(loadMarkdownHelpContent());
        markdownHelpPreview.addClassName("wiki-content");
        markdownHelpPreview.addClassName("markdown-help-content");
        markdownHelpPanel.addClassName("markdown-help-panel");
        markdownHelpPanel.add(markdownHelpPreview);
        markdownHelpPanel.setVisible(false);

        contentEditorLayout.addClassName("content-editor-layout");
        contentEditorLayout.setWidthFull();
        contentEditorLayout.setPadding(false);
        contentEditorLayout.setSpacing(false);
        contentEditorLayout.setMargin(false);
        contentEditorLayout.setAlignItems(Alignment.STRETCH);
        contentEditorLayout.add(contentArea, editorPreviewPanel, markdownHelpPanel);

        editorLayout.add(titleField, fieldRow, contentHeader, contentEditorLayout);
        editorLayout.setFlexGrow(1, contentEditorLayout);
    }

    private void createNewArticle() {
        currentArticle = new Article();
        authenticatedUser.get().ifPresent(user -> {
            currentArticle.setCreatedBy(user);
            currentArticle.setUpdatedBy(user);
        });
        currentArticle.setStatus(ArticleStatus.DRAFT);
        enableEditMode();
        titleField.clear();
        contentArea.clear();
        markdownPreview.setContent("");
        editorPreview.setContent("");
        titleDisplay.setText("");
    }

    private void showArticle(Article article) {
        currentArticle = article;
        editMode = false;
        highlightSelectedArticle(article);

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
        previewMode = true;
        markdownHelpVisible = false;
        updateUI();
    }

    private void cancelEdit() {
        editMode = false;
        previewMode = false;
        markdownHelpVisible = false;
        if (currentArticle != null && currentArticle.getId() != null) {
            // Reload from database and navigate to article
            articleService.findById(currentArticle.getId()).ifPresent(article -> {
                getUI().ifPresent(ui -> ui.navigate("knowledge/" + article.getSlug()));
            });
        } else {
            // No article or new article - go back to welcome
            getUI().ifPresent(ui -> ui.navigate("knowledge/welcome-to-knowledge"));
        }
    }

    private void saveArticle() {
        if (currentArticle == null) {
            return;
        }

        currentArticle.setTitle(titleField.getValue());
        currentArticle.setCategory(categoryField.getValue());
        currentArticle.setStatus(statusField.getValue() != null ? statusField.getValue() : ArticleStatus.DRAFT);
        currentArticle.setContent(contentArea.getValue());
        authenticatedUser.get().ifPresent(currentArticle::setUpdatedBy);

        try {
            currentArticle = articleService.save(currentArticle);
            editMode = false;
            previewMode = false;
            markdownHelpVisible = false;
            refreshArticleList();
            highlightSelectedArticle(currentArticle);
            updateUI();
            getUI().ifPresent(ui -> ui.navigate("knowledge/" + currentArticle.getSlug()));
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
        getUI().ifPresent(ui -> ui.navigate("knowledge/" + WELCOME_SLUG));
        Notification.show("Article deleted");
    }

    @Override
    public void beforeLeave(BeforeLeaveEvent event) {
        if (!editMode) {
            return;
        }

        BeforeLeaveEvent.ContinueNavigationAction navigation = event.postpone();
        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader("Discard unsaved changes?");
        dialog.setText("Your article changes have not been saved.");
        dialog.setCancelable(true);
        dialog.setCancelText("Continue editing");
        dialog.setConfirmText("Discard changes");
        dialog.setConfirmButtonTheme("error primary");
        dialog.addConfirmListener(confirmEvent -> navigation.proceed());
        dialog.addCancelListener(cancelEvent -> navigation.cancel());
        dialog.open();
    }

    private void openCreateCategoryDialog() {
        openCreateCategoryDialog(null);
    }

    private void openCreateCategoryDialog(Consumer<Category> onCreated) {
        if (!isAdmin) {
            Notification.show("Only administrators can create categories");
            return;
        }
        openCategoryDialog(new Category(), "Create Category", onCreated);
    }

    private void openEditCategoryDialog() {
        if (!isAdmin || currentCategory == null) {
            Notification.show("Select a category first");
            return;
        }
        openCategoryDialog(currentCategory, "Edit Category", null);
    }

    private void openCategoryDialog(Category category, String title, Consumer<Category> onCreated) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(title);

        TextField nameField = new TextField("Name");
        nameField.setWidthFull();
        nameField.setRequiredIndicatorVisible(true);
        nameField.setValue(category.getName() != null ? category.getName() : "");

        TextArea descriptionField = new TextArea("Description");
        descriptionField.setWidthFull();
        descriptionField.setValue(category.getDescription() != null ? category.getDescription() : "");

        VerticalLayout content = new VerticalLayout(nameField, descriptionField);
        content.setPadding(false);
        content.setSpacing(true);
        dialog.add(content);

        Button cancel = new Button("Cancel", e -> dialog.close());
        Button save = new Button("Save", e -> {
            String name = nameField.getValue() != null ? nameField.getValue().trim() : "";
            if (name.isEmpty()) {
                Notification.show("Category name is required");
                return;
            }

            category.setName(name);
            String description = descriptionField.getValue() != null ? descriptionField.getValue().trim() : "";
            category.setDescription(description.isEmpty() ? null : description);
            if (category.getId() == null) {
                category.setParent(null);
                category.setSortOrder(categoryService.findRootCategories().size() + 1);
            }

            try {
                Category saved = categoryService.save(category);
                currentCategory = saved;
                refreshArticleList();
                selectCategory(saved);
                updateUI();
                dialog.close();
                if (onCreated != null) {
                    onCreated.accept(saved);
                }
                Notification.show("Category saved");
            } catch (Exception ex) {
                Notification.show("Error saving category: " + ex.getMessage());
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        dialog.getFooter().add(cancel, save);
        dialog.open();
    }

    private void selectCategory(Category category) {
        if (category == null) {
            return;
        }
        articleTree.getTreeData().getRootItems().stream()
            .filter(node -> node.type() == WikiNodeType.CATEGORY && node.category() != null && node.category().getId() != null
                && node.category().getId().equals(category.getId()))
            .findFirst()
            .ifPresent(node -> {
                articleTree.select(node);
                articleTree.expand(node);
            });
    }

    private void deleteCategory() {
        if (!isAdmin || currentCategory == null || currentCategory.getId() == null) {
            Notification.show("Select a category first");
            return;
        }

        Category category = currentCategory;
        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader("Delete category?");
        dialog.setText("Delete category \"" + category.getName() + "\"?");
        dialog.setCancelable(true);
        dialog.setConfirmText("Delete");
        dialog.setConfirmButtonTheme("error primary");
        dialog.addConfirmListener(event -> performCategoryDelete(category));
        dialog.open();
    }

    private void performCategoryDelete(Category category) {
        try {
            categoryService.delete(category);
            currentCategory = null;
            refreshArticleList();
            updateUI();
            Notification.show("Category deleted");
        } catch (Exception ex) {
            Notification.show("Error deleting category: " + ex.getMessage());
        }
    }

    private void updateUI() {
        boolean hasArticle = currentArticle != null;
        boolean hasId = hasArticle && currentArticle.getId() != null;
        boolean hasCategory = currentCategory != null && currentCategory.getId() != null;

        // Update button visibility
        createButton.setVisible(isAdmin && !editMode);
        createCategoryButton.setVisible(isAdmin && !editMode);
        editButton.setVisible(hasArticle && !editMode && isAdmin);
        editCategoryButton.setVisible(hasCategory && !editMode && isAdmin);
        previewButton.setVisible(editMode);
        saveButton.setVisible(editMode);
        cancelButton.setVisible(editMode);
        deleteButton.setVisible(hasId && !editMode && isAdmin);
        deleteCategoryButton.setVisible(hasCategory && !editMode && isAdmin);
        if (exportLink != null) {
            exportLink.setVisible(!editMode);
        }
        headerDivider.setVisible(!editMode && isAdmin && (hasArticle || hasCategory));

        articleTree.setEnabled(!editMode);
        menuSearch.setEnabled(!editMode);
        toggleCategoriesButton.setEnabled(!editMode);
        articleTree.setRowsDraggable(isAdmin && !editMode);
        articleTree.setDropMode(isAdmin && !editMode ? GridDropMode.BETWEEN : null);

        // Sidebar is dimmed while editing
        if (editMode) {
            sidebar.addClassName("dimmed");
        } else {
            sidebar.removeClassName("dimmed");
        }

        // Update content visibility
        articleColumn.setVisible(!editMode);
        // The welcome markdown carries its own H1, so only show the title for real articles
        titleDisplay.setVisible(!editMode && hasArticle);
        markdownPreview.setVisible(!editMode);
        editorLayout.setVisible(editMode);
        editorPreviewPanel.setVisible(editMode && previewMode && !markdownHelpVisible);
        markdownHelpPanel.setVisible(editMode && markdownHelpVisible);
        metadataDisplay.setVisible(!editMode && hasArticle);
        updatePreviewButton();

        if (hasArticle) {
            // Update title and content for articles
            if (editMode) {
                titleField.setValue(currentArticle.getTitle() != null ? currentArticle.getTitle() : "");
                categoryField.setValue(currentArticle.getCategory());
                statusField.setValue(currentArticle.getStatus() != null ? currentArticle.getStatus() : ArticleStatus.DRAFT);
                contentArea.setValue(currentArticle.getContent() != null ? currentArticle.getContent() : "");
                editorPreview.setContent(contentArea.getValue());
            } else {
                titleDisplay.setText(currentArticle.getTitle() != null ? currentArticle.getTitle() : "");
                renderMetaRow(currentArticle);
                renderMarkdown(currentArticle.getContent());
            }
        } else if (!editMode) {
            // Keep default welcome content visible (set by showDefaultWelcome)
            // Title and markdown are already set, don't override
            metadataDisplay.setVisible(false);
        } else {
            // In edit mode but no article (shouldn't normally happen)
            titleDisplay.setText("");
            metadataDisplay.getElement().setProperty("innerHTML", "");
            titleField.clear();
            contentArea.clear();
            editorPreview.setContent("");
        }

        updateCrumbs();
    }

    private void updateCrumbs() {
        crumbs.removeAll();

        String categoryLabel = null;
        String currentLabel;
        if (currentArticle != null) {
            if (currentArticle.getCategory() != null && currentArticle.getCategory().getName() != null) {
                categoryLabel = currentArticle.getCategory().getName();
            }
            String title = currentArticle.getTitle();
            if (editMode && currentArticle.getId() == null) {
                currentLabel = "New article";
            } else {
                currentLabel = title != null && !title.isBlank() ? title : "Untitled";
            }
        } else if (currentCategory != null && currentCategory.getName() != null) {
            currentLabel = currentCategory.getName();
        } else {
            currentLabel = "Velkommen";
        }

        if (categoryLabel != null) {
            Span category = new Span(categoryLabel);
            Icon separator = VaadinIcon.ANGLE_RIGHT.create();
            Span separatorWrapper = new Span(separator);
            separatorWrapper.addClassName("kb-crumb-sep");
            crumbs.add(category, separatorWrapper);
        }

        Span current = new Span(currentLabel);
        current.addClassName("kb-crumb-current");
        crumbs.add(current);

        if (editMode) {
            crumbs.add(editingBadge);
        }
    }

    private void updatePreviewButton() {
        boolean showingPreview = previewMode && !markdownHelpVisible;
        previewButton.setText(showingPreview ? "Hide preview" : "Preview");
    }

    private void togglePreview() {
        if (markdownHelpVisible) {
            // Switch from help panel back to preview
            markdownHelpVisible = false;
            previewMode = true;
        } else {
            previewMode = !previewMode;
        }
        if (previewMode) {
            editorPreview.setContent(contentArea.getValue());
        }
        editorPreviewPanel.setVisible(editMode && previewMode && !markdownHelpVisible);
        markdownHelpPanel.setVisible(editMode && markdownHelpVisible);
        updatePreviewButton();
    }

    private void toggleMarkdownHelp() {
        markdownHelpVisible = !markdownHelpVisible;
        if (markdownHelpVisible) {
            markdownHelpPreview.setContent(loadMarkdownHelpContent());
        }
        if (editMode) {
            editorPreviewPanel.setVisible(previewMode && !markdownHelpVisible);
            markdownHelpPanel.setVisible(markdownHelpVisible);
        }
        updatePreviewButton();
    }

    private String loadMarkdownHelpContent() {
        return articleService.findVisibleBySlug(MARKDOWN_HELP_SLUG)
            .map(Article::getContent)
            .filter(content -> content != null && !content.isBlank())
            .orElse("""
                # Markdown syntax

                Use these raw markdown examples while writing.

                ## Headings
                ````text
                # Heading 1
                ## Heading 2
                ### Heading 3
                ````

                ## Emphasis
                ````text
                **bold**
                *italic*
                ~~strikethrough~~
                ````

                ## Lists
                ````text
                - Bullet item
                - Another item
                1. Numbered item
                2. Next item
                ````

                ## Links and images
                ````text
                [Knowledge Base](https://example.com)
                ![Alt text](https://via.placeholder.com/160x80)
                ````

                ## Horizontal rule
                ````text
                ***
                ````
                """);
    }

    private void renderMarkdown(String markdown) {
        if (markdown != null && !markdown.isEmpty()) {
            this.markdownPreview.setContent(markdown);
        } else {
            this.markdownPreview.setContent("");
        }
    }

    private void configureArticleTree() {
        var menuColumn = articleTree.addHierarchyColumn(WikiType::label)
            .setHeader((String) null)
            .setAutoWidth(true)
            .setFlexGrow(1);
        menuColumn.setSortable(false);
        menuColumn.setResizable(false);
        menuSearch.setPrefixComponent(VaadinIcon.SEARCH.create());
        menuSearch.setPlaceholder("Search articles…");
        menuSearch.setClearButtonVisible(true);
        menuSearch.setWidthFull();
        menuSearch.setItemLabelGenerator(WikiType::label);
        menuSearch.setAllowCustomValue(true);
        menuSearch.setRenderer(new ComponentRenderer<>(item -> {
            VerticalLayout layout = new VerticalLayout();
            layout.setPadding(false);
            layout.setSpacing(false);
            Span title = new Span(item.label());
            title.getStyle().set("font-weight", "bold");
            layout.add(title);
            if (item.type() == WikiNodeType.ARTICLE && item.article() != null && !currentSearchQuery.isBlank()) {
                String snippet = extractSnippet(item.article().getContent(), currentSearchQuery);
                if (!snippet.isEmpty()) {
                    Span snippetSpan = new Span(snippet);
                    snippetSpan.getStyle()
                        .set("font-size", "var(--lumo-font-size-s)")
                        .set("color", "var(--lumo-secondary-text-color)");
                    layout.add(snippetSpan);
                }
            }
            return layout;
        }));
        menuSearch.addValueChangeListener(event -> {
            if (event.getValue() != null) {
                handleQuickJump(event.getValue());
            }
        });
        menuSearch.setItems(DataProvider.fromFilteringCallbacks(
            query -> {
                String filter = query.getFilter().orElse("").trim();
                if (filter.length() < 2) {
                    currentSearchQuery = "";
                    return quickJumpItems.stream().skip(query.getOffset()).limit(query.getLimit());
                }
                currentSearchQuery = filter;
                String q = filter.toLowerCase();
                List<Article> results = isAdmin
                    ? articleService.search(filter)
                    : articleService.searchPublished(filter);
                results.sort(Comparator.comparingInt(a ->
                    a.getTitle().toLowerCase().contains(q) ? 0 : 1));
                return results.stream().map(WikiType::article).skip(query.getOffset()).limit(query.getLimit());
            },
            query -> {
                String filter = query.getFilter().orElse("").trim();
                if (filter.length() < 2) return quickJumpItems.size();
                return isAdmin
                    ? articleService.search(filter).size()
                    : articleService.searchPublished(filter).size();
            }
        ));
        toggleCategoriesButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE, ButtonVariant.LUMO_SMALL);
        toggleCategoriesButton.addClickListener(event -> toggleAllCategories());

        menuColumn.setRenderer(new ComponentRenderer<>(node -> {
                HorizontalLayout itemLayout = new HorizontalLayout();
                itemLayout.setSpacing(true);
                itemLayout.setPadding(false);
                itemLayout.setMargin(false);
                itemLayout.setAlignItems(Alignment.CENTER);
                itemLayout.setWidthFull();

                Icon icon = switch (node.type()) {
                    case WELCOME -> VaadinIcon.HOME.create();
                    case CATEGORY -> articleTree.isExpanded(node) ? VaadinIcon.FOLDER_OPEN.create() : VaadinIcon.FOLDER.create();
                    case ARTICLE -> VaadinIcon.FILE_O.create();
                    case SECTION -> articleTree.isExpanded(node) ? VaadinIcon.FOLDER_OPEN.create() : VaadinIcon.FOLDER.create();
                };
                icon.getStyle().set("color", "var(--lumo-secondary-text-color)");
                icon.setSize("var(--lumo-icon-size-s)");

                Span label = new Span(node.label());
                if (node.type() == WikiNodeType.CATEGORY || node.type() == WikiNodeType.SECTION) {
                    label.getStyle().set("font-weight", "600");
                }
                if (node.type() == WikiNodeType.CATEGORY) {
                    label.getStyle()
                        .set("color", "var(--_lumo-button-text-color, var(--lumo-primary-text-color))")
                    ;
                }
                label.getStyle()
                    .set("overflow", "hidden")
                    .set("text-overflow", "ellipsis")
                    .set("white-space", "nowrap")
                    .set("flex", "1");
                label.getElement().setProperty("title", node.label());

                itemLayout.add(icon, label);
                return itemLayout;
            }));

        articleTree.setPartNameGenerator(node ->
            node != null && node.type() == WikiNodeType.CATEGORY ? "category" : null
        );
        configureCategoryDragAndDrop();
        articleTree.addExpandListener(event -> articleTree.getDataProvider().refreshAll());
        articleTree.addExpandListener(event -> updateCategoryToggleButton());
        articleTree.addCollapseListener(event -> articleTree.getDataProvider().refreshAll());
        articleTree.addCollapseListener(event -> updateCategoryToggleButton());
        articleTree.addSelectionListener(event -> {
            event.getFirstSelectedItem().ifPresent(node -> {
                if (node.type() == WikiNodeType.WELCOME) {
                    currentCategory = null;
                    if (currentArticle == null) {
                        return;
                    }
                    getUI().ifPresent(ui -> ui.navigate("knowledge/" + WELCOME_SLUG));
                } else if (node.type() == WikiNodeType.ARTICLE && node.article() != null) {
                    currentCategory = null;
                    if (currentArticle != null && currentArticle.getId() != null
                        && currentArticle.getId().equals(node.article().getId())) {
                        return;
                    }
                    showArticle(node.article());
                } else if (node.type() == WikiNodeType.CATEGORY || node.type() == WikiNodeType.SECTION) {
                    currentCategory = node.category();
                    updateUI();
                }
            });
        });
        articleTree.addItemClickListener(event -> {
            WikiType node = event.getItem();
            if (node == null) {
                return;
            }
            if (node.type() == WikiNodeType.CATEGORY || node.type() == WikiNodeType.SECTION) {
                if (articleTree.isExpanded(node)) {
                    articleTree.collapse(node);
                } else {
                    articleTree.expand(node);
                }
            }
        });
    }

    private void configureCategoryDragAndDrop() {
        articleTree.setRowsDraggable(isAdmin);
        articleTree.setDropMode(isAdmin ? GridDropMode.BETWEEN : null);

        if (!isAdmin) {
            return;
        }

        articleTree.addDragStartListener(event -> {
            draggedNode = event.getDraggedItems().stream().findFirst().orElse(null);
            if (draggedNode == null || draggedNode.type() != WikiNodeType.CATEGORY || draggedNode.category() == null) {
                draggedNode = null;
            }
        });

        articleTree.addDragEndListener(event -> draggedNode = null);

        articleTree.addDropListener(event -> {
            if (draggedNode == null || draggedNode.type() != WikiNodeType.CATEGORY || draggedNode.category() == null) {
                return;
            }

            WikiType target = event.getDropTargetItem().orElse(null);
            if (target == null || target.type() != WikiNodeType.CATEGORY || target.category() == null) {
                return;
            }

            if (draggedNode.category().getId() != null && draggedNode.category().getId().equals(target.category().getId())) {
                return;
            }

            GridDropLocation location = event.getDropLocation();
            if (location != GridDropLocation.ABOVE && location != GridDropLocation.BELOW) {
                return;
            }

            reorderRootCategories(draggedNode.category(), target.category(), location);
            draggedNode = null;
        });
    }

    private void reorderRootCategories(Category draggedCategory, Category targetCategory, GridDropLocation location) {
        List<Category> rootCategories = new ArrayList<>(categoryService.findRootCategories());
        int fromIndex = indexOfCategory(rootCategories, draggedCategory);
        int targetIndex = indexOfCategory(rootCategories, targetCategory);
        if (fromIndex < 0 || targetIndex < 0) {
            return;
        }

        Category moved = rootCategories.remove(fromIndex);
        if (fromIndex < targetIndex) {
            targetIndex--;
        }
        if (location == GridDropLocation.BELOW) {
            targetIndex++;
        }

        if (targetIndex < 0) {
            targetIndex = 0;
        }
        if (targetIndex > rootCategories.size()) {
            targetIndex = rootCategories.size();
        }

        rootCategories.add(targetIndex, moved);

        categoryService.reorderRootCategories(rootCategories);

        currentCategory = moved;
        refreshArticleList();
        selectCategory(moved);
        Notification.show("Category order updated");
    }

    private int indexOfCategory(List<Category> categories, Category target) {
        if (target == null || target.getId() == null) {
            return -1;
        }
        for (int i = 0; i < categories.size(); i++) {
            Category category = categories.get(i);
            if (category.getId() != null && category.getId().equals(target.getId())) {
                return i;
            }
        }
        return -1;
    }

    private void addCategoryChildren(TreeData<WikiType> treeData, WikiType parentNode, Category category) {
        List<Article> articles = isAdmin ?
            articleService.findByCategory(category) :
            articleService.findByCategoryAndPublished(category);

        for (Article article : articles) {
            WikiType articleNode = WikiType.article(article);
            treeData.addItem(parentNode, articleNode);
            parentByNode.put(articleNode, parentNode);
            nodeBySlug.put(article.getSlug(), articleNode);
            quickJumpItems.add(articleNode);
        }

        for (Category child : category.getChildren()) {
            WikiType childCategoryNode = WikiType.category(child);
            treeData.addItem(parentNode, childCategoryNode);
            parentByNode.put(childCategoryNode, parentNode);
            quickJumpItems.add(childCategoryNode);
            categoryNodes.add(childCategoryNode);
            addCategoryChildren(treeData, childCategoryNode, child);
        }
    }

    private void handleQuickJump(WikiType item) {
        expandAncestors(item);
        if (item.type() == WikiNodeType.ARTICLE && item.article() != null) {
            articleTree.select(item);
            showArticle(item.article());
        } else if (item.type() == WikiNodeType.CATEGORY || item.type() == WikiNodeType.SECTION) {
            articleTree.expand(item);
            articleTree.select(item);
        }
        currentSearchQuery = "";
        menuSearch.clear();
    }

    private String extractSnippet(String content, String query) {
        if (content == null || query == null || query.isBlank()) return "";
        String plain = stripMarkdown(content);
        int idx = plain.toLowerCase().indexOf(query.toLowerCase());
        if (idx < 0) return "";
        int start = Math.max(0, idx - 40);
        int end = Math.min(plain.length(), idx + query.length() + 40);
        String snippet = plain.substring(start, end);
        if (start > 0) snippet = "…" + snippet;
        if (end < plain.length()) snippet = snippet + "…";
        return snippet;
    }

    private String stripMarkdown(String markdown) {
        if (markdown == null) return "";
        return markdown
            .replaceAll("(?s)```.*?```", " ")
            .replaceAll("`([^`]*)`", "$1")
            .replaceAll("!\\[.*?\\]\\(.*?\\)", "")
            .replaceAll("\\[([^\\]]+)\\]\\(.*?\\)", "$1")
            .replaceAll("(?m)^#{1,6}\\s+", "")
            .replaceAll("(\\*{1,3}|_{1,3})(.+?)\\1", "$2")
            .replaceAll("(?m)^>\\s+", "")
            .replaceAll("(?m)^[-*_]{3,}\\s*$", "")
            .replaceAll("(?m)^\\s*[-*+]\\s+", "")
            .replaceAll("(?m)^\\s*\\d+\\.\\s+", "")
            .replaceAll("\\n+", " ")
            .trim();
    }

    private void expandAncestors(WikiType item) {
        WikiType parent = parentByNode.get(item);
        while (parent != null) {
            articleTree.expand(parent);
            parent = parentByNode.get(parent);
        }
    }

    private void toggleAllCategories() {
        if (categoryNodes.isEmpty()) {
            return;
        }
        boolean allExpanded = categoryNodes.stream().allMatch(articleTree::isExpanded);
        if (allExpanded) {
            articleTree.collapse(categoryNodes);
        } else {
            articleTree.expand(categoryNodes);
        }
        updateCategoryToggleButton();
    }

    private void updateCategoryToggleButton() {
        if (categoryNodes.isEmpty()) {
            toggleCategoriesButton.setText("No categories");
            toggleCategoriesButton.setEnabled(false);
            toggleCategoriesButton.setIcon(VaadinIcon.FOLDER.create());
            return;
        }

        toggleCategoriesButton.setEnabled(true);
        boolean allExpanded = categoryNodes.stream().allMatch(articleTree::isExpanded);
        if (allExpanded) {
            toggleCategoriesButton.setText("Collapse all");
            toggleCategoriesButton.setIcon(VaadinIcon.CARET_DOWN.create());
        } else {
            toggleCategoriesButton.setText("Expand all");
            toggleCategoriesButton.setIcon(VaadinIcon.CARET_RIGHT.create());
        }
    }

    private void renderMetaRow(Article article) {
        String createdAt = article.getCreatedAt() != null
            ? article.getCreatedAt().format(EXPORT_DATE_FORMAT)
            : "Unknown";
        String updatedBy = formatUserDisplay(article.getUpdatedBy());
        String updatedAt = article.getUpdatedAt() != null
            ? article.getUpdatedAt().format(METADATA_DATE_FORMAT)
            : "Unknown";
        ArticleStatus status = article.getStatus() != null ? article.getStatus() : ArticleStatus.DRAFT;
        String statusBadgeClass = status == ArticleStatus.PUBLISHED
            ? "status-badge status-badge-success"
            : "status-badge status-badge-neutral";
        String statusLabel = status == ArticleStatus.PUBLISHED ? "Published" : "Draft";

        String html = "<span class=\"" + statusBadgeClass + "\">" + escapeHtml(statusLabel) + "</span>"
            + "<span class=\"kb-meta-dot\">·</span>"
            + "<span>Updated " + escapeHtml(updatedAt) + " by " + escapeHtml(updatedBy) + "</span>"
            + "<span class=\"kb-meta-dot\">·</span>"
            + "<span>Created " + escapeHtml(createdAt) + "</span>";
        metadataDisplay.getElement().setProperty("innerHTML", html);
    }

    private String formatUserDisplay(io.avec.data.User user) {
        if (user == null) {
            return "Unknown";
        }
        if (user.getName() != null && !user.getName().isBlank()) {
            return user.getName();
        }
        if (user.getUsername() != null && !user.getUsername().isBlank()) {
            return user.getUsername();
        }
        return "Unknown";
    }

    private String escapeHtml(String input) {
        if (input == null) {
            return "";
        }
        return input
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;");
    }

    private boolean isCreateCategoryOption(Category category) {
        return category != null && CREATE_CATEGORY_SLUG.equals(category.getSlug());
    }

    private String categoryOptionLabel(Category category) {
        return isCreateCategoryOption(category) ? "Create" : (category != null ? category.getName() : "");
    }

    private enum WikiNodeType {
        WELCOME,
        CATEGORY,
        ARTICLE,
        SECTION
    }

    private record WikiType(
        WikiNodeType type,
        String label,
        Category category,
        Article article
    ) {
        private static WikiType welcome(String label, String slug) {
            Article welcomeArticle = new Article();
            welcomeArticle.setSlug(slug);
            return new WikiType(WikiNodeType.WELCOME, label, null, welcomeArticle);
        }

        private static WikiType category(Category category) {
            return new WikiType(WikiNodeType.CATEGORY, category.getName(), category, null);
        }

        private static WikiType article(Article article) {
            return new WikiType(WikiNodeType.ARTICLE, article.getTitle(), null, article);
        }

        private static WikiType section(String label) {
            return new WikiType(WikiNodeType.SECTION, label, null, null);
        }
    }
}
