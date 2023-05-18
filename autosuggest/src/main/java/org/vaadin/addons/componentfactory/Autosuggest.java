package org.vaadin.addons.componentfactory;

/*
 * #%L
 * VCF Enhanced Combobox for Vaadin 14+
 * %%
 * Copyright (C) 2021 Vaadin Ltd
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import com.vaadin.flow.component.*;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.dependency.NpmPackage;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.dom.Element;
import com.vaadin.flow.dom.Style;
import com.vaadin.flow.dom.ThemeList;
import com.vaadin.flow.shared.Registration;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Server-side component for the <code>vcf-autosuggest</code> element.
 * <p>
 * Note: isOpened,setOpened and setValue are not supported. The current
 * implementation of the polymer-side component does not allow it.
 *
 * @author Vaadin Ltd
 */

@Tag("vcf-autosuggest")
@NpmPackage(value = "@vaadin/vaadin-element-mixin", version = "21.0.5")
@NpmPackage(value = "@vaadin-component-factory/vcf-autosuggest", version = "2.0.0")
@JsModule("@vaadin-component-factory/vcf-autosuggest/src/vcf-autosuggest.js")
//@JsModule("./vcf-autosuggest.js")
@CssImport(value = "@vaadin-component-factory/vcf-autosuggest/styles/style.css")
public class Autosuggest<T> extends Component
    implements HasTheme, HasSize, Focusable<Autosuggest<T>>, HasValidation {
    /**
     * This model binds properties {@link Autosuggest} and
     * vcf-autosuggest.html
     */
    private static class FOption {
        String key;
        String label;
        String searchStr;

        public FOption(String key, String label, String searchStr) {
            this.key = key;
            this.label = label;
            this.searchStr = searchStr;
        }

        public String getKey() { return this.key; }
        public void setKey(String key) { this.key = key; }
        public String getLabel() { return this.label; }
        public void setLabel(String label) { this.label = label; }
        public String getSearchStr() { return this.searchStr; }
        public void setSearchStr(String searchStr) { this.searchStr = searchStr; }

        public Map<String, Object> toMap() {
            Map<String, Object> result = new HashMap<>();
            result.put("key", key);
            result.put("label", label);
            result.put("searchStr", searchStr);
            return result;
        }
    }

    class Option extends FOption {
        T item;

        public Option(String key, String label, String searchStr,T item) {
            super(key, label, searchStr);
            this.item = item;
        }

        public T getItem() { return this.item; }
        public void setItem(T item) { this.item = item; }
    }

    public interface LazyProviderFunction<T> {}

    public interface LazyProviderFunctionSimple<T> extends LazyProviderFunction<T> {
        List<T> refresh(String searchQ);
    }

    public interface LazyProviderFunctionMap<T> extends LazyProviderFunction<T> {
        Map<String, T> refresh(String searchQ);
    }

    public interface KeyGenerator<T> {
        String generate(T obj);
    }

    public interface LabelGenerator<T> {
        String generate(T obj);
    }

    public interface SearchStringGenerator<T> {
        String generate(T obj);
    }

    private boolean showClearButton = true;
    public void setShowClearButton(Boolean v) { this.showClearButton = v; }

    private Map<String, Option> items = new HashMap<>();
    public Map<String, Option> getItems() { return this.items; }

    private Map<String, Option> itemsForWhenValueIsNull = new HashMap<>();
    public Map<String, Option> getItemsForWhenValueIsNull() { return this.itemsForWhenValueIsNull; }

//    @Id
    private TextField textField;
    public TextField getTextField() { return this.textField; }

    private KeyGenerator<T> keyGenerator = null;
    private LabelGenerator<T> labelGenerator = null;
    private SearchStringGenerator<T> searchStringGenerator = null;

//    @Id(value = "autosuggestOverlay")
    private Element overlay;

//    @Id(value = "dropdownEndSlot")
    private Element dropdownEndSlot;

    private FlexLayout inputPrefix;
    private FlexLayout inputSuffix;
    private Button clearButton;

    private Registration inputTextChangeEvent;
    private Registration selectionEvent;
    private Registration lazyDataRequestEventH;

    // properties
    private boolean openDropdownOnClick;
    private boolean caseSensitive;
    private Boolean lazy;
    private String label;
    private Boolean customizeOptionsForWhenValueIsNull;
    private Integer minimumInputLengthToPerformLazyQuery;
    private String placeholder;
    private int limit;
    private String searchMatchingMode;

    /**
     * Constructor that sets the maximum number of displayed options.
     *
     * @param limit maximum number of displayed options
     */
    public Autosuggest(int limit) {
        this();
        setLimit(limit);
    }

    public Autosuggest() {
        this(false);
    }

    /**
     * Default constructor.
     */
    public Autosuggest(boolean placeClearButtonFirst) {
        setMinimumInputLengthToPerformLazyQuery(0);

        textField.setSizeFull();
        textField.setValueChangeMode(ValueChangeMode.ON_CHANGE);

        // Init clear button
        initClearButton();

        // Init input prefix
        inputPrefix = new FlexLayout();
        inputPrefix.setAlignItems(FlexComponent.Alignment.CENTER);
        inputPrefix.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
        inputPrefix.getElement().setAttribute("slot", "prefix");
        textField.getElement().appendChild(inputPrefix.getElement());

        // Init input suffix
        inputSuffix = new FlexLayout();
        inputSuffix.setAlignItems(FlexComponent.Alignment.CENTER);
        inputSuffix.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
        FlexLayout inputSuffixContainer = placeClearButtonFirst ? new FlexLayout(clearButton, inputSuffix) : new FlexLayout(inputSuffix, clearButton);
        inputSuffixContainer.setAlignItems(FlexComponent.Alignment.CENTER);
        inputSuffixContainer.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
        inputSuffixContainer.getElement().setAttribute("slot", "suffix");
        textField.getElement().appendChild(inputSuffixContainer.getElement());

        overlay.getStyle().set("--x-no-results-msg", "'No results'");
        overlay.getStyle().set("--x-input-length-below-minimum-msg", "'Please keep typing to trigger search ...'");
    }

    /** Init clear button */
    private void initClearButton() {
        Icon clearIcon = new Icon("lumo:cross");
        clearIcon.getElement().getStyle().set("color", "var(--lumo-contrast-70pct)");
        clearButton = new Button(clearIcon, buttonClickEvent -> getElement().executeJs("this.clear()"));
        ThemeList themeList = clearButton.getElement().getThemeList();
        themeList.add("icon");
        themeList.add("tertiary");
        themeList.add("small");
        Style style = clearButton.getElement().getStyle();
        style.set("display", "none");
        style.set("font-size", "var(--lumo-icon-size-m)");
        style.set("padding", "0");
        clearButton.getElement().setAttribute("aria-label", "");
        clearButton.setId("button-clear");
        addValueChangeListener(valueChangeEvent -> {
            if(showClearButton && valueChangeEvent.value != null && !valueChangeEvent.value.isEmpty() && !isReadOnly()) {
                style.set("display", "block");
            } else {
                style.set("display", "none");
            }
        });
    }

    public void clear() {
        fireEvent(new ValueClearEvent(this, true));
    }

    public void setNoResultsMsg(String msg) {
        overlay.getStyle().set("--x-no-results-msg", "'" + msg + "'");
    }

    public void setInputPrefix(Component... components) {
        inputPrefix.removeAll();
        inputPrefix.add(components);
    }

    public void setInputSuffix(Component... components) {
        inputSuffix.removeAll();
        inputSuffix.add(components);
    }

    public void clearDropdownEndSlot() {
        dropdownEndSlot.removeAllChildren();
        dropdownEndSlot.getStyle().set("display", "none");
    }

    public void setComponentToDropdownEndSlot(Component component) {
        clearDropdownEndSlot();
        dropdownEndSlot.getStyle().set("display", "block");
        dropdownEndSlot.appendChild(component.getElement());
    }

    public boolean getShowClearButton() {
        return showClearButton;
    }

    public boolean getOpenDropdownOnClick() {
        return this.openDropdownOnClick;
    }

    public void setOpenDropdownOnClick(boolean v) {
        this.openDropdownOnClick = v;
        this.getElement().setProperty("openDropdownOnClick", v);
    }

    public void setLoading(boolean loading) {
        this.getElement().setProperty("loading", loading);
        getElement().executeJs("this._loadingChanged(" + loading + ")");
    }

    public Boolean isCaseSensitive() {
        return this.caseSensitive;
    }

    public void setCaseSensitive(boolean v) {
        this.caseSensitive = v;
        getElement().setProperty("caseSensitive", v);
    }

    public Boolean isLazy() {
        return this.lazy;
    }

    public void setLazy(boolean lazy) {
        textField.setValueChangeMode(lazy ? ValueChangeMode.LAZY : ValueChangeMode.ON_CHANGE);
        this.lazy = lazy;
        this.getElement().setProperty("lazy", this.lazy);
        if(inputTextChangeEvent!=null) inputTextChangeEvent.remove();
        if(selectionEvent!=null) selectionEvent.remove();
        if (lazy) {
            inputTextChangeEvent = addInputChangeListener(valueChangeEvent -> {
                if (!valueChangeEvent.isFromClient()) {
                    if (valueChangeEvent.getValue() == null || valueChangeEvent.getValue().toString().isEmpty())
                        getElement().executeJs("this.clear();");
                    setLoading(false);
                    return;
                }

                if ((valueChangeEvent.getValue() == null) ||
                    (valueChangeEvent.getValue().toString().isEmpty()) ||
                    (getItemForLabel(valueChangeEvent.getValue().toString()).isPresent()))
                {
                    setLoading(false);
                    return;
                }

                if (valueChangeEvent.getValue().toString().trim().length() >= getMinimumInputLengthToPerformLazyQuery())
                    getEventBus().fireEvent(new AutosuggestLazyDataRequestEvent(this, true, valueChangeEvent
                        .getValue()
                        .toString()));
            });
            selectionEvent = addValueAppliedListener(autosuggestValueAppliedEvent -> textField.setValue(autosuggestValueAppliedEvent.getLabel()));
        }
    }

    public SearchMatchingMode getSearchMatchingMode() {
        return SearchMatchingMode.valueOf(this.searchMatchingMode);
    }

    public void setSearchMatchingMode(SearchMatchingMode smm) {
        this.searchMatchingMode = smm.toString();
        getElement().setProperty("searchMatchingMode", searchMatchingMode);
    }

    public Integer getMinimumInputLengthToPerformLazyQuery() {
        return this.minimumInputLengthToPerformLazyQuery;
    }

    public void setMinimumInputLengthToPerformLazyQuery(Integer minLength) {
        this.minimumInputLengthToPerformLazyQuery = minLength;
        this.getElement().setProperty("minimumInputLengthToPerformLazyQuery", this.minimumInputLengthToPerformLazyQuery);
    }

    public void setInputLengthBelowMinimumMsg(String msg) {
        overlay.getStyle().set("--x-input-length-below-minimum-msg", "'" + msg + "'");
    }

    /**
     * Gets the placeholder.
     * <p>
     * A placeholder string in addition to the label.
     * <p>
     * This property is not synchronized automatically from the client side, so
     * the returned value may not be the same as in client side.
     *
     * @return the {@code placeholder} property from the webcomponent
     */
    public String getPlaceholder() {
        return this.placeholder;
    }

    /**
     * Sets the placeholder.
     * <p>
     * Description copied from corresponding location in WebComponent:
     * <p>
     * A placeholder string in addition to the label.
     *
     * @param placeholder
     *            the String value to set
     */
    public void setPlaceholder(String placeholder) {
        this.placeholder = placeholder;
        this.getElement().setProperty("placeholder", this.placeholder);
    }

    public void setReadOnly(boolean readOnly) {
        this.getElement().setProperty("readOnly", readOnly);
        if(showClearButton && getValueKey() != null && !getValueKey().isEmpty() && !readOnly) {
            clearButton.getElement().getStyle().set("display", "block");
        } else {
            clearButton.getElement().getStyle().set("display", "none");
        }
        textField.setReadOnly(readOnly);
    }

    /**
     * Gets the the maximum number of displayed options.
     *
     * @return limit maximum number of displayed options. null is returned if
     *         there is not a limit set.
     */
    public int getLimit() {
        return this.limit;
    }

    /**
     * Sets the limit to the maximum number of displayed options.
     * <p>
     * The limit should be bigger than 0.
     *
     * @param limit maximum number of displayed options
     */
    public void setLimit(int limit) {
        this.limit = limit;
        this.getElement().setProperty("limit", this.limit);
    }

    @Synchronize(property = "inputValue", value = "vcf-autosuggest-input-value-changed")
    public String getInputValue() {
        return getElement().getProperty("inputValue", null);
    }

    @Synchronize(property = "selectedValue", value = "vcf-autosuggest-value-applied")
    public String getValueKey() {
        return getElement().getProperty("selectedValue", null);
    }

    public T getValue() {
        String key = getElement().getProperty("selectedValue", null);
        if( this.items.containsKey(key) ) return this.items.get(key).item;
        return null;
    }

    public void setValueByKey(String value) {
        if(!this.items.containsKey(value)) throw new IllegalArgumentException("No item found with key " + value);
        applyValue(value);
    }

    public void setValueByLabel(String label) {
        Option option = getItemForLabel(label).orElseThrow(() -> new IllegalArgumentException("No item found with key " + label));
        applyValue(option.getKey());
    }

    public void setValue(T item) {
        if(item == null || !contains(item)) {
            getElement().executeJs("this._applyValue(null);");
        } else {
            setValueByKey(getKey(item));
        }
    }

    public boolean contains(T item) {
        return this.items.containsKey(getKey(item));
    }

    private void applyValue(String value) {
        Element element = getElement();
        element.getNode().runWhenAttached(ui -> ui.beforeClientResponse(this,
                context -> element.executeJs("setTimeout(function() { $0._applyValue(\"" + value + "\"); }, 0);", element))
        );
    }

    private Optional<Option> getItemForLabel(String label) {
        return this.items.values().stream().filter(item -> item.getLabel().equals(label)).findFirst();
    }

    private String getKey(T item) {
        return keyGenerator != null ? keyGenerator.generate(item) : item.toString();
    }

    private String getLabel(T item) {
        return labelGenerator != null ? labelGenerator.generate(item) : item.toString();
    }

    private String getSearchStr(T item, String defValue) {
        return searchStringGenerator != null ? searchStringGenerator.generate(item) : defValue;
    }

    /**
     * Gets the label.
     * <p>
     * String used for the label element.
     * <p>
     * This property is not synchronized automatically from the client side, so
     * the returned value may not be the same as in client side.
     *
     * @return the {@code label} property from the webcomponent
     */
    public String getLabel() {
        return this.label;
    }

    /**
     * Sets the label.
     * <p>
     * String used for the label element.
     *
     * @param label
     *            the String value to set
     */
    public void setLabel(String label) {
        this.label = label == null ? "" : label;
        this.getElement().setProperty("label", this.label);
    }

    public Boolean getCustomizeItemsForWhenValueIsNull() {
        return this.customizeOptionsForWhenValueIsNull;
    }

    public void setCustomizeItemsForWhenValueIsNull(boolean v) {
        this.customizeOptionsForWhenValueIsNull = v;
        this.getElement().setProperty("customizeOptionsForWhenValueIsNull", this.customizeOptionsForWhenValueIsNull);
    }

    public Registration addEagerInputChangeListener(ComponentEventListener<EagerInputChangeEvent> listener) {
        return addListener(EagerInputChangeEvent.class, listener);
    }

    public Registration addCustomValueSubmitListener(ComponentEventListener<CustomValueSubmitEvent> listener) {
        return addListener(CustomValueSubmitEvent.class, listener);
    }

    public Registration addInputChangeListener(HasValue.ValueChangeListener listener) {
        return textField.addValueChangeListener(listener);
    }

    public Registration addLazyDataRequestListener(ComponentEventListener<AutosuggestLazyDataRequestEvent> listener) {
        return getEventBus().addListener(AutosuggestLazyDataRequestEvent.class, listener);
    }

    /**
     * Adds a listener for {@code AutosuggestValueAppliedEvent} events fired by
     * the webcomponent.
     *
     * @param listener
     *            the listener
     * @return a {@link Registration} for removing the event listener
     */
    public Registration addValueChangeListener(
        ComponentEventListener<AutosuggestValueAppliedEvent> listener) {
        return addListener(AutosuggestValueAppliedEvent.class, listener);
    }

    public void clearDefaultOptionValue() {
        this.getElement().setProperty("defaultOption", null);
    }

    public void setDefaultOption(String key, String label, String searchStr) {
        FOption defaultOption = new FOption(key, label, searchStr);
        this.getElement().setPropertyMap("defaultOption", defaultOption.toMap());
    }

    public void setDefaultOption(String label) {
        FOption defaultOption = new FOption(label, label, label);
        this.getElement().setPropertyMap("defaultOption", defaultOption.toMap());
    }

    /**
     * Adds a listener for {@code ValueClearEvent}.
     *
     * @param listener
     *            the listener
     * @return a {@link Registration} for removing the event listener
     */
    public Registration addValueClearListener(
        ComponentEventListener<ValueClearEvent> listener) {
        return addListener(ValueClearEvent.class, listener);
    }

    public void setLazyProviderSimple(LazyProviderFunctionSimple<T> ff) {
        if(lazyDataRequestEventH!=null) lazyDataRequestEventH.remove();
        lazyDataRequestEventH = addLazyDataRequestListener(event -> setItems(ff.refresh(getInputValue())));
    }

    public void setLazyProviderMap(LazyProviderFunctionMap<T> ff) {
        if(lazyDataRequestEventH!=null) lazyDataRequestEventH.remove();
        lazyDataRequestEventH = addLazyDataRequestListener(event -> setItems(ff.refresh(getInputValue())));
    }

    public void setKeyGenerator(KeyGenerator<T> keyG) {
        this.keyGenerator = keyG;
        this.setItems();
    }

    public void unsetKeyGenerator() {
        this.keyGenerator = null;
        this.setItems();
    }

    public void setLabelGenerator(LabelGenerator<T> lblG) {
        this.labelGenerator = lblG;
        this.setItems();
    }

    public void unsetLabelGenerator() {
        this.labelGenerator = null;
        this.setItems();
    }

    public void clearSearchStringGenerator() {
        this.searchStringGenerator = null;
        this.getElement().setProperty("disableSearchHighlighting", false);
        this.setItems();
    }

    public void setSearchStringGenerator(SearchStringGenerator<T> searchStringGenerator) {
        this.searchStringGenerator = searchStringGenerator;
        this.getElement().setProperty("disableSearchHighlighting", true);
        this.setItems();
    }

    public void clearItemsForWhenValueIsNull() {
        this.customizeOptionsForWhenValueIsNull = false;
        this.getElement().setProperty("customizeOptionsForWhenValueIsNull", this.customizeOptionsForWhenValueIsNull);
        this.itemsForWhenValueIsNull = new HashMap<>();
        setOptionsForWhenValueIsNull(new ArrayList<>());
    }

    public void setItemsForWhenValueIsNull(Collection<T> items) {
        this.itemsForWhenValueIsNull.clear();
        this.itemsForWhenValueIsNull.putAll(items.stream().collect(Collectors.toMap(this::getKey, this::getOption)));

        this.customizeOptionsForWhenValueIsNull = true;
        this.getElement().setProperty("customizeOptionsForWhenValueIsNull", this.customizeOptionsForWhenValueIsNull);
        setOptionsForWhenValueIsNull(new ArrayList<>(this.itemsForWhenValueIsNull.values()));
    }

    public void setItemsForWhenValueIsNull(Map<String, T> items) {
        this.itemsForWhenValueIsNull.clear();
        this.items.putAll(
                items.keySet().stream().collect(Collectors.toMap(key -> key, key -> getOption(items.get(key))))
        );

        this.customizeOptionsForWhenValueIsNull = true;
        this.getElement().setProperty("CustomizeOptionsForWhenValueIsNull", this.customizeOptionsForWhenValueIsNull);
        setOptionsForWhenValueIsNull(new ArrayList<>(this.itemsForWhenValueIsNull.values()));
    }

    private void setOptionsForWhenValueIsNull(List<FOption> options) {
        List<Map<String, Object>> optionsAsMaps = options.stream().map(FOption::toMap).collect(Collectors.toList());
        getElement().setPropertyList("optionsForWhenValueIsNull", optionsAsMaps);
    }

    public void clearOptionTemplate() {
        this.getElement().setProperty("customItemTemplate", null);
    }

    public void setOptionTemplate(String template) { //Available to replace: ${domItem}, ${option}
        String generator = "function(option, domItem) { " +
            "return `" + template + "`;" +
        "}";

        this.getElement().setProperty("customItemTemplate", generator);
    }

    private void setItems() {
        this.setItems(this.items.values().stream().map(Option::getItem).collect(Collectors.toList()));
    }

    public void setItems(Collection<T> items) {
        clearItems();
        this.items.putAll(items.stream().collect(Collectors.toMap(this::getKey, this::getOption)));
        setOptions(new ArrayList<>(this.items.values()));
        getElement().executeJs("this._refreshOptionsToDisplay(this.options, this.inputValue)");
        setLoading(false);
    }

    public void setItems(Map<String, T> items) {
        clearItems();
        this.items.putAll(
                items.keySet().stream().collect(Collectors.toMap(key -> key, key -> getOption(items.get(key))))
        );
        setOptions(new ArrayList<>(this.items.values()));
        getElement().executeJs("this._refreshOptionsToDisplay(this.options, this.inputValue)");
        setLoading(false);
    }

    private void setOptions(List<FOption> options) {
        List<Map<String, Object>> optionsAsMaps = options.stream().map(FOption::toMap).collect(Collectors.toList());
        getElement().setPropertyList("options", optionsAsMaps);
    }

    private void clearItems() {
        this.items.clear();
    }

    private Option getOption(T item) {
        String key = getKey(item);
        String label = getLabel(item);
        String searchStr = getSearchStr(item, label);

        return new Option(key, label, searchStr, item);
    }

    /**
     * ValueClearEvent is created when the user clicks on the clean button.
     */
    @DomEvent("clear")
    public static class ValueClearEvent extends ComponentEvent<Autosuggest> {
        public ValueClearEvent(Autosuggest source, boolean fromClient) {
            super(source, fromClient);
        }
    }

    public boolean isReadOnly() {
        return textField.isReadOnly();
    }

    public void setRequiredIndicatorVisible(boolean requiredIndicatorVisible) {
        textField.setRequiredIndicatorVisible(requiredIndicatorVisible);
    }

    public boolean isRequiredIndicatorVisible() {
        return textField.isRequiredIndicatorVisible();
    }

    public Registration addValueAppliedListener(ComponentEventListener<AutosuggestValueAppliedEvent> listener) {
        return addListener(AutosuggestValueAppliedEvent.class, listener);
    }

    @Override
    public String getErrorMessage() {
        if (textField != null) {
            return textField.getErrorMessage();
        } else {
            return null;
        }
    }

    @Override
    public boolean isInvalid() {
        if (textField != null) {
            return textField.isInvalid();
        } else {
            return false;
        }
    }

    @Override
    public void setErrorMessage(String errorMessage) {
        if (textField != null) {
            textField.setErrorMessage(errorMessage);
        }
    }

    @Override
    public void setInvalid(boolean invalid) {
        if (textField != null) {
            textField.setInvalid(invalid);
        }
    }

    @Override
    public void focus() {
        if (textField != null) {
            textField.focus();
        }
    }

    @Override
    public void blur() {
        if (textField != null) {
            textField.blur();
        }
    }

    @Override
    public void setTabIndex(int tabIndex) {
        if (textField != null) {
            textField.setTabIndex(tabIndex);
        }
    }

    @Override
    public int getTabIndex() {
        return textField.getTabIndex();
    }

    @Override
    public ShortcutRegistration addFocusShortcut(Key key,  KeyModifier... keyModifiers) {
        return textField.addFocusShortcut(key, keyModifiers);
    }

    /**
     * EagerInputChangeEvent is created when the value of the TextField changes.
     */
    @DomEvent("vcf-autosuggest-input-value-changed")
    public static class EagerInputChangeEvent extends ComponentEvent<Autosuggest> {
        private final String value;

        public EagerInputChangeEvent(Autosuggest source, boolean fromClient, @EventData("event.detail.value") String value) {
            super(source, fromClient);
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    /**
     * CustomValueSubmitEvent is created when the enter key is pressed for an option that's not in the list, returning the current value of the TextField
     */
    @DomEvent("vcf-autosuggest-custom-value-submit")
    public static class CustomValueSubmitEvent extends ComponentEvent<Autosuggest> {
        private final String value;
        private final Integer numberOfAvailableOptions;

        public CustomValueSubmitEvent(Autosuggest source, boolean fromClient, @EventData("event.detail.value") String value, @EventData("event.detail.numberOfAvailableOptions") Integer numberOfAvailableOptions) {
            super(source, fromClient);
            this.value = value;
            this.numberOfAvailableOptions = numberOfAvailableOptions;
        }

        public String getValue() { return value; }
        public Integer getNumberOfAvailableOptions() { return numberOfAvailableOptions; }
    }

    /**
     * AutosuggestValueAppliedEvent is created when the user clicks on a option
     * of the Autosuggest.
     */
    @DomEvent("vcf-autosuggest-value-applied")
    public static class AutosuggestValueAppliedEvent extends ComponentEvent<Autosuggest> {

        private final String label;
        private final String value;

        public AutosuggestValueAppliedEvent(Autosuggest source, boolean fromClient, @EventData("event.detail.value") String value, @EventData("event.detail.label") String label) {
            super(source, fromClient);
            this.value = value;
            this.source = source;
            this.label = label;
        }

        public String getValue() {
            return value;
        }
        public String getLabel() {
            return label;
        }
    }

    //@DomEvent("vcf-autosuggest-lazy-data-request")
    public static class AutosuggestLazyDataRequestEvent extends ComponentEvent<Autosuggest> {

        private final String value;

        public AutosuggestLazyDataRequestEvent(Autosuggest source, boolean fromClient, @EventData("event.detail.value") String value) {
            super(source, fromClient);
            this.value = value;
            this.source = source;
        }

        public String getValue() {
            return value;
        }
    }

    public enum SearchMatchingMode { STARTS_WITH, CONTAINS }
}
