package mekanism.client;

import mekanism.client.gui.GuiRadialSelector;
import mekanism.client.key.MekKeyHandler;
import mekanism.client.render.hud.MekanismStatusOverlay;
import mekanism.client.render.lib.ScrollIncrementer;
import mekanism.common.Mekanism;
import mekanism.common.config.MekanismConfig;
import mekanism.common.item.interfaces.IModeItem;
import mekanism.common.lib.radial.IGenericRadialModeItem;
import mekanism.common.network.to_server.PacketModeChange;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.InputEvent.MouseScrollingEvent;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Exact Configurator radial/scroll input path extracted from {@link ClientTickHandler}. */
public class TransporterClientInputHandler {

    private static final Minecraft minecraft = Minecraft.getInstance();
    private static final ScrollIncrementer scrollIncrementer = new ScrollIncrementer(true);

    @SubscribeEvent
    public void onTick(ClientTickEvent event) {
        if (event.phase == Phase.START && minecraft.player != null &&
            (minecraft.screen == null || minecraft.screen instanceof GuiRadialSelector)) {
            if (!MekKeyHandler.isRadialPressed() || (!updateSelectorRenderer(EquipmentSlot.MAINHAND) && !updateSelectorRenderer(EquipmentSlot.OFFHAND))) {
                if (minecraft.screen instanceof GuiRadialSelector) {
                    minecraft.setScreen(null);
                }
            }
        }
    }

    private boolean updateSelectorRenderer(EquipmentSlot slot) {
        if (minecraft.player != null) {
            ItemStack stack = minecraft.player.getItemBySlot(slot);
            if (stack.getItem() instanceof IGenericRadialModeItem item) {
                var radialData = item.getRadialData(stack);
                if (radialData != null) {
                    if (!(minecraft.screen instanceof GuiRadialSelector screen) || !screen.hasMatchingData(slot, radialData)) {
                        GuiRadialSelector newSelector = new GuiRadialSelector(slot, radialData, () -> minecraft.player);
                        newSelector.tryInheritCurrentPath(minecraft.screen);
                        minecraft.setScreen(newSelector);
                    }
                    return true;
                }
            }
        }
        return false;
    }

    @SubscribeEvent
    public void onMouseEvent(MouseScrollingEvent event) {
        if (MekanismConfig.client.allowModeScroll.get() && minecraft.player != null && minecraft.player.isShiftKeyDown()) {
            handleModeScroll(event, EquipmentSlot.MAINHAND, event.getScrollDelta());
        }
    }

    private void handleModeScroll(Event event, EquipmentSlot slot, double delta) {
        if (delta != 0 && IModeItem.isModeItem(minecraft.player, slot)) {
            int shift = scrollIncrementer.scroll(delta);
            if (shift != 0) {
                MekanismStatusOverlay.INSTANCE.setTimer();
                Mekanism.packetHandler().sendToServer(new PacketModeChange(slot, shift));
            }
            event.setCanceled(true);
        }
    }
}
