package net.novucs.dlac;

import lombok.Data;

public interface PlayerPacket {

    static AttackPlayerPacket attack(long time, double x, double y, double z, double yaw, double pitch) {
        return new AttackPlayerPacket(time, x, y, z, yaw, pitch);
    }

    static PositionPlayerPacket position(long time, double x, double y, double z) {
        return new PositionPlayerPacket(time, x, y, z);
    }

    static LookPlayerPacket look(long time, double yaw, double pitch) {
        return new LookPlayerPacket(time, yaw, pitch);
    }

    static PositionLookPlayerPacket positionLook(long time, double x, double y, double z, double yaw, double pitch) {
        return new PositionLookPlayerPacket(time, x, y, z, yaw, pitch);
    }

    enum Type {
        ATTACK, POSITION, LOOK, POSITION_LOOK
    }

    @Data
    class AttackPlayerPacket implements PlayerPacket {
        private final Type type = Type.ATTACK;
        private final long time;
        private final double x, y, z, yaw, pitch;
    }

    @Data
    class PositionPlayerPacket implements PlayerPacket {
        private final Type type = Type.POSITION;
        private final long time;
        private final double x, y, z;
    }

    @Data
    class LookPlayerPacket implements PlayerPacket {
        private final Type type = Type.LOOK;
        private final long time;
        private final double yaw, pitch;
    }

    @Data
    class PositionLookPlayerPacket implements PlayerPacket {
        private final Type type = Type.POSITION_LOOK;
        private final long time;
        private final double x, y, z, yaw, pitch;
    }
}
