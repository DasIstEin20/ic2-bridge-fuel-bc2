package dev.bcic2bridge;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
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
    private static final int PANEL_MAX_WIDTH = 780;
    private static final int PANEL_TOP = 58;
    private static final int PANEL_PADDING = 24;
    private static final int INPUT_COLUMN_WIDTH = 390;

    private final Screen parent;
    private final Draft draft;
    private final Map<String, EditBox> inputs = new LinkedHashMap<>();
    private Page page = Page.ENERGY;
    private String status = "";
    private boolean canSave;

    public BridgeConfigScreen(Screen parent)
    {
        super(Component.translatable("screen.bcic2fuelbridge.title"));
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
            this.addRenderableWidget(Button.builder(Component.translatable("screen.bcic2fuelbridge.done"), button -> this.onClose())
                    .bounds(this.width / 2 - 50, this.height - 28, 100, 20)
                    .build());
            return;
        }

        this.canSave = this.minecraft != null && this.minecraft.hasSingleplayerServer();
        this.addNavigation();

        switch (this.page)
        {
            case ENERGY -> this.addEnergyWidgets();
            case BC_TO_IC2 -> this.addBuildCraftToIc2Widgets();
            case BALANCE -> this.addBalanceWidgets();
            case PROFILES -> this.addProfileWidgets();
            case IC2_TO_BC -> this.addIc2ToBuildCraftWidgets();
            case DISCOVERY -> this.addDiscoveryWidgets();
            case OVERRIDES -> this.addOverrideWidgets();
            case COMPATIBILITY -> BuildCraftCompatibilityResolver.resolve();
        }

        int buttonY = this.height - 28;
        Button save = this.addRenderableWidget(Button.builder(Component.translatable("screen.bcic2fuelbridge.save"), ignored -> this.save())
                .bounds(this.width / 2 - 104, buttonY, 98, 20)
                .build());
        save.active = this.canSave;
        this.addRenderableWidget(Button.builder(Component.translatable("screen.bcic2fuelbridge.done"), ignored -> this.closeAfterSaving())
                .bounds(this.width / 2 + 6, buttonY, 98, 20)
                .build());
    }

    private void addNavigation()
    {
        int tabWidth = Math.min(96, (this.width - 32) / Page.values().length);
        int totalWidth = tabWidth * Page.values().length;
        int startX = (this.width - totalWidth) / 2;
        for (Page candidate : Page.values())
        {
            Button button = this.addRenderableWidget(Button.builder(
                            Component.translatable(candidate.translationKey),
                            ignored -> this.switchPage(candidate)
                    )
                    .bounds(startX + candidate.ordinal() * tabWidth, 28, tabWidth - 2, 20)
                    .build());
            button.active = candidate != this.page;
        }
    }

    private void addBalanceWidgets()
    {
        this.addInput("energyMultiplier", "screen.bcic2fuelbridge.field.fuel_multiplier", this.draft.energyMultiplier, 94);
        this.addInput("referenceVolume", "screen.bcic2fuelbridge.field.reference_volume", this.draft.referenceVolumeMb, 122);
        this.addInput("oilEnergy", "screen.bcic2fuelbridge.field.oil_energy", this.draft.oilEnergyEuPerReferenceUnit, 150);
        this.addInput("oilCycle", "screen.bcic2fuelbridge.field.oil_cycle", this.draft.oilCycleAmountMb, 178);
        this.addInput("fuelEnergy", "screen.bcic2fuelbridge.field.fuel_energy", this.draft.fuelEnergyEuPerReferenceUnit, 206);
        this.addInput("fuelCycle", "screen.bcic2fuelbridge.field.fuel_cycle", this.draft.fuelCycleAmountMb, 234);
    }

    private void addEnergyWidgets()
    {
        this.addToggle("screen.bcic2fuelbridge.toggle.energy", this.draft.energyBridgeEnabled,
                value -> this.draft.energyBridgeEnabled = value, 94);
        this.addToggle("screen.bcic2fuelbridge.toggle.energy_bc_to_ic2", this.draft.buildCraftToIc2EnergyBridgeEnabled,
                value -> this.draft.buildCraftToIc2EnergyBridgeEnabled = value, 122);
        this.addEnergyModeButton(150);
        EditBox ratio = this.addInput("euPerMj", "screen.bcic2fuelbridge.field.eu_per_mj", this.draft.euPerMj, 178);
        ratio.setEditable(this.canSave && this.draft.energyConversionMode == EnergyConversionMode.MANUAL);
        ratio.active = this.canSave && this.draft.energyConversionMode == EnergyConversionMode.MANUAL;
        ratio.setTooltip(Tooltip.create(Component.translatable("screen.bcic2fuelbridge.tooltip.conversion_mode", EnergyConversionService.AUTO_EU_PER_MJ)));
        this.addTransferLimitModeButton(206);
        EditBox limit = this.addInput("transferLimit", "screen.bcic2fuelbridge.field.transfer_limit", this.draft.transferLimitEuPerTick, 234);
        limit.setEditable(this.canSave && this.draft.transferLimitMode == EnergyTransferLimitMode.MANUAL);
        limit.active = this.canSave && this.draft.transferLimitMode == EnergyTransferLimitMode.MANUAL;
        limit.setTooltip(Tooltip.create(Component.translatable("screen.bcic2fuelbridge.tooltip.transfer_limit")));
    }

    private void addBuildCraftToIc2Widgets()
    {
        this.addToggle("screen.bcic2fuelbridge.toggle.bc_to_ic2", this.draft.bcToIc2Enabled,
                value -> this.draft.bcToIc2Enabled = value, 94);
        this.addToggle("screen.bcic2fuelbridge.toggle.ce_profiles", this.draft.useBuiltInProfiles,
                value -> this.draft.useBuiltInProfiles = value, 122);
        this.addInput("ceCycle", "screen.bcic2fuelbridge.field.ce_cycle", this.draft.ceCycleAmountMb, 150);
    }

    private void addIc2ToBuildCraftWidgets()
    {
        this.addToggle("screen.bcic2fuelbridge.toggle.ic2_to_bc", this.draft.ic2ToBcEnabled,
                value -> this.draft.ic2ToBcEnabled = value, 94);
        this.addToggle("screen.bcic2fuelbridge.toggle.ic2_discovery", this.draft.ic2ToBcAutoDiscovery,
                value -> this.draft.ic2ToBcAutoDiscovery = value, 122);
        this.addInput("ic2ToBcTokens", "screen.bcic2fuelbridge.field.namespace_tokens", this.draft.ic2ToBcNamespaceTokens, 150);
        this.addInput("ic2ToBcBurnTicks", "screen.bcic2fuelbridge.field.burn_time", this.draft.ic2ToBcBurnTimeTicks, 178);
    }

    private void addProfileWidgets()
    {
        int activeX = this.panelLeft() + 278;
        int modeX = activeX + 58;
        int manualX = modeX + 84;
        int manualWidth = this.panelRight() - manualX - PANEL_PADDING;
        int y = 104;

        for (BuildCraftCeFuelProfiles.FuelDefinition fuel : BuildCraftCeFuelProfiles.definitions())
        {
            Draft.FuelProfileDraft profile = this.draft.fuelProfiles.get(fuel.id());
            if (profile == null)
            {
                continue;
            }

            String key = "profile." + fuel.id() + ".manual";
            Button enabled = this.addRenderableWidget(Button.builder(Component.translatable(profile.enabled ? "screen.bcic2fuelbridge.on" : "screen.bcic2fuelbridge.off"), ignored -> {
                        this.storeCurrentPageValues();
                        profile.enabled = !profile.enabled;
                        this.init();
                    })
                    .bounds(activeX, y, 56, 18)
                    .build());
            enabled.active = this.canSave;

            Button mode = this.addRenderableWidget(Button.builder(this.enumLabel(profile.mode), ignored -> {
                        this.storeCurrentPageValues();
                        profile.mode = profile.mode == BuildCraftFuelMode.AUTO
                                ? BuildCraftFuelMode.MANUAL
                                : BuildCraftFuelMode.AUTO;
                        this.init();
                    })
                    .bounds(modeX, y, 82, 18)
                    .build());
            mode.active = this.canSave;

            EditBox manual = new EditBox(this.font, manualX, y, manualWidth, 18,
                    Component.translatable("screen.bcic2fuelbridge.profile.manual"));
            manual.setMaxLength(32);
            manual.setValue(profile.manualEuPerMj);
            manual.setEditable(this.canSave && profile.mode == BuildCraftFuelMode.MANUAL);
            manual.active = this.canSave && profile.mode == BuildCraftFuelMode.MANUAL;
            this.addRenderableWidget(manual);
            this.inputs.put(key, manual);
            y += 22;
        }
    }

    private void addDiscoveryWidgets()
    {
        this.addToggle("screen.bcic2fuelbridge.toggle.auto_discovery", this.draft.autoDiscovery,
                value -> this.draft.autoDiscovery = value, 94);
        this.addInput("namespaceTokens", "screen.bcic2fuelbridge.field.namespace_tokens", this.draft.namespaceTokens, 122);
        this.addToggle("screen.bcic2fuelbridge.toggle.class_fallback", this.draft.classPackageFallback,
                value -> this.draft.classPackageFallback = value, 150);
        this.addToggle("screen.bcic2fuelbridge.toggle.log_skipped", this.draft.logSkippedOilFuelIds,
                value -> this.draft.logSkippedOilFuelIds = value, 178);
    }

    private void addOverrideWidgets()
    {
        this.addInput("customRules", "screen.bcic2fuelbridge.field.bc_to_ic2_rules", this.draft.customRules, 116);
        this.addInput("ic2ToBcRules", "screen.bcic2fuelbridge.field.ic2_to_bc_rules", this.draft.ic2ToBcCustomRules, 168);
    }

    private void addEnergyModeButton(int y)
    {
        Button button = this.addRenderableWidget(Button.builder(
                        Component.translatable("screen.bcic2fuelbridge.button.conversion_mode", this.enumLabel(this.draft.energyConversionMode)),
                        ignored -> {
                            this.storeCurrentPageValues();
                            this.draft.energyConversionMode = this.draft.energyConversionMode == EnergyConversionMode.AUTO
                                    ? EnergyConversionMode.MANUAL
                                    : EnergyConversionMode.AUTO;
                            this.init();
                        }
                )
                .bounds(this.controlX(), y, this.controlWidth(), 20)
                .build());
        button.active = this.canSave;
        button.setTooltip(Tooltip.create(Component.translatable("screen.bcic2fuelbridge.tooltip.conversion_mode", EnergyConversionService.AUTO_EU_PER_MJ)));
    }

    private void addTransferLimitModeButton(int y)
    {
        Button button = this.addRenderableWidget(Button.builder(
                        Component.translatable("screen.bcic2fuelbridge.button.transfer_limit", this.enumLabel(this.draft.transferLimitMode)),
                        ignored -> {
                            this.storeCurrentPageValues();
                            this.draft.transferLimitMode = this.draft.transferLimitMode == EnergyTransferLimitMode.AUTO
                                    ? EnergyTransferLimitMode.MANUAL
                                    : EnergyTransferLimitMode.AUTO;
                            this.init();
                        }
                )
                .bounds(this.controlX(), y, this.controlWidth(), 20)
                .build());
        button.active = this.canSave;
        button.setTooltip(Tooltip.create(Component.translatable("screen.bcic2fuelbridge.tooltip.transfer_limit")));
    }

    private void addToggle(String translationKey, boolean value, BooleanConsumer onChange, int y)
    {
        Button toggle = this.addRenderableWidget(Button.builder(toggleLabel(translationKey, value), button -> {
                    boolean newValue = !button.getMessage().getString().equals(toggleLabel(translationKey, true).getString());
                    onChange.accept(newValue);
                    button.setMessage(toggleLabel(translationKey, newValue));
                })
                .bounds(this.controlX(), y, this.controlWidth(), 20)
                .build());
        toggle.active = this.canSave;
    }

    private static Component toggleLabel(String translationKey, boolean value)
    {
        return Component.translatable("screen.bcic2fuelbridge.toggle", Component.translatable(translationKey),
                Component.translatable(value ? "screen.bcic2fuelbridge.on" : "screen.bcic2fuelbridge.off"));
    }

    private EditBox addInput(String key, String labelKey, Object value, int y)
    {
        EditBox input = new EditBox(this.font, this.inputX(), y, this.inputWidth(), 20, Component.translatable(labelKey));
        input.setMaxLength(2048);
        input.setValue(String.valueOf(value));
        input.setEditable(this.canSave);
        input.active = this.canSave;
        this.addRenderableWidget(input);
        this.inputs.put(key, input);
        input.setTooltip(Tooltip.create(Component.translatable(labelKey)));
        return input;
    }

    private int inputX()
    {
        return this.panelRight() - PANEL_PADDING - INPUT_COLUMN_WIDTH;
    }

    private int inputWidth()
    {
        return INPUT_COLUMN_WIDTH;
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
            case ENERGY -> {
                this.draft.euPerMj = this.value("euPerMj");
                this.draft.transferLimitEuPerTick = this.value("transferLimit");
            }
            case BC_TO_IC2 -> this.draft.ceCycleAmountMb = this.value("ceCycle");
            case BALANCE -> {
                this.draft.energyMultiplier = this.value("energyMultiplier");
                this.draft.referenceVolumeMb = this.value("referenceVolume");
                this.draft.oilEnergyEuPerReferenceUnit = this.value("oilEnergy");
                this.draft.oilCycleAmountMb = this.value("oilCycle");
                this.draft.fuelEnergyEuPerReferenceUnit = this.value("fuelEnergy");
                this.draft.fuelCycleAmountMb = this.value("fuelCycle");
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
            case IC2_TO_BC -> {
                this.draft.ic2ToBcNamespaceTokens = this.value("ic2ToBcTokens");
                this.draft.ic2ToBcBurnTimeTicks = this.value("ic2ToBcBurnTicks");
            }
            case DISCOVERY -> this.draft.namespaceTokens = this.value("namespaceTokens");
            case OVERRIDES -> {
                this.draft.customRules = this.value("customRules");
                this.draft.ic2ToBcCustomRules = this.value("ic2ToBcRules");
            }
            case COMPATIBILITY -> { }
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
            this.status = Component.translatable("screen.bcic2fuelbridge.saved").getString();
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
            this.status = Component.translatable("screen.bcic2fuelbridge.readonly_status").getString();
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
            double transferLimit = this.parseDouble("Transfer limit", this.draft.transferLimitEuPerTick, 0.000001D, 1_000_000_000.0D);
            int ceCycle = this.parseInt("CE burn cycle", this.draft.ceCycleAmountMb, 1, 10_000);
            int ic2ToBcBurnTicks = this.parseInt("IC2 → BuildCraft burn time", this.draft.ic2ToBcBurnTimeTicks, 1, 2_000_000);

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
            BridgeConfig.ENERGY_BRIDGE_ENABLED.set(this.draft.energyBridgeEnabled);
            BridgeConfig.BUILDCRAFT_TO_IC2_ENERGY_BRIDGE_ENABLED.set(this.draft.buildCraftToIc2EnergyBridgeEnabled);
            BridgeConfig.ENERGY_CONVERSION_MODE.set(this.draft.energyConversionMode);
            BridgeConfig.EU_PER_BUILDCRAFT_MJ.set(euPerMj);
            BridgeConfig.ENERGY_TRANSFER_LIMIT_MODE.set(this.draft.transferLimitMode);
            BridgeConfig.ENERGY_TRANSFER_LIMIT_EU_PER_TICK.set(transferLimit);
            BridgeConfig.FUEL_BRIDGE_BC_TO_IC2_ENABLED.set(this.draft.bcToIc2Enabled);
            BridgeConfig.FUEL_BRIDGE_IC2_TO_BC_ENABLED.set(this.draft.ic2ToBcEnabled);
            BridgeConfig.IC2_TO_BC_AUTO_DISCOVERY.set(this.draft.ic2ToBcAutoDiscovery);
            BridgeConfig.IC2_TO_BC_NAMESPACE_TOKENS.set(this.splitList(this.draft.ic2ToBcNamespaceTokens, ","));
            BridgeConfig.IC2_TO_BC_BURN_TIME_TICKS.set(ic2ToBcBurnTicks);
            BridgeConfig.REFERENCE_UNIT_VOLUME_MB.set(referenceVolume);
            BridgeConfig.ENERGY_MULTIPLIER.set(multiplier);
            BridgeConfig.OIL_ENERGY_EU_PER_REFERENCE_UNIT.set(oilEnergy);
            BridgeConfig.OIL_CYCLE_AMOUNT_MB.set(oilCycle);
            BridgeConfig.FUEL_ENERGY_EU_PER_REFERENCE_UNIT.set(fuelEnergy);
            BridgeConfig.FUEL_CYCLE_AMOUNT_MB.set(fuelCycle);
            BridgeConfig.USE_BUILDCRAFT_CE_8_PROFILES.set(this.draft.useBuiltInProfiles);
            BridgeConfig.BUILDCRAFT_CE_CYCLE_AMOUNT_MB.set(ceCycle);
            BridgeConfig.CUSTOM_FUEL_RULES.set(this.splitList(this.draft.customRules, "\\|"));
            BridgeConfig.IC2_TO_BC_CUSTOM_FUEL_RULES.set(this.splitList(this.draft.ic2ToBcCustomRules, "\\|"));
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
        graphics.fill(this.panelLeft(), PANEL_TOP, this.panelRight(), this.panelBottom(), 0xD0101822);
        graphics.fill(this.panelLeft(), PANEL_TOP, this.panelRight(), PANEL_TOP + 1, 0xFF66788B);
        graphics.fill(this.panelLeft(), this.panelBottom() - 1, this.panelRight(), this.panelBottom(), 0xFF263544);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFF);

        if (this.draft == null)
        {
            graphics.drawCenteredString(this.font, Component.translatable("screen.bcic2fuelbridge.not_loaded"),
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
                            ? Component.translatable("screen.bcic2fuelbridge.integrated_note")
                            : Component.translatable("screen.bcic2fuelbridge.readonly_note"),
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
            case ENERGY -> {
                this.centeredNote(graphics, "screen.bcic2fuelbridge.note.energy", 70);
                this.label(graphics, "screen.bcic2fuelbridge.field.eu_per_mj", 184);
                this.label(graphics, "screen.bcic2fuelbridge.field.transfer_limit", 240);
            }
            case BC_TO_IC2 -> {
                this.centeredNote(graphics, "screen.bcic2fuelbridge.note.bc_to_ic2", 70);
                this.centeredNote(graphics, "screen.bcic2fuelbridge.note.profiles", 184);
            }
            case BALANCE -> {
                this.centeredNote(graphics, "screen.bcic2fuelbridge.note.balance", 70);
                this.label(graphics, "screen.bcic2fuelbridge.field.fuel_multiplier", 100);
                this.label(graphics, "screen.bcic2fuelbridge.field.reference_volume", 128);
                this.label(graphics, "screen.bcic2fuelbridge.field.oil_energy", 156);
                this.label(graphics, "screen.bcic2fuelbridge.field.oil_cycle", 184);
                this.label(graphics, "screen.bcic2fuelbridge.field.fuel_energy", 212);
                this.label(graphics, "screen.bcic2fuelbridge.field.fuel_cycle", 240);
            }
            case PROFILES -> {
                String message = this.status.isEmpty()
                        ? Component.translatable("screen.bcic2fuelbridge.note.profile").getString()
                        : this.status;
                graphics.drawCenteredString(this.font, message, this.width / 2, 70,
                        this.status.isEmpty() ? 0xE0E0E0 : 0xFF8080);
                int activeX = this.panelLeft() + 278;
                int modeX = activeX + 58;
                int manualX = modeX + 84;
                this.label(graphics, "screen.bcic2fuelbridge.profile.fuel", 91);
                graphics.drawString(this.font, Component.translatable("screen.bcic2fuelbridge.profile.active"), activeX, 91, 0xE0E0E0);
                graphics.drawString(this.font, Component.translatable("screen.bcic2fuelbridge.profile.mode"), modeX, 91, 0xE0E0E0);
                graphics.drawString(this.font, Component.translatable("screen.bcic2fuelbridge.profile.manual"), manualX, 91, 0xE0E0E0);
                int y = 109;
                for (BuildCraftCeFuelProfiles.FuelDefinition fuel : BuildCraftCeFuelProfiles.definitions())
                {
                    graphics.drawString(this.font, Component.translatable("screen.bcic2fuelbridge.fuel." + fuel.configKey(), decimal(fuel.megaJoulesPerMb())), this.labelX(), y, 0xE0E0E0);
                    y += 22;
                }
            }
            case IC2_TO_BC -> {
                this.centeredNote(graphics, "screen.bcic2fuelbridge.note.ic2_to_bc", 70);
                this.label(graphics, "screen.bcic2fuelbridge.field.namespace_tokens", 156);
                this.label(graphics, "screen.bcic2fuelbridge.field.burn_time", 184);
            }
            case DISCOVERY -> {
                this.centeredNote(graphics, "screen.bcic2fuelbridge.note.discovery", 70);
                this.label(graphics, "screen.bcic2fuelbridge.field.namespace_tokens", 128);
            }
            case OVERRIDES -> {
                this.centeredNote(graphics, "screen.bcic2fuelbridge.note.bc_rule", 70);
                this.label(graphics, "screen.bcic2fuelbridge.field.bc_to_ic2_rules", 106);
                this.centeredNote(graphics, "screen.bcic2fuelbridge.note.ic2_rule", 142);
                this.label(graphics, "screen.bcic2fuelbridge.field.ic2_to_bc_rules", 158);
            }
            case COMPATIBILITY -> {
                this.renderCompatibility(graphics);
            }
        }
    }

    private void renderCompatibility(GuiGraphics graphics)
    {
        BuildCraftCompatibilityResolver.Snapshot compatibility = BuildCraftCompatibilityResolver.latest();
        int x = this.labelX();
        graphics.drawString(this.font, Component.translatable(
                "screen.bcic2fuelbridge.compat.detected",
                compatibility.detected()
                        ? compatibility.modId() + " " + compatibility.version()
                        : Component.translatable("screen.bcic2fuelbridge.compat.not_detected")
        ), x, 78, 0xE0E0E0);
        graphics.drawString(this.font, Component.translatable(
                "screen.bcic2fuelbridge.compat.adapter",
                compatibility.adapter().displayName()
        ), x, 100, 0xE0E0E0);
        graphics.drawString(this.font, Component.translatable(
                "screen.bcic2fuelbridge.compat.mode",
                compatibility.modeName()
        ), x, 122, compatibility.bestEffort() ? 0xFFFF55 : 0xC8D3E0);
        graphics.drawString(this.font, this.compatibilityFeature("screen.bcic2fuelbridge.compat.energy_ic2_to_bc", compatibility.energyBridge()), x, 148, 0xE0E0E0);
        graphics.drawString(this.font, this.compatibilityFeature("screen.bcic2fuelbridge.compat.energy_bc_to_ic2", compatibility.buildCraftToIc2EnergyBridge()), x, 170, 0xE0E0E0);
        graphics.drawString(this.font, this.compatibilityFeature("screen.bcic2fuelbridge.compat.bc_to_ic2", compatibility.buildCraftToIc2Fuels()), x, 192, 0xE0E0E0);
        graphics.drawString(this.font, this.compatibilityFeature("screen.bcic2fuelbridge.compat.ic2_to_bc", compatibility.ic2ToBuildCraftFuels()), x, 214, 0xE0E0E0);
        graphics.drawString(this.font, Component.translatable(
                "screen.bcic2fuelbridge.compat.fluid_discovery",
                compatibilityFeatureName(compatibility.fluidDiscovery()),
                compatibility.discoveredBuildCraftFluids()
        ), x, 236, 0xE0E0E0);
    }

    private Component compatibilityFeature(String key, boolean available)
    {
        return Component.translatable(key, compatibilityFeatureName(available));
    }

    private Component compatibilityFeatureName(boolean available)
    {
        return Component.translatable(available
                ? "screen.bcic2fuelbridge.compat.available"
                : "screen.bcic2fuelbridge.compat.unavailable");
    }

    private void label(GuiGraphics graphics, String translationKey, int y)
    {
        graphics.drawString(this.font, Component.translatable(translationKey), this.labelX(), y, 0xE0E0E0);
    }

    private void centeredNote(GuiGraphics graphics, String translationKey, int y)
    {
        graphics.drawCenteredString(this.font, Component.translatable(translationKey), this.width / 2, y, 0xC8D3E0);
    }

    private int panelWidth()
    {
        return Math.min(PANEL_MAX_WIDTH, this.width - 32);
    }

    private int panelLeft()
    {
        return (this.width - this.panelWidth()) / 2;
    }

    private int panelRight()
    {
        return this.panelLeft() + this.panelWidth();
    }

    private int panelBottom()
    {
        return switch (this.page)
        {
            case PROFILES -> 320;
            case BALANCE -> 270;
            case ENERGY -> 270;
            case IC2_TO_BC, DISCOVERY -> 218;
            case OVERRIDES -> 210;
            case BC_TO_IC2 -> 202;
            case COMPATIBILITY -> 260;
        };
    }

    private int controlX()
    {
        return this.panelLeft() + PANEL_PADDING;
    }

    private int controlWidth()
    {
        return this.panelWidth() - PANEL_PADDING * 2;
    }

    private int labelX()
    {
        return this.panelLeft() + PANEL_PADDING;
    }

    private Component enumLabel(Enum<?> value)
    {
        return Component.translatable("screen.bcic2fuelbridge.enum." + value.name().toLowerCase());
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
        ENERGY("screen.bcic2fuelbridge.category.energy"),
        BC_TO_IC2("screen.bcic2fuelbridge.category.bc_to_ic2"),
        PROFILES("screen.bcic2fuelbridge.category.profiles"),
        IC2_TO_BC("screen.bcic2fuelbridge.category.ic2_to_bc"),
        BALANCE("screen.bcic2fuelbridge.category.balance"),
        OVERRIDES("screen.bcic2fuelbridge.category.overrides"),
        DISCOVERY("screen.bcic2fuelbridge.category.discovery"),
        COMPATIBILITY("screen.bcic2fuelbridge.category.compatibility");

        private final String translationKey;

        Page(String translationKey)
        {
            this.translationKey = translationKey;
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
        private boolean energyBridgeEnabled;
        private boolean buildCraftToIc2EnergyBridgeEnabled;
        private boolean bcToIc2Enabled;
        private boolean ic2ToBcEnabled;
        private boolean ic2ToBcAutoDiscovery;
        private EnergyConversionMode energyConversionMode;
        private EnergyTransferLimitMode transferLimitMode;
        private String namespaceTokens;
        private String ic2ToBcNamespaceTokens;
        private String referenceVolumeMb;
        private String energyMultiplier;
        private String oilEnergyEuPerReferenceUnit;
        private String oilCycleAmountMb;
        private String fuelEnergyEuPerReferenceUnit;
        private String fuelCycleAmountMb;
        private String euPerMj;
        private String transferLimitEuPerTick;
        private String ceCycleAmountMb;
        private String customRules;
        private String ic2ToBcBurnTimeTicks;
        private String ic2ToBcCustomRules;
        private final Map<String, FuelProfileDraft> fuelProfiles = new LinkedHashMap<>();

        private static Draft fromLoadedConfig()
        {
            Draft result = new Draft();
            result.autoDiscovery = BridgeConfig.AUTO_DISCOVERY.get();
            result.classPackageFallback = BridgeConfig.CLASS_PACKAGE_FALLBACK.get();
            result.logSkippedOilFuelIds = BridgeConfig.LOG_SKIPPED_OIL_FUEL_IDS.get();
            result.useBuiltInProfiles = BridgeConfig.USE_BUILDCRAFT_CE_8_PROFILES.get();
            result.energyBridgeEnabled = BridgeConfig.ENERGY_BRIDGE_ENABLED.get();
            result.buildCraftToIc2EnergyBridgeEnabled = BridgeConfig.BUILDCRAFT_TO_IC2_ENERGY_BRIDGE_ENABLED.get();
            result.bcToIc2Enabled = BridgeConfig.FUEL_BRIDGE_BC_TO_IC2_ENABLED.get();
            result.ic2ToBcEnabled = BridgeConfig.FUEL_BRIDGE_IC2_TO_BC_ENABLED.get();
            result.ic2ToBcAutoDiscovery = BridgeConfig.IC2_TO_BC_AUTO_DISCOVERY.get();
            result.energyConversionMode = BridgeConfig.ENERGY_CONVERSION_MODE.get();
            result.transferLimitMode = BridgeConfig.ENERGY_TRANSFER_LIMIT_MODE.get();
            result.namespaceTokens = join(BridgeConfig.NAMESPACE_TOKENS.get(), ", ");
            result.ic2ToBcNamespaceTokens = join(BridgeConfig.IC2_TO_BC_NAMESPACE_TOKENS.get(), ", ");
            result.referenceVolumeMb = String.valueOf(BridgeConfig.REFERENCE_UNIT_VOLUME_MB.get());
            result.energyMultiplier = decimal(BridgeConfig.ENERGY_MULTIPLIER.get());
            result.oilEnergyEuPerReferenceUnit = decimal(BridgeConfig.OIL_ENERGY_EU_PER_REFERENCE_UNIT.get());
            result.oilCycleAmountMb = String.valueOf(BridgeConfig.OIL_CYCLE_AMOUNT_MB.get());
            result.fuelEnergyEuPerReferenceUnit = decimal(BridgeConfig.FUEL_ENERGY_EU_PER_REFERENCE_UNIT.get());
            result.fuelCycleAmountMb = String.valueOf(BridgeConfig.FUEL_CYCLE_AMOUNT_MB.get());
            result.euPerMj = decimal(BridgeConfig.EU_PER_BUILDCRAFT_MJ.get());
            result.transferLimitEuPerTick = decimal(BridgeConfig.ENERGY_TRANSFER_LIMIT_EU_PER_TICK.get());
            result.ceCycleAmountMb = String.valueOf(BridgeConfig.BUILDCRAFT_CE_CYCLE_AMOUNT_MB.get());
            result.customRules = join(BridgeConfig.CUSTOM_FUEL_RULES.get(), " | ");
            result.ic2ToBcBurnTimeTicks = String.valueOf(BridgeConfig.IC2_TO_BC_BURN_TIME_TICKS.get());
            result.ic2ToBcCustomRules = join(BridgeConfig.IC2_TO_BC_CUSTOM_FUEL_RULES.get(), " | ");
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
