package com.autoblockparty.movement;

import com.autoblockparty.config.AutoBlockPartyConfig;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BlockPartyMover {
    private static final Logger LOGGER = LoggerFactory.getLogger("AutoBlockParty");

    private final AutoBlockPartyConfig config;
    private final BlockPos.Mutable searchPos = new BlockPos.Mutable();

    private boolean enabled;
    private BlockPos currentTarget = null;
    private int ticksSinceLastSearch = 0;

    public BlockPartyMover(AutoBlockPartyConfig config) {
        this.config = config;
        this.enabled = config.enabledOnStart;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void toggle(MinecraftClient client) {
        setEnabled(client, !this.enabled);
    }

    public void setEnabled(MinecraftClient client, boolean newState) {
        this.enabled = newState;
        this.currentTarget = null;
        this.ticksSinceLastSearch = 0;

        if (!this.enabled) {
            releaseAllKeys(client);
        }

        if (config.showActionbarMessages && client != null && client.player != null) {
            client.player.sendMessage(Text.literal("AutoBlockParty: " + (this.enabled ? "ON" : "OFF")), true);
        }
    }

    public void tick(MinecraftClient client) {
        if (client == null) {
            return;
        }

        ClientPlayerEntity player = client.player;
        ClientWorld world = client.world;

        if (player == null || world == null) {
            currentTarget = null;
            releaseAllKeys(client);
            return;
        }

        if (!enabled || client.currentScreen != null || player.isDead() || player.getHealth() <= 0.0f) {
            releaseAllKeys(client);
            return;
        }

        ItemStack heldStack = player.getInventory().getStack(player.getInventory().selectedSlot);
        if (heldStack.isEmpty() || !(heldStack.getItem() instanceof BlockItem blockItem)) {
            currentTarget = null;
            releaseAllKeys(client);
            return;
        }

        Block targetBlock = blockItem.getBlock();

        BlockPos under = BlockPos.ofFloored(player.getX(), player.getY() - 0.1, player.getZ());
        if (world.getBlockState(under).isOf(targetBlock)) {
            currentTarget = null;
            releaseAllKeys(client);
            if (config.debug) {
                LOGGER.info("Standing on matching block {}, stopping.", targetBlock);
            }
            return;
        }

        if (currentTarget != null && !world.getBlockState(currentTarget).isOf(targetBlock)) {
            currentTarget = null;
        }

        if (currentTarget == null) {
            ticksSinceLastSearch++;
            int interval = Math.max(1, config.searchIntervalTicks);
            if (ticksSinceLastSearch < interval) {
                releaseAllKeys(client);
                return;
            }
            ticksSinceLastSearch = 0;
            currentTarget = findNearestTarget(world, player, targetBlock, under.getY(), config.scanRadius);
        }

        if (currentTarget == null) {
            releaseAllKeys(client);
            return;
        }

        double dx = (currentTarget.getX() + 0.5) - player.getX();
        double dz = (currentTarget.getZ() + 0.5) - player.getZ();
        double horizontalDistSq = dx * dx + dz * dz;
        double horizontalDist = Math.sqrt(horizontalDistSq);

        double yawRad = Math.toRadians(player.getYaw());
        double sinYaw = Math.sin(yawRad);
        double cosYaw = Math.cos(yawRad);

        double forward = dz * cosYaw - dx * sinYaw;
        double strafe = -(dx * cosYaw + dz * sinYaw);

        double deadzone = config.deadzone;
        boolean pressForward = forward > deadzone;
        boolean pressBack = forward < -deadzone;
        boolean pressLeft = strafe > deadzone;
        boolean pressRight = strafe < -deadzone;

        boolean pressSprint = config.sprint && pressForward && horizontalDist > config.sprintMinDistance;

        boolean pressJump = false;
        if (horizontalDist >= 1.5) {
            if (config.autoJump && player.horizontalCollision && player.isOnGround()) {
                pressJump = true;
            }
            if (config.bunnyHop && pressSprint && horizontalDist > 5.0 && player.isOnGround()) {
                pressJump = true;
            }
        }

        GameOptions options = client.options;
        options.forwardKey.setPressed(pressForward);
        options.backKey.setPressed(pressBack);
        options.leftKey.setPressed(pressLeft);
        options.rightKey.setPressed(pressRight);
        options.sprintKey.setPressed(pressSprint);
        options.jumpKey.setPressed(pressJump);

        if (config.debug) {
            LOGGER.info("Target={} dist={} fwd={} str={} sprint={} jump={}",
                    currentTarget, String.format("%.2f", horizontalDist), pressForward, pressLeft, pressSprint, pressJump);
        }
    }

    private BlockPos findNearestTarget(ClientWorld world, ClientPlayerEntity player, Block targetBlock, int floorY, int radius) {
        int centerX = player.getBlockX();
        int centerZ = player.getBlockZ();
        double px = player.getX();
        double pz = player.getZ();

        BlockPos bestPos = null;
        double bestDistSq = Double.POSITIVE_INFINITY;

        for (int r = 0; r <= radius; r++) {
            boolean foundInRing = false;

            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (r > 0 && Math.abs(dx) != r && Math.abs(dz) != r) {
                        continue;
                    }

                    int x = centerX + dx;
                    int z = centerZ + dz;
                    searchPos.set(x, floorY, z);

                    BlockState state = world.getBlockState(searchPos);
                    if (state.isOf(targetBlock)) {
                        double cx = x + 0.5 - px;
                        double cz = z + 0.5 - pz;
                        double distSq = cx * cx + cz * cz;
                        if (distSq < bestDistSq) {
                            bestDistSq = distSq;
                            bestPos = searchPos.toImmutable();
                            foundInRing = true;
                        }
                    }
                }
            }

            if (foundInRing) {
                return bestPos;
            }
        }

        return null;
    }

    public void releaseAllKeys(MinecraftClient client) {
        if (client == null || client.options == null) {
            return;
        }
        GameOptions options = client.options;
        options.forwardKey.setPressed(false);
        options.backKey.setPressed(false);
        options.leftKey.setPressed(false);
        options.rightKey.setPressed(false);
        options.sprintKey.setPressed(false);
        options.jumpKey.setPressed(false);
    }
}