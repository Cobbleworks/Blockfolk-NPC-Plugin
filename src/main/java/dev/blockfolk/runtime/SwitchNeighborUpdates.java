package dev.blockfolk.runtime;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.logging.Logger;

import org.bukkit.block.Block;

/**
 * Sends the second neighbor update that vanilla switches send at their support
 * block.
 */
final class SwitchNeighborUpdates {

    private static boolean warningLogged;

    private SwitchNeighborUpdates() {
    }

    static void notifyNeighbors(Block source, Block support, Logger logger) {
        try {
            // Bukkit's setBlockData updates neighbors of the switch, but it does not
            // update neighbors of the block the switch is attached to. Reapplying
            // that block's unchanged state does not produce a neighbor update.
            Object level = source.getWorld().getClass().getMethod("getHandle").invoke(source.getWorld());
            ClassLoader serverLoader = level.getClass().getClassLoader();
            Class<?> blockPosClass = Class.forName("net.minecraft.core.BlockPos", true, serverLoader);
            Class<?> nmsBlockClass = Class.forName("net.minecraft.world.level.block.Block", true, serverLoader);
            Constructor<?> blockPosConstructor = blockPosClass.getConstructor(int.class, int.class, int.class);
            Object sourcePos = blockPosConstructor.newInstance(source.getX(), source.getY(), source.getZ());
            Object supportPos = blockPosConstructor.newInstance(support.getX(), support.getY(), support.getZ());
            Object sourceState = level.getClass().getMethod("getBlockState", blockPosClass).invoke(level, sourcePos);
            Object sourceBlock = sourceState.getClass().getMethod("getBlock").invoke(sourceState);
            Method updateNeighbors = level.getClass().getMethod("updateNeighborsAt", blockPosClass, nmsBlockClass);
            updateNeighbors.invoke(level, supportPos, sourceBlock);
        } catch (ReflectiveOperationException | SecurityException exception) {
            if (!warningLogged) {
                warningLogged = true;
                logger.warning("Could not update redstone around a switch's support block: " + exception);
            }
        }
    }
}
