package mekanism.common.network;

import io.netty.buffer.Unpooled;
import mekanism.common.Mekanism;
import mekanism.common.base.KeySync;
import mekanism.common.network.to_server.PacketKey;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Exercises the live channel encoder that previously crashed on the Boost key binding. */
@PrefixGameTestTemplate(false)
@GameTestHolder(Mekanism.MODID)
public class PacketKeyRegistrationTest {

    @GameTest(template = "transmitter/straight_3c_cable", batch = "key_packets")
    public static void keyPressAndReleaseEncodeOnRegisteredChannel(GameTestHelper helper) {
        for (int key : new int[]{KeySync.BOOST, KeySync.ASCEND}) {
            for (boolean pressed : new boolean[]{true, false}) {
                FriendlyByteBuf encoded = new FriendlyByteBuf(Unpooled.buffer());
                FriendlyByteBuf decoded = new FriendlyByteBuf(Unpooled.buffer());
                try {
                    //Use the initialized mod channel, not a test-only registration or PacketKey.encode alone.
                    Mekanism.packetHandler().getChannel().encodeMessage(new PacketKey(key, pressed), encoded);
                    encoded.readUnsignedByte(); //Forge's packet discriminator precedes the message payload.
                    PacketKey.decode(encoded).encode(decoded);
                    helper.assertTrue(decoded.readVarInt() == key, "Key type changed during packet encoding");
                    helper.assertTrue(decoded.readBoolean() == pressed, "Key press/release state changed during packet encoding");
                    helper.assertTrue(!encoded.isReadable() && !decoded.isReadable(), "Unexpected trailing key packet data");
                } finally {
                    encoded.release();
                    decoded.release();
                }
            }
        }
        helper.succeed();
    }
}
