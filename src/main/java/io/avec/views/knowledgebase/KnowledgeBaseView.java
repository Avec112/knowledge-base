package io.avec.views.knowledgebase;

import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.listbox.MultiSelectListBox;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.FlexComponent.JustifyContentMode;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility.Gap;
import jakarta.annotation.security.PermitAll;
import java.util.ArrayList;
import java.util.List;
import org.vaadin.lineawesome.LineAwesomeIconUrl;

@PageTitle("KnowledgeBase")
@Route("")
@Menu(order = 0, icon = LineAwesomeIconUrl.GRADUATION_CAP_SOLID)
@PermitAll
public class KnowledgeBaseView extends Composite<VerticalLayout> {

    public KnowledgeBaseView() {
        HorizontalLayout layoutRow = new HorizontalLayout();
        VerticalLayout layoutColumn2 = new VerticalLayout();
        VerticalLayout layoutColumn3 = new VerticalLayout();
        Button buttonPrimary = new Button();
        MultiSelectListBox textItems = new MultiSelectListBox();
        VerticalLayout layoutColumn4 = new VerticalLayout();
        VerticalLayout layoutColumn5 = new VerticalLayout();
        Button buttonPrimary2 = new Button();
        TextField textField = new TextField();
        TextArea textArea = new TextArea();
        HorizontalLayout layoutRow2 = new HorizontalLayout();
        Button buttonPrimary3 = new Button();
        Button buttonSecondary = new Button();
        getContent().setWidth("100%");
        getContent().getStyle().set("flex-grow", "1");
        layoutRow.addClassName(Gap.MEDIUM);
        layoutRow.setWidth("100%");
        layoutRow.getStyle().set("flex-grow", "1");
        layoutColumn2.getStyle().set("flex-grow", "1");
        layoutColumn3.setWidthFull();
        layoutColumn2.setFlexGrow(1.0, layoutColumn3);
        layoutColumn3.setWidth("100%");
        layoutColumn3.getStyle().set("flex-grow", "1");
        buttonPrimary.setText("New");
        buttonPrimary.setWidth("min-content");
        buttonPrimary.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        textItems.setWidth("min-content");
        setMultiSelectListBoxSampleData(textItems);
        layoutColumn4.setWidth("100%");
        layoutColumn4.getStyle().set("flex-grow", "1");
        layoutColumn5.setWidthFull();
        layoutColumn4.setFlexGrow(1.0, layoutColumn5);
        layoutColumn5.setWidth("100%");
        layoutColumn5.getStyle().set("flex-grow", "1");
        buttonPrimary2.setText("Edit");
        layoutColumn5.setAlignSelf(FlexComponent.Alignment.END, buttonPrimary2);
        buttonPrimary2.setWidth("min-content");
        buttonPrimary2.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        textField.setLabel("Title");
        textField.setWidth("100%");
        textArea.setLabel("Content");
        textArea.setWidth("100%");
        textArea.getStyle().set("flex-grow", "1");
        layoutRow2.setWidthFull();
        layoutColumn5.setFlexGrow(1.0, layoutRow2);
        layoutRow2.addClassName(Gap.MEDIUM);
        layoutRow2.setWidth("100%");
        layoutRow2.setHeight("min-content");
        layoutRow2.setAlignItems(Alignment.START);
        layoutRow2.setJustifyContentMode(JustifyContentMode.END);
        buttonPrimary3.setText("Save");
        layoutRow2.setAlignSelf(FlexComponent.Alignment.START, buttonPrimary3);
        buttonPrimary3.setWidth("min-content");
        buttonPrimary3.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        buttonSecondary.setText("Preview");
        buttonSecondary.setWidth("min-content");
        getContent().add(layoutRow);
        layoutRow.add(layoutColumn2);
        layoutColumn2.add(layoutColumn3);
        layoutColumn3.add(buttonPrimary);
        layoutColumn3.add(textItems);
        layoutRow.add(layoutColumn4);
        layoutColumn4.add(layoutColumn5);
        layoutColumn5.add(buttonPrimary2);
        layoutColumn5.add(textField);
        layoutColumn5.add(textArea);
        layoutColumn5.add(layoutRow2);
        layoutRow2.add(buttonPrimary3);
        layoutRow2.add(buttonSecondary);
    }

    record SampleItem(String value, String label, Boolean disabled) {
    }

    private void setMultiSelectListBoxSampleData(MultiSelectListBox multiSelectListBox) {
        List<SampleItem> sampleItems = new ArrayList<>();
        sampleItems.add(new SampleItem("first", "First", null));
        sampleItems.add(new SampleItem("second", "Second", null));
        sampleItems.add(new SampleItem("third", "Third", Boolean.TRUE));
        sampleItems.add(new SampleItem("fourth", "Fourth", null));
        multiSelectListBox.setItems(sampleItems);
        multiSelectListBox.setItemLabelGenerator(item -> ((SampleItem) item).label());
        multiSelectListBox.setItemEnabledProvider(item -> !Boolean.TRUE.equals(((SampleItem) item).disabled()));
    }
}
