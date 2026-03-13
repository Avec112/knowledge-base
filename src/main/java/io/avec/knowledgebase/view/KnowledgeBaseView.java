package io.avec.knowledgebase.view;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
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
import com.vaadin.flow.component.grid.dnd.GridDropLocation;
import com.vaadin.flow.component.grid.dnd.GridDropMode;
import com.vaadin.flow.data.provider.hierarchy.TreeData;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.router.*;
import io.avec.data.Role;
import io.avec.knowledgebase.data.Article;
import io.avec.knowledgebase.data.ArticleStatus;
import io.avec.knowledgebase.data.Category;
import io.avec.knowledgebase.service.ArticleService;
import io.avec.knowledgebase.service.CategoryService;
import io.avec.security.AuthenticatedUser;
import io.avec.views.MainLayout;
import jakarta.annotation.security.PermitAll;
import org.vaadin.lineawesome.LineAwesomeIconUrl;
import com.vaadin.flow.component.markdown.Markdown;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.function.Consumer;

import java.util.Optional;

@PageTitle("KnowledgeBase")
@Route(value = "knowledge", layout = MainLayout.class)
@Menu(order = 0, icon = LineAwesomeIconUrl.GRADUATION_CAP_SOLID)
@PermitAll
public class KnowledgeBaseView extends VerticalLayout implements HasUrlParameter<String> {

    private final ArticleService articleService;
    private final CategoryService categoryService;
    private final AuthenticatedUser authenticatedUser;

    private final TreeGrid<WikiType> articleTree = new TreeGrid<>();
    private final TextField titleField = new TextField("Title");
    private final ComboBox<Category> categoryField = new ComboBox<>("Category");
    private final ComboBox<ArticleStatus> statusField = new ComboBox<>("Status");
    private final TextArea contentArea = new TextArea("Content (Markdown)");
    private final Markdown markdownPreview = new Markdown("");
    private final H2 titleDisplay = new H2();
    private final ComboBox<WikiType> menuSearch = new ComboBox<>();
    private final Button toggleCategoriesButton = new Button();
    private final Map<String, WikiType> nodeBySlug = new HashMap<>();
    private final Map<WikiType, WikiType> parentByNode = new HashMap<>();
    private final List<WikiType> quickJumpItems = new ArrayList<>();
    private final List<WikiType> categoryNodes = new ArrayList<>();
    private WikiType welcomeNode;
    private WikiType draggedNode;

    private final Button createButton = new Button("Create");
    private final Button createCategoryButton = new Button("Create");
    private final Button editButton = new Button("Edit");
    private final Button editCategoryButton = new Button("Edit");
    private final Button previewButton = new Button("Preview");
    private final Button saveButton = new Button("Save");
    private final Button cancelButton = new Button("Cancel");
    private final Button deleteButton = new Button("Delete");
    private final Button deleteCategoryButton = new Button("Delete");

    private Article currentArticle;
    private Category currentCategory;
    private boolean editMode = false;
    private boolean previewMode = false;
    private boolean isAdmin = false;
    private static final String WELCOME_SLUG = "welcome-to-knowledge";
    private static final String CREATE_CATEGORY_SLUG = "__create_category__";

    public KnowledgeBaseView(ArticleService articleService, CategoryService categoryService, AuthenticatedUser authenticatedUser) {
        this.articleService = articleService;
        this.categoryService = categoryService;
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
            if (WELCOME_SLUG.equals(slug)) {
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

        configureArticleTree();
        articleTree.setWidthFull();
        articleTree.getStyle().set("flex", "0 1 clamp(220px, 26vw, 360px)");
        articleTree.setHeightFull();
        articleTree.addClassName("kb-sidebar");

        rightPanel.setSizeFull();
        rightPanel.addClassName("kb-content-area");

        layout.add(articleTree, rightPanel);

        return layout;
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
            List<Article> uncategorized = articleService.findUncategorized();
            if (!uncategorized.isEmpty()) {
                WikiType uncategorizedNode = WikiType.section("Ukategorisert");
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
            menuSearch.setItems(quickJumpItems);
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

    private HorizontalLayout createButtonBarLayout() {
        // Button bar
        HorizontalLayout buttonBar = new HorizontalLayout();
        buttonBar.setWidthFull();
        buttonBar.setJustifyContentMode(JustifyContentMode.BETWEEN);
        buttonBar.setPadding(true);
        buttonBar.setSpacing(true);
        buttonBar.addClassName("kb-button-bar");
        buttonBar.getStyle()
            .set("padding", "2px var(--lumo-space-m)")
        ;

        createButton.setIcon(VaadinIcon.FILE_O.create());
        createButton.addClickListener(e -> createNewArticle());
        createButton.setVisible(false);

        createCategoryButton.setIcon(VaadinIcon.FOLDER.create());
        createCategoryButton.addClickListener(e -> openCreateCategoryDialog());
        createCategoryButton.setVisible(false);

        editButton.setIcon(VaadinIcon.FILE_O.create());
        editButton.addClickListener(e -> enableEditMode());
        editButton.setVisible(false);

        editCategoryButton.setIcon(VaadinIcon.FOLDER.create());
        editCategoryButton.addClickListener(e -> openEditCategoryDialog());
        editCategoryButton.setVisible(false);

        previewButton.setIcon(VaadinIcon.FILE_O.create());
        previewButton.addClickListener(e -> togglePreview());
        previewButton.setVisible(false);

        saveButton.setIcon(VaadinIcon.FILE_O.create());
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        saveButton.addClickListener(e -> saveArticle());
        saveButton.setVisible(false);

        cancelButton.setIcon(VaadinIcon.FILE_O.create());
        cancelButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        cancelButton.addClickListener(e -> cancelEdit());
        cancelButton.setVisible(false);

        deleteButton.setIcon(VaadinIcon.FILE_O.create());
        deleteButton.addThemeVariants(ButtonVariant.LUMO_ERROR);
        deleteButton.addClickListener(e -> deleteArticle());
        deleteButton.setVisible(false);

        deleteCategoryButton.setIcon(VaadinIcon.FOLDER.create());
        deleteCategoryButton.addThemeVariants(ButtonVariant.LUMO_ERROR);
        deleteCategoryButton.addClickListener(e -> deleteCategory());
        deleteCategoryButton.setVisible(false);

        HorizontalLayout categoryButtons = new HorizontalLayout(createCategoryButton, editCategoryButton, deleteCategoryButton);
        categoryButtons.setSpacing(true);
        categoryButtons.setPadding(false);
        categoryButtons.setMargin(false);

        HorizontalLayout articleButtons = new HorizontalLayout(createButton, editButton, previewButton, saveButton, cancelButton, deleteButton);
        articleButtons.setSpacing(true);
        articleButtons.setPadding(false);
        articleButtons.setMargin(false);

        buttonBar.add(categoryButtons, articleButtons);
        return buttonBar;
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
        categoryField.setVisible(false);

        // Status field (edit mode)
        statusField.setWidthFull();
        statusField.setItems(ArticleStatus.DRAFT, ArticleStatus.PUBLISHED);
        statusField.setItemLabelGenerator(ArticleStatus::name);
        statusField.setVisible(false);

        // Content area (edit mode) - monospace font for Markdown editing
        contentArea.setWidthFull();
        contentArea.setHeight("400px");
        contentArea.setVisible(false);
        contentArea.getStyle()
            .set("font-family", "monospace")
            .set("font-size", "0.9em")
            .set("line-height", "1.5");

        // Markdown preview (read mode)
        markdownPreview.setWidthFull();
        markdownPreview.addClassName("wiki-content");

        panel.add(titleDisplay, titleField, categoryField, statusField, contentArea, markdownPreview);
//        panel.setFlexGrow(1, markdownPreview);

        return panel;
    }

    private void createNewArticle() {
        currentArticle = new Article();
        authenticatedUser.get().ifPresent(user -> currentArticle.setCreatedBy(user));
        currentArticle.setStatus(ArticleStatus.DRAFT);
        enableEditMode();
        titleField.clear();
        contentArea.clear();
        markdownPreview.setContent("");
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
        updateUI();
    }

    private void cancelEdit() {
        editMode = false;
        previewMode = false;
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
            category.setParent(null);
            if (category.getId() == null) {
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

        if (!articleService.findByCategory(currentCategory).isEmpty()) {
            Notification.show("Category has articles and cannot be deleted");
            return;
        }

        try {
            categoryService.delete(currentCategory);
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
        deleteButton.setVisible(hasId && isAdmin);
        deleteCategoryButton.setVisible(hasCategory && !editMode && isAdmin);

        // Update content visibility
        titleDisplay.setVisible(!editMode);
        titleField.setVisible(editMode);
        categoryField.setVisible(editMode);
        statusField.setVisible(editMode);
        contentArea.setVisible(editMode);
        markdownPreview.setVisible(!editMode);

        if (hasArticle) {
            // Update title and content for articles
            if (editMode) {
                titleField.setValue(currentArticle.getTitle() != null ? currentArticle.getTitle() : "");
                categoryField.setValue(currentArticle.getCategory());
                statusField.setValue(currentArticle.getStatus() != null ? currentArticle.getStatus() : ArticleStatus.DRAFT);
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
            previewButton.setIcon(VaadinIcon.EDIT.create());
        } else {
            // Back to edit mode
            previewButton.setText("Preview");
            previewButton.setIcon(VaadinIcon.EYE.create());
        }

        // Toggle visibility
        titleField.setVisible(!previewMode);
        categoryField.setVisible(!previewMode);
        statusField.setVisible(!previewMode);
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

    private void configureArticleTree() {
        var menuColumn = articleTree.addHierarchyColumn(WikiType::label)
            .setHeader((String) null)
            .setAutoWidth(true)
            .setFlexGrow(1);
        menuColumn.setSortable(false);
        menuColumn.setResizable(false);
        menuSearch.setPrefixComponent(VaadinIcon.SEARCH.create());
        menuSearch.setClearButtonVisible(true);
        menuSearch.setWidthFull();
        menuSearch.setItemLabelGenerator(WikiType::label);
        menuSearch.setAllowCustomValue(true);
        menuSearch.addValueChangeListener(event -> {
            if (event.getValue() != null) {
                handleQuickJump(event.getValue());
            }
        });
        menuSearch.addCustomValueSetListener(event -> {
            String query = event.getDetail() == null ? "" : event.getDetail().trim().toLowerCase();
            if (query.isEmpty()) {
                return;
            }
            quickJumpItems.stream()
                .filter(item -> item.label() != null && item.label().toLowerCase().contains(query))
                .findFirst()
                .ifPresent(this::handleQuickJump);
        });
        toggleCategoriesButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE, ButtonVariant.LUMO_SMALL);
        toggleCategoriesButton.addClickListener(event -> toggleAllCategories());

        VerticalLayout menuHeader = new VerticalLayout(menuSearch, toggleCategoriesButton);
        menuHeader.setPadding(false);
        menuHeader.setSpacing(false);
        menuHeader.setMargin(false);
        menuHeader.setAlignItems(FlexComponent.Alignment.START);
        menuHeader.setWidthFull();
        menuHeader.getStyle().set("gap", "var(--lumo-space-xs)");
        menuColumn.setHeader(menuHeader);
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
        articleTree.setAllRowsVisible(true);
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
        menuSearch.clear();
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
