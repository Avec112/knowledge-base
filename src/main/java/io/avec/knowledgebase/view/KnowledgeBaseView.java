package io.avec.knowledgebase.view;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.treegrid.TreeGrid;
import com.vaadin.flow.data.provider.hierarchy.TreeData;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.router.*;
import io.avec.data.Role;
import io.avec.knowledgebase.data.Article;
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
    private final TextArea contentArea = new TextArea("Content (Markdown)");
    private final Markdown markdownPreview = new Markdown("");
    private final H2 titleDisplay = new H2();
    private final ComboBox<WikiType> menuSearch = new ComboBox<>();
    private final Map<String, WikiType> nodeBySlug = new HashMap<>();
    private final Map<WikiType, WikiType> parentByNode = new HashMap<>();
    private final List<WikiType> quickJumpItems = new ArrayList<>();
    private WikiType welcomeNode;

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
    private static final String WELCOME_SLUG = "welcome-to-knowledge";

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
        TreeData<WikiType> treeData = new TreeData<>();

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
        buttonBar.setJustifyContentMode(JustifyContentMode.END);
        buttonBar.setPadding(true);
        buttonBar.setSpacing(true);
        buttonBar.addClassName("kb-button-bar");
        buttonBar.getStyle()
            .set("padding", "2px var(--lumo-space-m)")
        ;

        createButton.setIcon(VaadinIcon.PLUS.create());
        createButton.addClickListener(e -> createNewArticle());
        createButton.setVisible(false);

        editButton.setIcon(VaadinIcon.EDIT.create());
        editButton.addClickListener(e -> enableEditMode());
        editButton.setVisible(false);

        previewButton.setIcon(VaadinIcon.EYE.create());
        previewButton.addClickListener(e -> togglePreview());
        previewButton.setVisible(false);

        saveButton.setIcon(VaadinIcon.CHECK.create());
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        saveButton.addClickListener(e -> saveArticle());
        saveButton.setVisible(false);

        cancelButton.setIcon(VaadinIcon.CLOSE.create());
        cancelButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        cancelButton.addClickListener(e -> cancelEdit());
        cancelButton.setVisible(false);

        deleteButton.setIcon(VaadinIcon.TRASH.create());
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

        // Category field (edit mode)
        categoryField.setWidthFull();
        categoryField.setItems(categoryService.findAll());
        categoryField.setItemLabelGenerator(Category::getName);
        categoryField.setPlaceholder("Select a category (optional)");
        categoryField.setClearButtonVisible(true);
        categoryField.setVisible(false);

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

        panel.add(titleDisplay, titleField, categoryField, contentArea, markdownPreview);
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
        categoryField.setVisible(editMode);
        contentArea.setVisible(editMode);
        markdownPreview.setVisible(!editMode);

        if (hasArticle) {
            // Update title and content for articles
            if (editMode) {
                titleField.setValue(currentArticle.getTitle() != null ? currentArticle.getTitle() : "");
                categoryField.setValue(currentArticle.getCategory());
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
        menuColumn.setHeader(menuSearch);
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

        articleTree.setPartNameGenerator(node -> node.type() == WikiNodeType.CATEGORY ? "category" : null);
        articleTree.setAllRowsVisible(true);
        articleTree.addExpandListener(event -> articleTree.getDataProvider().refreshAll());
        articleTree.addCollapseListener(event -> articleTree.getDataProvider().refreshAll());
        articleTree.addSelectionListener(event -> {
            event.getFirstSelectedItem().ifPresent(node -> {
                if (node.type() == WikiNodeType.WELCOME) {
                    if (currentArticle == null) {
                        return;
                    }
                    getUI().ifPresent(ui -> ui.navigate("knowledge/" + WELCOME_SLUG));
                } else if (node.type() == WikiNodeType.ARTICLE && node.article() != null) {
                    if (currentArticle != null && currentArticle.getId() != null
                        && currentArticle.getId().equals(node.article().getId())) {
                        return;
                    }
                    showArticle(node.article());
                } else if (node.type() == WikiNodeType.CATEGORY || node.type() == WikiNodeType.SECTION) {
                    articleTree.expand(node);
                }
            });
        });
        articleTree.addItemClickListener(event -> {
            WikiType node = event.getItem();
            if (node.type() == WikiNodeType.CATEGORY || node.type() == WikiNodeType.SECTION) {
                if (articleTree.isExpanded(node)) {
                    articleTree.collapse(node);
                } else {
                    articleTree.expand(node);
                }
            }
        });
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
