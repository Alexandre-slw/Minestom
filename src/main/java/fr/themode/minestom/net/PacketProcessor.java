package fr.themode.minestom.net;

import fr.themode.minestom.MinecraftServer;
import fr.themode.minestom.entity.Player;
import fr.themode.minestom.net.packet.PacketReader;
import fr.themode.minestom.net.packet.client.ClientPlayPacket;
import fr.themode.minestom.net.packet.client.ClientPreplayPacket;
import fr.themode.minestom.net.packet.client.handler.ClientLoginPacketsHandler;
import fr.themode.minestom.net.packet.client.handler.ClientPlayPacketsHandler;
import fr.themode.minestom.net.packet.client.handler.ClientStatusPacketsHandler;
import fr.themode.minestom.net.packet.client.handshake.HandshakePacket;
import fr.themode.minestom.net.player.PlayerConnection;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PacketProcessor {

    private Map<ChannelHandlerContext, PlayerConnection> connectionPlayerConnectionMap = new ConcurrentHashMap<>();

    private ConnectionManager connectionManager;

    // Protocols
    private ClientStatusPacketsHandler statusPacketsHandler;
    private ClientLoginPacketsHandler loginPacketsHandler;
    private ClientPlayPacketsHandler playPacketsHandler;

    public PacketProcessor() {
        this.connectionManager = MinecraftServer.getConnectionManager();

        this.statusPacketsHandler = new ClientStatusPacketsHandler();
        this.loginPacketsHandler = new ClientLoginPacketsHandler();
        this.playPacketsHandler = new ClientPlayPacketsHandler();
    }

    private List<Integer> printBlackList = Arrays.asList(17, 18, 19);

    public void process(ChannelHandlerContext channel, ByteBuf buffer, int id, int length, int offset) {
        PlayerConnection playerConnection = connectionPlayerConnectionMap.computeIfAbsent(channel, c -> new PlayerConnection(channel));
        ConnectionState connectionState = playerConnection.getConnectionState();
        //if (!printBlackList.contains(id)) {
        // System.out.println("RECEIVED ID: 0x" + Integer.toHexString(id) + " State: " + connectionState);
        //}

        PacketReader packetReader = new PacketReader(buffer, length);

        if (connectionState == ConnectionState.UNKNOWN) {
            // Should be handshake packet
            if (id == 0) {
                HandshakePacket handshakePacket = new HandshakePacket();
                handshakePacket.read(packetReader);
                handshakePacket.process(playerConnection, connectionManager);
            }
            return;
        }

        switch (connectionState) {
            case PLAY:
                Player player = connectionManager.getPlayer(playerConnection);
                ClientPlayPacket playPacket = (ClientPlayPacket) playPacketsHandler.getPacketInstance(id);
                playPacket.read(packetReader);

                player.addPacketToQueue(playPacket);
                break;
            case LOGIN:
                ClientPreplayPacket loginPacket = (ClientPreplayPacket) loginPacketsHandler.getPacketInstance(id);
                loginPacket.read(packetReader);

                loginPacket.process(playerConnection, connectionManager);
                break;
            case STATUS:
                ClientPreplayPacket statusPacket = (ClientPreplayPacket) statusPacketsHandler.getPacketInstance(id);
                statusPacket.read(packetReader);

                statusPacket.process(playerConnection, connectionManager);
                break;
            case UNKNOWN:
                // Ignore packet (unexpected)
                break;
        }
    }

    public PlayerConnection getPlayerConnection(ChannelHandlerContext channel) {
        return connectionPlayerConnectionMap.get(channel);
    }

    public boolean hasPlayerConnection(ChannelHandlerContext channel) {
        return connectionPlayerConnectionMap.containsKey(channel);
    }

    public void removePlayerConnection(ChannelHandlerContext channel) {
        connectionPlayerConnectionMap.remove(channel);
    }
}
