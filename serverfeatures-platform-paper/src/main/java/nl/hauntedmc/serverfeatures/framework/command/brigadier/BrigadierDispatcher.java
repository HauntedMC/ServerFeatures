package nl.hauntedmc.serverfeatures.framework.command.brigadier;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import nl.hauntedmc.serverfeatures.ServerFeatures;
import nl.hauntedmc.serverfeatures.api.command.brigadier.BrigadierCommand;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

public class BrigadierDispatcher {

    private final ServerFeatures plugin;
    private volatile @Nullable CommandDispatcher<CommandSourceStack> dispatcher;

    private static Field F_CHILDREN;
    private static Field F_LITERALS;
    private static Field F_ARGUMENTS;

    private final ReentrantLock writeLock = new ReentrantLock();

    static {
        try {
            F_CHILDREN = CommandNode.class.getDeclaredField("children");
            F_LITERALS = CommandNode.class.getDeclaredField("literals");
            F_ARGUMENTS = CommandNode.class.getDeclaredField("arguments");
            F_CHILDREN.setAccessible(true);
            F_LITERALS.setAccessible(true);
            F_ARGUMENTS.setAccessible(true);
        } catch (Throwable ignored) {
        }
    }

    public BrigadierDispatcher(ServerFeatures plugin) {
        this.plugin = plugin;
    }

    /**
     * Resolve Brigadier dispatcher via CraftServer → MinecraftServer → Commands → dispatcher. Safe to call many times.
     */
    public void resolveDispatcher() {
        if (this.dispatcher != null) return;
        try {
            Object disp = getBrigadierDispatcher();

            if (disp instanceof CommandDispatcher<?> cd) {
                @SuppressWarnings("unchecked")
                CommandDispatcher<CommandSourceStack> cast = (CommandDispatcher<CommandSourceStack>) cd;
                this.dispatcher = cast;
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("[Brigadier] Failed to resolve dispatcher: " + t.getMessage());
        }
    }

    private Object getBrigadierDispatcher() throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        Object craftServer = plugin.getServer(); // CraftServer
        Method mGetServer = craftServer.getClass().getMethod("getServer"); // -> MinecraftServer
        Object mcServer = mGetServer.invoke(craftServer);
        Method mGetCmds = mcServer.getClass().getMethod("getCommands"); // -> command holder
        Object cmds = mGetCmds.invoke(mcServer);
        Method mGetDisp = cmds.getClass().getMethod("getDispatcher");
        return mGetDisp.invoke(cmds);
    }

    public void attachBrigadierCommand(BrigadierCommand cmd) {
        resolveDispatcher();
        final CommandDispatcher<CommandSourceStack> disp = this.dispatcher;
        if (disp == null) {
            plugin.getLogger().warning("[Brigadier] Dispatcher not available; cannot attach /" + cmd.name());
            return;
        }

        writeLock.lock();
        try {
            RootCommandNode<CommandSourceStack> root = disp.getRoot();

            if (root.getChild(cmd.name()) != null) {
                removeRootLiteral(disp, cmd.name());
            }
            for (String alias : cmd.aliases()) {
                if (root.getChild(alias) != null) {
                    removeRootLiteral(disp, alias);
                }
            }

            var node = cmd.buildTree();
            root.addChild(node);

            for (String alias : cmd.aliases()) {
                if (root.getChild(alias) == null) {
                    root.addChild(Commands.literal(alias).redirect(node).build());
                }
            }
        } finally {
            writeLock.unlock();
        }
    }

    public void detachBrigadierCommand(BrigadierCommand cmd) {
        resolveDispatcher();
        final CommandDispatcher<CommandSourceStack> disp = this.dispatcher;
        if (disp == null) {
            plugin.getLogger().warning("[Brigadier] Dispatcher not available; cannot detach /" + cmd.name());
            return;
        }

        writeLock.lock();
        try {
            boolean changed = removeRootLiteral(disp, cmd.name());
            for (String alias : cmd.aliases()) {
                changed |= removeRootLiteral(disp, alias);
            }
            if (!changed) {
                plugin.getLogger().info("[Brigadier] No dispatcher changes for /" + cmd.name() + " (already absent?)");
            }
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Removes a root literal by name from the dispatcher’s root node. Returns true if anything changed.
     */
    @SuppressWarnings("unchecked")
    public static boolean removeRootLiteral(CommandDispatcher<CommandSourceStack> dispatcher, String literal) {
        if (dispatcher == null || literal == null || literal.isEmpty() || F_CHILDREN == null) return false;
        RootCommandNode<CommandSourceStack> root = dispatcher.getRoot();
        try {
            Map<String, CommandNode<CommandSourceStack>> children =
                    (Map<String, CommandNode<CommandSourceStack>>) F_CHILDREN.get(root);
            Map<String, CommandNode<CommandSourceStack>> literals =
                    (Map<String, CommandNode<CommandSourceStack>>) F_LITERALS.get(root);
            Map<String, CommandNode<CommandSourceStack>> arguments =
                    (Map<String, CommandNode<CommandSourceStack>>) F_ARGUMENTS.get(root);

            boolean changed = false;
            changed |= (children.remove(literal) != null);
            changed |= (literals.remove(literal) != null);
            changed |= (arguments.remove(literal) != null); // usually empty for root literals, but be thorough
            return changed;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public @Nullable CommandDispatcher<CommandSourceStack> getDispatcher() {
        return dispatcher;
    }
}
