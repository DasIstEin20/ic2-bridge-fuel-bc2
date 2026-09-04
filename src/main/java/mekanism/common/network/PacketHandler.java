package mekanism.common.network;

import mekanism.common.Mekanism;
import mekanism.common.network.to_client.PacketLaserHitBlock;
import mekanism.common.network.to_client.PacketLightningRender;
import mekanism.common.network.to_client.PacketPlayerData;
import mekanism.common.network.to_client.PacketPortalFX;
import mekanism.common.network.to_client.PacketQIOItemViewerGuiSync;
import mekanism.common.network.to_client.PacketRadiationData;
import mekanism.common.network.to_client.PacketResetPlayerClient;
import mekanism.common.network.to_client.PacketSecurityUpdate;
import mekanism.common.network.to_client.PacketShowModeChange;
import mekanism.common.network.to_client.PacketTransmitterUpdate;
import mekanism.common.network.to_client.PacketTransporterUpdate;
import mekanism.common.network.to_client.PacketUpdateTile;
import mekanism.common.network.to_client.container.PacketUpdateContainer;
import mekanism.common.network.to_server.PacketAddTrusted;
import mekanism.common.network.to_server.PacketConfigurationUpdate;
import mekanism.common.network.to_server.PacketDropperUse;
import mekanism.common.network.to_server.PacketEditFilter;
import mekanism.common.network.to_server.PacketGearStateUpdate;
import mekanism.common.network.to_server.PacketGuiButtonPress;
import mekanism.common.network.to_server.PacketGuiInteract;
import mekanism.common.network.to_server.PacketGuiItemDataRequest;
import mekanism.common.network.to_server.PacketGuiSetEnergy;
import mekanism.common.network.to_server.PacketGuiSetFrequency;
import mekanism.common.network.to_server.PacketGuiSetFrequencyColor;
import mekanism.common.network.to_server.PacketKey;
import mekanism.common.network.to_server.PacketModeChange;
import mekanism.common.network.to_server.PacketModeChangeCurios;
import mekanism.common.network.to_server.PacketNewFilter;
import mekanism.common.network.to_server.PacketOpenGui;
import mekanism.common.network.to_server.PacketPortableTeleporterTeleport;
import mekanism.common.network.to_server.PacketQIOClearCraftingWindow;
import mekanism.common.network.to_server.PacketQIOFillCraftingWindow;
import mekanism.common.network.to_server.PacketQIOItemViewerSlotInteract;
import mekanism.common.network.to_server.PacketRadialModeChange;
import mekanism.common.network.to_server.PacketRemoveModule;
import mekanism.common.network.to_server.PacketRobit;
import mekanism.common.network.to_server.PacketSecurityMode;
import mekanism.common.network.to_server.PacketUpdateModuleSettings;
import mekanism.common.network.to_server.PacketWindowSelect;
import net.minecraftforge.network.simple.SimpleChannel;

public class PacketHandler extends BasePacketHandler {

    private final SimpleChannel netHandler = createChannel(Mekanism.rl(Mekanism.MODID), Mekanism.instance.versionNumber);

    @Override
    protected SimpleChannel getChannel() {
        return netHandler;
    }

    @Override
    public void initialize() {
        //Client to server messages
        registerClientToServer(PacketModeChange.class, PacketModeChange::decode);
        registerClientToServer(PacketRadialModeChange.class, PacketRadialModeChange::decode);

        //Server to client messages
        registerServerToClient(PacketShowModeChange.class, PacketShowModeChange::decode);
        registerServerToClient(PacketTransmitterUpdate.class, PacketTransmitterUpdate::decode);
        registerServerToClient(PacketTransporterUpdate.class, PacketTransporterUpdate::decode);
        registerServerToClient(PacketUpdateTile.class, PacketUpdateTile::decode);
    }
}
