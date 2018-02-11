package net.novucs.dlac;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class DlacOutputStream extends DataOutputStream {

    public DlacOutputStream(OutputStream o) {
        super(o);
    }

    public void writeCombatSnippet(CombatSnippet snippet) throws IOException {
        writeInt(snippet.getCombatMode().getId());
        writeInt(snippet.getPacketHistory().size());
        for (PlayerPacket packet : snippet.getPacketHistory()) {
            writePlayerPacket(packet);
        }
    }

    public void writePlayerPacket(PlayerPacket packet) throws IOException {
        writeInt(packet.getType().getId());
        writeInt((int) packet.getTime());
        switch (packet.getType()) {
            case ATTACK:
                PlayerPacket.Attack attack = (PlayerPacket.Attack) packet;
                writeDouble(attack.getX());
                writeDouble(attack.getY());
                writeDouble(attack.getZ());
                writeDouble(attack.getYaw());
                writeDouble(attack.getPitch());
                break;
            case POSITION:
                PlayerPacket.Position position = (PlayerPacket.Position) packet;
                writeDouble(position.getX());
                writeDouble(position.getY());
                writeDouble(position.getZ());
                break;
            case LOOK:
                PlayerPacket.Look look = (PlayerPacket.Look) packet;
                writeDouble(look.getYaw());
                writeDouble(look.getPitch());
                break;
            case POSITION_LOOK:
                PlayerPacket.PositionLook positionLook = (PlayerPacket.PositionLook) packet;
                writeDouble(positionLook.getX());
                writeDouble(positionLook.getY());
                writeDouble(positionLook.getZ());
                writeDouble(positionLook.getYaw());
                writeDouble(positionLook.getPitch());
                break;
        }
    }
}
