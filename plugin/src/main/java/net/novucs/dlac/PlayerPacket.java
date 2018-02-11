package net.novucs.dlac;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;

public interface PlayerPacket {

    static Attack attack(long time, double x, double y, double z, double yaw, double pitch) {
        return new Attack(time, x, y, z, yaw, pitch);
    }

    static Position position(long time, double x, double y, double z) {
        return new Position(time, x, y, z);
    }

    static Look look(long time, double yaw, double pitch) {
        return new Look(time, yaw, pitch);
    }

    static PositionLook positionLook(long time, double x, double y, double z, double yaw, double pitch) {
        return new PositionLook(time, x, y, z, yaw, pitch);
    }

    Type getType();
    long getTime();

    @AllArgsConstructor
    @Getter
    enum Type {
        ATTACK(0),
        POSITION(1),
        LOOK(2),
        POSITION_LOOK(3);
        private final int id;
    }

    @Data
    class Attack implements PlayerPacket {
        private final Type type = Type.ATTACK;
        private final long time;
        private final double x, y, z, yaw, pitch;
    }

    @Data
    class Position implements PlayerPacket {
        private final Type type = Type.POSITION;
        private final long time;
        private final double x, y, z;
    }

    @Data
    class Look implements PlayerPacket {
        private final Type type = Type.LOOK;
        private final long time;
        private final double yaw, pitch;
    }

    @Data
    class PositionLook implements PlayerPacket {
        private final Type type = Type.POSITION_LOOK;
        private final long time;
        private final double x, y, z, yaw, pitch;
    }
}
