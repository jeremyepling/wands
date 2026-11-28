package net.nicguzzo.wands.client.screens;

import me.shedaniel.math.Color;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/** Uses vanilla widgets with the same option definitions as the Cloth Config screen. */
final class NativeConfigScreen extends Screen {
    static final class ConfigBuilder {
        private final List<Category> categories = new ArrayList<>();
        private Component title;
        private Screen parent;
        private Runnable save;

        static ConfigBuilder create() { return new ConfigBuilder(); }
        ConfigBuilder setTitle(Component title) { this.title = title; return this; }
        ConfigBuilder setParentScreen(Screen parent) { this.parent = parent; return this; }
        void setSavingRunnable(Runnable save) { this.save = save; }
        Category getOrCreateCategory(Component name) {
            var category = new Category(name, new ArrayList<>());
            categories.add(category);
            return category;
        }
        Fields entryBuilder() { return new Fields(); }
        Screen build() { return new NativeConfigScreen(this); }
    }

    record Category(Component name, List<Field<?>> fields) {
        void addEntry(Field<?> field) { fields.add(field); }
    }

    static final class Fields {
        Field<Boolean> startBooleanToggle(Component name, boolean value) {
            return new Field<>(name, value, List.of(false, true), Boolean::valueOf);
        }
        <E extends Enum<E>> Field<E> startEnumSelector(Component name, Class<E> type, E value) {
            return new Field<>(name, value, List.of(type.getEnumConstants()), s -> Enum.valueOf(type, s));
        }
        Field<Integer> startIntField(Component name, int value) {
            return new Field<>(name, value, List.of(), Integer::valueOf);
        }
        Field<Float> startFloatField(Component name, float value) {
            return new Field<>(name, value, List.of(), Float::valueOf);
        }
        Field<String> startStrField(Component name, String value) {
            return new Field<>(name, value, List.of(), Function.identity());
        }
        Field<Integer> startAlphaColorField(Component name, Color value) {
            return new Field<>(name, value.getColor(), List.of(), Fields::parseArgb);
        }

        private static int parseArgb(String text) {
            String digits = text.startsWith("#") ? text.substring(1) : text;
            if (digits.length() != 8) throw new NumberFormatException();
            return Integer.parseUnsignedInt(digits, 16);
        }
    }

    static final class Field<T> {
        private final Component name;
        private final List<T> choices;
        private final Function<String, T> parse;
        private T value;
        private T initialDefault;
        private String draft;
        private Consumer<T> save;
        private Component tooltip;
        private boolean valid = true;
        private boolean argb;
        private double min = Double.NEGATIVE_INFINITY;
        private double max = Double.POSITIVE_INFINITY;

        Field(Component name, T value, List<T> choices, Function<String, T> parse) {
            this.name = name;
            this.value = value;
            this.choices = choices;
            this.parse = parse;
        }
        Field<T> setDefaultValue(T value) { initialDefault = value; return this; }
        Field<T> setSaveConsumer(Consumer<T> save) { this.save = save; return this; }
        Field<T> setTooltip(Component tooltip) { this.tooltip = tooltip; return this; }
        Field<T> setMin(int min) { this.min = min; return this; }
        Field<T> setMax(int max) { this.max = max; return this; }
        Field<T> setAlphaMode(boolean enabled) { argb = enabled; return this; }
        Field<T> build() { return this; }
        void saveValue() { save.accept(value); }

        private String text() {
            return argb ? String.format("#%08X", value) : value.toString();
        }
        private Component choiceLabel() {
            return value instanceof Boolean enabled ? (enabled ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF)
                    : Component.literal(value.toString());
        }
        void display(NativeConfigScreen screen, int x, int y, int width) {
            int editorWidth = Math.min(112, width / 3);
            int editorX = x + width - editorWidth - 44;
            var label = screen.addRenderableWidget(new StringWidget(x, y, editorX - x - 4, 20, name, screen.font));
            label.setMaxWidth(editorX - x - 4);
            if (tooltip != null) label.setTooltip(Tooltip.create(tooltip));
            if (choices.isEmpty()) {
                var box = new EditBox(screen.font, editorX, y, editorWidth, 20, name);
                box.setMaxLength(32767);
                box.setValue(draft == null ? text() : draft);
                box.setTextColor(valid ? 0xFFFFFFFF : 0xFFFF5555);
                box.setResponder(input -> {
                    draft = input;
                    try {
                        T parsed = parse.apply(input);
                        valid = !(parsed instanceof Number n) ||
                                (Double.isFinite(n.doubleValue()) && n.doubleValue() >= min && n.doubleValue() <= max);
                        if (valid) value = parsed;
                    } catch (NumberFormatException ex) {
                        valid = false;
                    }
                    box.setTextColor(valid ? 0xFFFFFFFF : 0xFFFF5555);
                    screen.updateSaveButton();
                });
                screen.addRenderableWidget(box);
            } else {
                screen.addRenderableWidget(Button.builder(choiceLabel(), button -> {
                    value = choices.get((choices.indexOf(value) + 1) % choices.size());
                    button.setMessage(choiceLabel());
                }).bounds(editorX, y, editorWidth, 20).build());
            }
            screen.addRenderableWidget(Button.builder(Component.translatable("controls.reset"), button -> {
                value = initialDefault;
                draft = null;
                valid = true;
                screen.rebuildWidgets();
            }).bounds(editorX + editorWidth + 2, y, 42, 20).build());
        }
    }

    private final ConfigBuilder config;
    private int categoryIndex;
    private int page;
    private Button saveButton;

    private NativeConfigScreen(ConfigBuilder config) {
        super(config.title);
        this.config = config;
    }

    @Override
    protected void init() {
        Category selected = config.categories.get(categoryIndex);
        int width = Math.min(this.width - 12, 460);
        int left = (this.width - width) / 2;
        int perPage = Math.max(1, (height - 87) / 22);
        int lastPage = Math.max(0, (selected.fields.size() - 1) / perPage);
        page = Math.min(page, lastPage);
        int tabWidth = width / config.categories.size();
        for (int index = 0; index < config.categories.size(); index++) {
            final int tab = index;
            Button button = addRenderableWidget(Button.builder(config.categories.get(tab).name, b -> {
                categoryIndex = tab;
                page = 0;
                rebuildWidgets();
            }).bounds(left + tab * tabWidth, 22, tabWidth - 2, 20).build());
            button.active = tab != categoryIndex;
        }
        for (int row = 0; row < perPage && page * perPage + row < selected.fields.size(); row++) {
            selected.fields.get(page * perPage + row).display(this, left, 46 + row * 22, width);
        }
        int y = height - 27;
        addRenderableWidget(Button.builder(Component.literal("<"), b -> {
            page--;
            rebuildWidgets();
        }).bounds(left, y, 24, 20).build()).active = page > 0;
        addRenderableWidget(Button.builder(Component.literal(">"), b -> {
            page++;
            rebuildWidgets();
        }).bounds(left + 26, y, 24, 20).build()).active = page < lastPage;
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose())
                .bounds(left + width - 132, y, 62, 20).build());
        saveButton = addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> {
            for (Category category : config.categories) {
                for (Field<?> field : category.fields) field.saveValue();
            }
            config.save.run();
            onClose();
        }).bounds(left + width - 68, y, 68, 20).build());
        updateSaveButton();
    }

    private void updateSaveButton() {
        if (saveButton != null) {
            saveButton.active = config.categories.stream().flatMap(category -> category.fields.stream())
                    .allMatch(field -> field.valid);
        }
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(config.parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics) {
        super.extractRenderState(graphics);
        graphics.centeredText(font, title, width / 2, 7, 0xFFFFFFFF);
    }
}
