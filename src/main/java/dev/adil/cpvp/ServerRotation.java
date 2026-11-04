package dev.adil.cpvp;

import java.util.function.Supplier;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.phys.Vec3;

/** Sends a temporary server-facing rotation without changing the local camera. */
public final class ServerRotation {
    private ServerRotation() {}

    public static <T> T runFacing(Minecraft client, Vec3 target, Supplier<T> action) {
        if (client == null || client.player == null || client.player.connection == null
                || target == null) {
            return action.get();
        }

        var player = client.player;
        float previousYaw = player.getYRot();
        float previousPitch = player.getXRot();
        Vec3 eye = player.getEyePosition();
        double dx = target.x - eye.x;
        double dy = target.y - eye.y;
        double dz = target.z - eye.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float targetYaw = (float)(Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float targetPitch = (float)-Math.toDegrees(Math.atan2(dy, horizontal));

        player.connection.send(new ServerboundMovePlayerPacket.Rot(
                targetYaw, targetPitch, player.onGround(), player.horizontalCollision));
        try {
            return action.getAsBoolean();
        } finally {
            player.connection.send(new ServerboundMovePlayerPacket.Rot(
                    previousYaw, previousPitch, player.onGround(), player.horizontalCollision));
        }
    }
}
