package dev.bcic2bridge;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Compact Forge config screen for the bridge's server-side settings.
 *
 * <p>Dedicated-server values are intentionally shown as read-only: Forge syncs
 * server config to the client, but does not grant clients permission to alter it.</p>
 */
public final class BridgeConfigScreen extends Screen
{
    private static final int ROW_HEIGHT = 24;
    private static final int CONTENT_LEFT = 12;
    private static final int CONTENT_RIGHT = 12;

    private final Screen parent;
    private final Draft draft;
    private final Map<String, EditBox> inputs = new LinkedHashMap<>();
    private Page page = Page.BALANCE;
    private String status = "";
    private boolean canSave;

    public BridgeConfigScreen(Screen parent)
    {
        super(Component.literal("BuildCraft x IC2 Fuel Bridge"));
        this.parent = parent;
        this.draft = BridgeConfig.SPEC.isLoaded() ? Draft.fromLoadedConfig() : null;
    }

    @Override
    protected void init()
    {
        this.clearWidgets();
        this.inputs.clear();

        if (this.draft == null)
        {
            this.addRenderableWidget(Button.builder(Component.literal("Done"), button -> this.onClose())
                    .bounds(this.width / 2 - 50, this.height - 28, 100, 20)
                    .build());
            return;
        }

        this.canSave = this.minecraft != null && this.minecraft.hasSingleplayerServer();
        this.addNavigation();

        switch (this.page)
        {
            case BALANCE -> this.addBalanceWidgets();
            case PROFILES -> this.addProfileWidgets();
            case FALLBACK -> this.addFallbackWidgets();
            case DISCOVERY -> this.addDiscoveryWidgets();
            case OVERRIDES -> this.addOverrideWidgets();
        }

        int buttonY = this.height - 28;
        Button save = this.addRenderableWidget(Button.builder(Component.literal("Save"), ignored -> this.save())
                .bounds(this.width / 2 - 104, buttonY, 98, 20)
                .build());
        save.active = this.canSave;
        this.addRenderableWidget(Button.builder(Component.literal("Done"), ignored -> this.closeAfterSaving())
                .bounds(this.width / 2 + 6, buttonY, 98, 20)
                .build());
    }

    private void addNavigation()
    {
        int tabWidth = Math.min(88, (this.width - 20) / Page.values().length);
        int totalWidth = tabWidth * Page.values().length;
        int startX = (this.width - totalWidth) / 2;
        for (Page candidate : Page.values())
        {
            Button button = this.addRenderableWidget(Button.builder(
                            Component.literal(candidate.label),
                            ignored -> this.switchPage(candidate)
                    )
                    .bounds(startX + candidate.ordinal() * tabWidth, 28, tabWidth - 2, 20)
                    .build());
            button.active = candidate != this.page;
        }
    }

    private void addBalanceWidgets()
    {
        this.addToggle("Use automatic BuildCraft CE profiles", this.draft.useBuiltInProfiles,
                value -> this.draft.useBuiltInProfiles = value, 68);
        this.addInput("euPerMj", "EU per BuildCraft MJ", this.draft.euPerMj, 96);
        this.addInput("energyMultiplier", "Global energy multiplier", this.draft.energyMultiplier, 120);
        this.addInput("ceCycle", "CE burn cycle (mB)", this.draft.ceCycleAmountMb, 144);
    }

    private void addProfileWidgets()
    {
        int activeX = Math.max(108, this.width / 2 - 84);
        int modeX = activeX + 44;
        int manualX = modeX + 66;
        int manualWidth = Math.max(48, this.width - manualX - CONTENT_RIGHT);
        int y = 72;

        for (BuildCraftCeFuelProfiles.FuelDefinition fuel : BuildCraftCeFuelProfiles.definitions())
        {
            Draft.FuelProfileDraft profile = this.draft.fuelProfiles.get(fuel.id());
            if (profile == null)
            {
                continue;
            }

            String key = "profile." + fuel.id() + ".manual";
            Button enabled = this.addRenderableWidget(Button.builder(Component.literal(profile.enabled ? "ON" : "OFF"), ignored -> {
                        this.storeCurrentPageValues();
                        profile.enabled = !profile.enabled;
                        this.init();
                    })
                    .bounds(activeX, y, 42, 16)
                    .build());
            enabled.active = this.canSave;

            Button mode = this.addRenderableWidget(Button.builder(Component.literal(profile.mode.name()), ignored -> {
                        this.storeCurrentPageValues();
                        profile.mode = profile.mode == BuildCraftFuelMode.AUTO
                                ? BuildCraftFuelMode.MANUAL
                                : BuildCraftFuelMode.AUTO;
                        this.init();
                    })
                    .bounds(modeX, y, 64, 16)
                    .build());
            mode.active = this.canSave;

            EditBox manual = new EditBox(this.font, manualX, y, manualWidth, 16,
                    Component.literal("Manual EU/MJ"));
            manual.setMaxLength(32);
            manual.setValue(profile.manualEuPerMj);
            manual.setEditable(this.canSave && profile.mode == BuildCraftFuelMode.MANUAL);
            manual.active = this.canSave && profile.mode == BuildCraftFuelMode.MANUAL;
            this.addRenderableWidget(manual);
            this.inputs.put(key, manual);
            y += 18;
        }
    }

    private void addFallbackWidgets()
    {
        this.addInput("referenceVolume", "Reference unit volume (mB)", this.draft.referenceVolumeMb, 72);
        this.addInput("oilEnergy", "Generic oil EU / unit", this.draft.oilEnergyEuPerReferenceUnit, 96);
        this.addInput("oilCycle", "Generic oil cycle (mB)", this.draft.oilCycleAmountMb, 120);
        this.addInput("fuelEnergy", "Generic fuel EU / unit", this.draft.fuelEnergyEuPerReferenceUnit, 144);
        this.addInput("fuelCycle", "Generic fuel cycle (mB)", this.draft.fuelCycleAmountMb, 168);
    }

    private void addDiscoveryWidgets()
    {
        this.addToggle("Automatic fluid discovery", this.draft.autoDiscovery,
                value -> this.draft.autoDiscovery = value, 72);
        this.addInput("namespaceTokens", "Namespace tokens (comma-separated)", this.draft.namespaceTokens, 100);
        this.addToggle("Class package fallback", this.draft.classPackageFallback,
                value -> this.draft.classPackageFallback = value, 128);
        this.addToggle("Log skipped oil/fuel fluids", this.draft.logSkippedOilFuelIds,
                value -> this.draft.logSkippedOilFuelIds = value, 156);
    }

    private void addOverrideWidgets()
    {
        this.addInput("customRules", "Rules (separate with |)", this.draft.customRules, 82);
    }

    private void addToggle(String label, boolean value, BooleanConsumer onChange, int y)
    {
        Button toggle = this.addRenderableWidget(Button.builder(toggleLabel(label, value), button -> {
                    boolean newValue = !valueOf(button);
                    onChange.accept(newValue);
                    button.setMessage(toggleLabel(label, newValue));
                })
                .bounds(CONTENT_LEFT, y, this.width - CONTENT_LEFT - CONTENT_RIGHT, 20)
                .build());
        toggle.active = this.canSave;
    }

    private static boolean valueOf(Button button)
    {
        return button.getMessage().getString().endsWith(": ON");
    }

    private static Component toggleLabel(String label, boolean value)
    {
        return Component.literal(label + ": " + (value ? "ON" : "OFF"));
    }

    private void addInput(String key, String label, Object value, int y)
    {
        EditBox input = new EditBox(this.font, this.inputX(), y, this.inputWidth(), 20, Component.literal(label));
        input.setMaxLength(2048);
        input.setValue(String.valueOf(value));
        input.setEditable(this.canSave);
        input.active = this.canSave;
        this.addRenderableWidget(input);
        this.inputs.put(key, input);
    }

    private int inputX()
    {
        return this.width / 2 + 8;
    }

    private int inputWidth()
    {
        return this.width - this.inputX() - CONTENT_RIGHT;
    }

    private void switchPage(Page nextPage)
    {
        this.storeCurrentPageValues();
        this.page = nextPage;
        this.init();
    }

    private void storeCurrentPageValues()
    {
        switch (this.page)
        {
            case BALANCE -> {
                this.draft.euPerMj = this.value("euPerMj");
                this.draft.energyMultiplier = this.value("energyMultiplier");
                this.draft.ceCycleAmountMb = this.value("ceCycle");
            }
            case PROFILES -> {
                for (BuildCraftCeFuelProfiles.FuelDefinition fuel : BuildCraftCeFuelProfiles.definitions())
                {
                    Draft.FuelProfileDraft profile = this.draft.fuelProfiles.get(fuel.id());
                    if (profile != null)
                    {
                        profile.manualEuPerMj = this.value("profile." + fuel.id() + ".manual");
                    }
                }
            }
            case FALLBACK -> {
                this.draft.referenceVolumeMb = this.value("referenceVolume");
                this.draft.oilEnergyEuPerReferenceUnit = this.value("oilEnergy");
                this.draft.oilCycleAmountMb = this.value("oilCycle");
                this.draft.fuelEnergyEuPerReferenceUnit = this.value("fuelEnergy");
                this.draft.fuelCycleAmountMb = this.value("fuelCycle");
            }
            case DISCOVERY -> this.draft.namespaceTokens = this.value("namespaceTokens");
            case OVERRIDES -> this.draft.customRules = this.value("customRules");
        }
    }

    private String value(String key)
    {
        EditBox input = this.inputs.get(key);
        return input == null ? "" : input.getValue().trim();
    }

    private void save()
    {
        this.storeCurrentPageValues();
        if (this.applyDraft())
        {
            this.status = "Saved. Restart the world/server to apply fuel rules.";
        }
    }

    private void closeAfterSaving()
    {
        this.storeCurrentPageValues();
        if (!this.canSave || this.applyDraft())
        {
            this.onClose();
        }
    }

    private boolean applyDraft()
    {
        if (!this.canSave)
        {
            this.status = "Server config is read-only here. Edit the serverconfig file as an administrator.";
            return false;
        }

        try
        {
            int referenceVolume = this.parseInt("Reference unit volume", this.draft.referenceVolumeMb, 1, 10_000);
            double multiplier = this.parseDouble("Global energy multiplier", this.draft.energyMultiplier, 0.000001D, 1_000_000.0D);
            double oilEnergy = this.parseDouble("Generic oil energy", this.draft.oilEnergyEuPerReferenceUnit, 0.000001D, 1_000_000_000_000.0D);
            int oilCycle = this.parseInt("Generic oil cycle", this.draft.oilCycleAmountMb, 1, 10_000);
            double fuelEnergy = this.parseDouble("Generic fuel energy", this.draft.fuelEnergyEuPerReferenceUnit, 0.000001D, 1_000_000_000_000.0D);
            int fuelCycle = this.parseInt("Generic fuel cycle", this.draft.fuelCycleAmountMb, 1, 10_000);
            double euPerMj = this.parseDouble("EU per BuildCraft MJ", this.draft.euPerMj, 0.000001D, 1_000_000.0D);
            int ceCycle = this.parseInt("CE burn cycle", this.draft.ceCycleAmountMb, 1, 10_000);

            Map<String, Double> profileConversions = new LinkedHashMap<>();
            for (BuildCraftCeFuelProfiles.FuelDefinition fuel : BuildCraftCeFuelProfiles.definitions())
            {
                Draft.FuelProfileDraft profile = this.draft.fuelProfiles.get(fuel.id());
                if (profile != null)
                {
                    profileConversions.put(fuel.id(), this.parseDouble(
                            fuel.displayName() + " manual EU/MJ", profile.manualEuPerMj, 0.000001D, 1_000_000.0D
                    ));
                }
            }

            BridgeConfig.AUTO_DISCOVERY.set(this.draft.autoDiscovery);
            BridgeConfig.NAMESPACE_TOKENS.set(this.splitList(this.draft.namespaceTokens, ","));
            BridgeConfig.CLASS_PACKAGE_FALLBACK.set(this.draft.classPackageFallback);
            BridgeConfig.LOG_SKIPPED_OIL_FUEL_IDS.set(this.draft.logSkippedOilFuelIds);
            BridgeConfig.REFERENCE_UNIT_VOLUME_MB.set(referenceVolume);
            BridgeConfig.ENERGY_MULTIPLIER.set(multiplier);
            BridgeConfig.OIL_ENERGY_EU_PER_REFERENCE_UNIT.set(oilEnergy);
            BridgeConfig.OIL_CYCLE_AMOUNT_MB.set(oilCycle);
            BridgeConfig.FUEL_ENERGY_EU_PER_REFERENCE_UNIT.set(fuelEnergy);
            BridgeConfig.FUEL_CYCLE_AMOUNT_MB.set(fuelCycle);
            BridgeConfig.USE_BUILDCRAFT_CE_8_PROFILES.set(this.draft.useBuiltInProfiles);
            BridgeConfig.EU_PER_BUILDCRAFT_MJ.set(euPerMj);
            BridgeConfig.BUILDCRAFT_CE_CYCLE_AMOUNT_MB.set(ceCycle);
            BridgeConfig.CUSTOM_FUEL_RULES.set(this.splitList(this.draft.customRules, "\\|"));
            for (BuildCraftCeFuelProfiles.FuelDefinition fuel : BuildCraftCeFuelProfiles.definitions())
            {
                Draft.FuelProfileDraft profile = this.draft.fuelProfiles.get(fuel.id());
                BridgeConfig.BuildCraftCeFuelSettings settings = BridgeConfig.getBuildCraftCeFuelSettings(fuel.id());
                if (profile != null && settings != null)
                {
                    settings.enabled.set(profile.enabled);
                    settings.mode.set(profile.mode);
                    settings.manualEuPerBuildCraftMj.set(profileConversions.get(fuel.id()));
                }
            }
            BridgeConfig.SPEC.save();
            return true;
        }
        catch (IllegalArgumentException exception)
        {
            this.status = exception.getMessage();
            return false;
        }
    }

    private int parseInt(String label, String raw, int min, int max)
    {
        try
        {
            int value = Integer.parseInt(raw);
            if (value < min || value > max)
            {
                throw new IllegalArgumentException(label + " must be between " + min + " and " + max + ".");
            }
            return value;
        }
        catch (NumberFormatException exception)
        {
            throw new IllegalArgumentException(label + " must be a whole number.");
        }
    }

    private double parseDouble(String label, String raw, double min, double max)
    {
        try
        {
            double value = Double.parseDouble(raw);
            if (!Double.isFinite(value) || value < min || value > max)
            {
                throw new IllegalArgumentException(label + " must be between " + min + " and " + max + ".");
            }
            return value;
        }
        catch (NumberFormatException exception)
        {
            throw new IllegalArgumentException(label + " must be a number.");
        }
    }

    private List<String> splitList(String raw, String separator)
    {
        if (raw == null || raw.isBlank())
        {
            return List.of();
        }
        return Arrays.stream(raw.split(separator))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
    {
        this.renderBackground(graphics);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFF);

        if (this.draft == null)
        {
            graphics.drawCenteredString(this.font, "The server config is loaded only after opening a world/server.",
                    this.width / 2, this.height / 2 - 8, 0xFF8080);
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }

        this.renderPageLabels(graphics);
        if (this.page != Page.PROFILES)
        {
            graphics.drawCenteredString(
                    this.font,
                    this.canSave
                            ? "Changes are saved to this integrated server. Restart required."
                            : "Server-synced values are read-only. Configure a dedicated server in serverconfig.",
                    this.width / 2,
                    this.height - 52,
                    this.canSave ? 0xFFFF55 : 0xFFAA55
            );
            if (!this.status.isEmpty())
            {
                graphics.drawCenteredString(this.font, this.status, this.width / 2, this.height - 42, 0xFF8080);
            }
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderPageLabels(GuiGraphics graphics)
    {
        switch (this.page)
        {
            case BALANCE -> {
                this.label(graphics, "AUTO profiles use BuildCraft CE's extracted MJ/mB values.", 52);
                this.label(graphics, "EU per BuildCraft MJ", 102);
                this.label(graphics, "Global energy multiplier", 126);
                this.label(graphics, "CE burn cycle (mB)", 150);
            }
            case PROFILES -> {
                String message = this.status.isEmpty()
                        ? "OFF excludes this fuel. MANUAL enables its own EU/MJ conversion."
                        : this.status;
                graphics.drawCenteredString(this.font, message, this.width / 2, 51,
                        this.status.isEmpty() ? 0xE0E0E0 : 0xFF8080);
                int activeX = Math.max(108, this.width / 2 - 84);
                int modeX = activeX + 44;
                int manualX = modeX + 66;
                this.label(graphics, "Fuel (MJ/mB)", 61);
                graphics.drawString(this.font, "Active", activeX, 61, 0xE0E0E0);
                graphics.drawString(this.font, "Mode", modeX, 61, 0xE0E0E0);
                graphics.drawString(this.font, "Manual EU/MJ", manualX, 61, 0xE0E0E0);
                int y = 76;
                for (BuildCraftCeFuelProfiles.FuelDefinition fuel : BuildCraftCeFuelProfiles.definitions())
                {
                    this.label(graphics, fuel.displayName() + " (" + decimal(fuel.megaJoulesPerMb()) + ")", y);
                    y += 18;
                }
            }
            case FALLBACK -> {
                this.label(graphics, "Fallback values apply to non-CE oil/fuel fluids found by discovery.", 52);
                this.label(graphics, "Reference unit volume (mB)", 78);
                this.label(graphics, "Generic oil EU / unit", 102);
                this.label(graphics, "Generic oil cycle (mB)", 126);
                this.label(graphics, "Generic fuel EU / unit", 150);
                this.label(graphics, "Generic fuel cycle (mB)", 174);
            }
            case DISCOVERY -> {
                this.label(graphics, "Only source fluids are registered; flowing variants are skipped.", 52);
                this.label(graphics, "Namespace tokens (comma-separated)", 106);
            }
            case OVERRIDES -> {
                this.label(graphics, "Syntax: fluid_id;energy_EU;volume_mB;cycle_mB", 52);
                this.label(graphics, "Separate multiple rules with a | character.", 66);
                this.label(graphics, "Rules (separate with |)", 88);
            }
        }
    }

    private void label(GuiGraphics graphics, String text, int y)
    {
        graphics.drawString(this.font, text, CONTENT_LEFT, y, 0xE0E0E0);
    }

    @Override
    public void onClose()
    {
        if (this.minecraft != null)
        {
            this.minecraft.setScreen(this.parent);
        }
    }

    private enum Page
    {
        BALANCE("Balance"),
        PROFILES("9 fuels"),
        FALLBACK("Fallback"),
        DISCOVERY("Discovery"),
        OVERRIDES("Overrides");

        private final String label;

        Page(String label)
        {
            this.label = label;
        }
    }

    @FunctionalInterface
    private interface BooleanConsumer
    {
        void accept(boolean value);
    }

    private static final class Draft
    {
        private boolean autoDiscovery;
        private boolean classPackageFallback;
        private boolean logSkippedOilFuelIds;
        private boolean useBuiltInProfiles;
        private String namespaceTokens;
        private String referenceVolumeMb;
        private String energyMultiplier;
        private String oilEnergyEuPerReferenceUnit;
        private String oilCycleAmountMb;
        private String fuelEnergyEuPerReferenceUnit;
        private String fuelCycleAmountMb;
        private String euPerMj;
        private String ceCycleAmountMb;
        private String customRules;
        private final Map<String, FuelProfileDraft> fuelProfiles = new LinkedHashMap<>();

        private static Draft fromLoadedConfig()
        {
            Draft result = new Draft();
            result.autoDiscovery = BridgeConfig.AUTO_DISCOVERY.get();
            result.classPackageFallback = BridgeConfig.CLASS_PACKAGE_FALLBACK.get();
            result.logSkippedOilFuelIds = BridgeConfig.LOG_SKIPPED_OIL_FUEL_IDS.get();
            result.useBuiltInProfiles = BridgeConfig.USE_BUILDCRAFT_CE_8_PROFILES.get();
            result.namespaceTokens = join(BridgeConfig.NAMESPACE_TOKENS.get(), ", ");
            result.referenceVolumeMb = String.valueOf(BridgeConfig.REFERENCE_UNIT_VOLUME_MB.get());
            result.energyMultiplier = decimal(BridgeConfig.ENERGY_MULTIPLIER.get());
            result.oilEnergyEuPerReferenceUnit = decimal(BridgeConfig.OIL_ENERGY_EU_PER_REFERENCE_UNIT.get());
            result.oilCycleAmountMb = String.valueOf(BridgeConfig.OIL_CYCLE_AMOUNT_MB.get());
            result.fuelEnergyEuPerReferenceUnit = decimal(BridgeConfig.FUEL_ENERGY_EU_PER_REFERENCE_UNIT.get());
            result.fuelCycleAmountMb = String.valueOf(BridgeConfig.FUEL_CYCLE_AMOUNT_MB.get());
            result.euPerMj = decimal(BridgeConfig.EU_PER_BUILDCRAFT_MJ.get());
            result.ceCycleAmountMb = String.valueOf(BridgeConfig.BUILDCRAFT_CE_CYCLE_AMOUNT_MB.get());
            result.customRules = join(BridgeConfig.CUSTOM_FUEL_RULES.get(), " | ");
            for (BuildCraftCeFuelProfiles.FuelDefinition fuel : BuildCraftCeFuelProfiles.definitions())
            {
                BridgeConfig.BuildCraftCeFuelSettings settings = BridgeConfig.getBuildCraftCeFuelSettings(fuel.id());
                if (settings != null)
                {
                    result.fuelProfiles.put(fuel.id(), new FuelProfileDraft(
                            settings.enabled.get(), settings.mode.get(), decimal(settings.manualEuPerBuildCraftMj.get())
                    ));
                }
            }
            return result;
        }

        private static String join(List<? extends String> values, String separator)
        {
            return String.join(separator, new ArrayList<>(values));
        }

        private static final class FuelProfileDraft
        {
            private boolean enabled;
            private BuildCraftFuelMode mode;
            private String manualEuPerMj;

            private FuelProfileDraft(boolean enabled, BuildCraftFuelMode mode, String manualEuPerMj)
            {
                this.enabled = enabled;
                this.mode = mode;
                this.manualEuPerMj = manualEuPerMj;
            }
        }
    }

    private static String decimal(double value)
    {
        return Double.toString(value);
    }
}
