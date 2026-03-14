package com.extendedclip.deluxemenus.packet;

import com.extendedclip.deluxemenus.DeluxeMenus;
import com.extendedclip.deluxemenus.menu.MenuHolder;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerCloseWindow;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerOpenWindow;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetSlot;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWindowItems;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

public class DeluxePacketMenuManager {

    private final DeluxeMenus plugin;

    private final Map<UUID, MenuHolder> openHolders = new ConcurrentHashMap<>();

    private final AtomicInteger windowIdCounter;
    private final int minId;
    private final int maxId;

    private DeluxePacketListener packetListener;
    private boolean available = false;

    public DeluxePacketMenuManager(final @NotNull DeluxeMenus plugin) {
        this.plugin = plugin;

        final ServerVersion version = PacketEvents.getAPI().getServerManager().getVersion();
        if (version.isNewerThanOrEquals(ServerVersion.V_1_21_2)) {
            this.minId = -2147483648;
            this.maxId = 2147483647;
        } else {
            this.minId = 1;
            this.maxId = 127;
        }
        this.windowIdCounter = new AtomicInteger(minId);
    }

    public void init() {
        try {
            Class.forName("com.github.retrooper.packetevents.PacketEvents");
        } catch (ClassNotFoundException e) {
            plugin.getLogger().log(Level.WARNING, "[PacketGUI] PacketEvents not found, packet-based menus will be disabled.");
            return;
        }

        this.packetListener = new DeluxePacketListener(this, plugin);
        PacketEvents.getAPI().getEventManager().registerListener(packetListener);

        this.available = true;
        plugin.getLogger().log(Level.INFO, "[PacketGUI] Packet-based menu system initialized.");
    }

    public void disable() {
        for (final Map.Entry<UUID, MenuHolder> entry : openHolders.entrySet()) {
            final MenuHolder holder = entry.getValue();
            final Player player = holder.getViewer();
            if (player != null && player.isOnline()) {
                sendCloseWindow(player, holder.getPacketWindowId());
                player.updateInventory();
            }
        }
        openHolders.clear();
    }

    public boolean isAvailable() {
        return available;
    }

    public int nextWindowId() {
        final int next = windowIdCounter.incrementAndGet();
        if (next > maxId) {
            windowIdCounter.set(minId);
        }
        return windowIdCounter.get();
    }

    public void trackHolder(final @NotNull Player player, final @NotNull MenuHolder holder) {
        openHolders.put(player.getUniqueId(), holder);
    }

    public void untrackHolder(final @NotNull UUID uuid) {
        openHolders.remove(uuid);
    }

    public @Nullable MenuHolder getTrackedHolder(final @NotNull UUID uuid) {
        return openHolders.get(uuid);
    }

    public boolean isInPacketMenu(final @NotNull UUID uuid) {
        return openHolders.containsKey(uuid);
    }

    public void openPacketMenu(final @NotNull Player player,
                               final @NotNull MenuHolder holder,
                               final int containerSize,
                               final @NotNull String title) {
        final MenuHolder existing = openHolders.get(player.getUniqueId());
        if (existing != null) {
            sendCloseWindow(player, existing.getPacketWindowId());
            openHolders.remove(player.getUniqueId());
        }

        final int windowId = nextWindowId();
        final int rows = containerSize / 9;

        holder.setPacketMode(true);
        holder.setPacketWindowId(windowId);

        sendOpenWindow(player, windowId, rows, title);

        sendFullWindowItems(player, holder, windowId, containerSize);

        trackHolder(player, holder);
    }

    public void closePacketMenu(final @NotNull Player player, final boolean sendPacket) {
        final MenuHolder holder = openHolders.remove(player.getUniqueId());
        if (holder == null) return;

        if (sendPacket) {
            sendCloseWindow(player, holder.getPacketWindowId());
        }

        player.updateInventory();
    }

    public void resyncWindow(final @NotNull Player player, final @NotNull MenuHolder holder) {
        if (!holder.isPacketMode()) return;

        sendFullWindowItems(player, holder, holder.getPacketWindowId(),
                holder.getMenu().map(m -> m.options().size()).orElse(holder.getInventory().getSize()));

        sendSetSlot(player, -1, -1, null);
    }

    private static Constructor<WrapperPlayServerOpenWindow> cachedOpenWindowCtor;
    private static java.lang.reflect.Method cachedComponentText;

    public void sendOpenWindow(final @NotNull Player player, final int windowId, final int rows, final @NotNull String title) {
        final int menuTypeId = Math.max(0, Math.min(rows - 1, 5));

        try {
            if (cachedOpenWindowCtor == null) {
                for (final Constructor<?> ctor : WrapperPlayServerOpenWindow.class.getDeclaredConstructors()) {
                    final Class<?>[] params = ctor.getParameterTypes();
                    if (params.length == 3
                            && (params[0] == int.class || params[0] == Integer.class)
                            && (params[1] == int.class || params[1] == Integer.class)
                            && params[2].getName().contains("Component")) {
                        ctor.setAccessible(true);
                        @SuppressWarnings("unchecked")
                        final Constructor<WrapperPlayServerOpenWindow> typed = (Constructor<WrapperPlayServerOpenWindow>) ctor;
                        cachedOpenWindowCtor = typed;
                        cachedComponentText = params[2].getMethod("text", String.class);
                        break;
                    }
                }
            }

            if (cachedOpenWindowCtor == null) {
                final StringBuilder sb = new StringBuilder("[PacketGUI] Could not find WrapperPlayServerOpenWindow(int, int, Component) constructor. Available:");
                for (final Constructor<?> c : WrapperPlayServerOpenWindow.class.getDeclaredConstructors()) {
                    sb.append("\n  ").append(c);
                }
                plugin.getLogger().log(Level.SEVERE, sb.toString());
                return;
            }

            final Object titleComponent = cachedComponentText.invoke(null, title);
            final WrapperPlayServerOpenWindow packet = cachedOpenWindowCtor.newInstance(windowId, menuTypeId, titleComponent);
            PacketEvents.getAPI().getPlayerManager().sendPacket(player, packet);
        } catch (final Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[PacketGUI] Failed to send open window packet", e);
        }
    }

    private void sendFullWindowItems(final @NotNull Player player,
                                     final @NotNull MenuHolder holder,
                                     final int windowId,
                                     final int containerSize) {
        final int totalSlots = containerSize + 36;
        final List<com.github.retrooper.packetevents.protocol.item.ItemStack> items = new ArrayList<>(
                Collections.nCopies(totalSlots, com.github.retrooper.packetevents.protocol.item.ItemStack.EMPTY)
        );

        for (int i = 0; i < containerSize && i < holder.getInventory().getSize(); i++) {
            final ItemStack bukkit = holder.getInventory().getItem(i);
            if (bukkit != null) {
                items.set(i, SpigotConversionUtil.fromBukkitItemStack(bukkit));
            }
        }

        if (holder.getActiveItems() != null) {
            for (final var menuItem : holder.getActiveItems()) {
                final int slot = menuItem.options().slot();
                if (slot >= containerSize) {
                    final int playerInvSlot = slot - containerSize;
                    if (playerInvSlot >= 0 && playerInvSlot < 36) {
                        final ItemStack iStack = menuItem.getItemStack(holder);
                        if (iStack != null) {
                            items.set(containerSize + playerInvSlot,
                                    SpigotConversionUtil.fromBukkitItemStack(iStack));
                        }
                    }
                }
            }
        }

        final WrapperPlayServerWindowItems packet = new WrapperPlayServerWindowItems(
                windowId, 0, items,
                com.github.retrooper.packetevents.protocol.item.ItemStack.EMPTY
        );
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, packet);
    }

    public void sendSetSlot(final @NotNull Player player, final int windowId, final int slot, @Nullable final ItemStack item) {
        final com.github.retrooper.packetevents.protocol.item.ItemStack peItem = item != null
                ? SpigotConversionUtil.fromBukkitItemStack(item)
                : com.github.retrooper.packetevents.protocol.item.ItemStack.EMPTY;

        final WrapperPlayServerSetSlot packet = new WrapperPlayServerSetSlot(
                windowId, 0, slot, peItem
        );
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, packet);
    }

    public void sendCloseWindow(final @NotNull Player player, final int windowId) {
        final WrapperPlayServerCloseWindow packet = new WrapperPlayServerCloseWindow(windowId);
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, packet);
    }

    @NotNull
    DeluxeMenus getPlugin() {
        return plugin;
    }
}
