package com.extendedclip.deluxemenus.packet;

import com.extendedclip.deluxemenus.DeluxeMenus;
import com.extendedclip.deluxemenus.action.ClickHandler;
import com.extendedclip.deluxemenus.menu.Menu;
import com.extendedclip.deluxemenus.menu.MenuHolder;
import com.extendedclip.deluxemenus.menu.MenuItem;
import com.extendedclip.deluxemenus.requirement.RequirementList;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClickWindow;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientCloseWindow;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Intercepts client-to-server inventory packets for packet-based DeluxeMenus.
 */
public class DeluxePacketListener extends PacketListenerAbstract {

    private final DeluxePacketMenuManager manager;
    private final DeluxeMenus plugin;

    private final Cache<UUID, Long> cache = CacheBuilder.newBuilder()
            .expireAfterWrite(75, TimeUnit.MILLISECONDS).build();
    private final Cache<UUID, Long> shiftCache = CacheBuilder.newBuilder()
            .expireAfterWrite(200, TimeUnit.MILLISECONDS).build();

    public DeluxePacketListener(final DeluxePacketMenuManager manager, final DeluxeMenus plugin) {
        super(PacketListenerPriority.LOWEST);
        this.manager = manager;
        this.plugin = plugin;
    }

    @Override
    public void onPacketReceive(final PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.CLICK_WINDOW) {
            handleClickWindow(event);
        } else if (event.getPacketType() == PacketType.Play.Client.CLOSE_WINDOW) {
            handleCloseWindow(event);
        }
    }

    private void handleClickWindow(final PacketReceiveEvent event) {
        final WrapperPlayClientClickWindow wrapper = new WrapperPlayClientClickWindow(event);
        final int windowId = wrapper.getWindowId();

        final Player player = (Player) event.getPlayer();
        if (player == null) return;

        final MenuHolder holder = manager.getTrackedHolder(player.getUniqueId());
        if (holder == null || !holder.isPacketMode()) return;
        if (holder.getPacketWindowId() != windowId) return;

        event.setCancelled(true);

        final int rawSlot = wrapper.getSlot();
        final int button = wrapper.getButton();
        final int mode = wrapper.getWindowClickType().ordinal();

        final int containerSize = holder.getMenu().map(m -> m.options().size()).orElse(holder.getInventory().getSize());
        final int menuSlot;
        if (rawSlot >= 0 && rawSlot < containerSize) {
            menuSlot = rawSlot;
        } else if (rawSlot >= containerSize && rawSlot < containerSize + 36) {
            menuSlot = rawSlot;
        } else {
            resyncAndReturn(player, holder, windowId, containerSize);
            return;
        }

        final MenuItem item = holder.getItem(menuSlot);
        if (item == null) {
            resyncAndReturn(player, holder, windowId, containerSize);
            return;
        }

        if (cache.getIfPresent(player.getUniqueId()) != null) {
            resyncAndReturn(player, holder, windowId, containerSize);
            return;
        }
        if (shiftCache.getIfPresent(player.getUniqueId()) != null) {
            resyncAndReturn(player, holder, windowId, containerSize);
            return;
        }

        if (mode == 6) {
            resyncAndReturn(player, holder, windowId, containerSize);
            return;
        }

        if (mode == 1 && button == 0) {
            shiftCache.put(player.getUniqueId(), System.currentTimeMillis());
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;

            final MenuHolder current = manager.getTrackedHolder(player.getUniqueId());
            if (current != holder) return;

            if (handleClick(player, holder, item.options().clickHandler(), item.options().clickRequirements())) {
                return;
            }

            if (mode == 1 && button == 0) {
                if (handleClick(player, holder, item.options().shiftLeftClickHandler(), item.options().shiftLeftClickRequirements())) {
                    return;
                }
            }

            if (mode == 1 && button == 1) {
                if (handleClick(player, holder, item.options().shiftRightClickHandler(), item.options().shiftRightClickRequirements())) {
                    return;
                }
            }

            if (mode == 0 && button == 0) {
                if (handleClick(player, holder, item.options().leftClickHandler(), item.options().leftClickRequirements())) {
                    return;
                }
            }

            if (mode == 0 && button == 1) {
                if (handleClick(player, holder, item.options().rightClickHandler(), item.options().rightClickRequirements())) {
                    return;
                }
            }

            if (mode == 3) {
                handleClick(player, holder, item.options().middleClickHandler(), item.options().middleClickRequirements());
            }
        });

        resyncAndReturn(player, holder, windowId, containerSize);
    }

    private void resyncAndReturn(final Player player, final MenuHolder holder, final int windowId, final int containerSize) {
        manager.resyncWindow(player, holder);
    }

    private boolean handleClick(final Player player, final MenuHolder holder,
                                final Optional<ClickHandler> handler, final Optional<RequirementList> requirements) {
        if (handler.isEmpty()) {
            return false;
        }

        if (requirements.isPresent()) {
            final ClickHandler denyHandler = requirements.get().getDenyHandler();

            if (!requirements.get().evaluate(holder)) {
                if (denyHandler != null) {
                    denyHandler.onClick(holder);
                }
                return true;
            }
        }

        cache.put(player.getUniqueId(), System.currentTimeMillis());
        handler.get().onClick(holder);
        return true;
    }

    private void handleCloseWindow(final PacketReceiveEvent event) {
        final WrapperPlayClientCloseWindow wrapper = new WrapperPlayClientCloseWindow(event);
        final int windowId = wrapper.getWindowId();

        final Player player = (Player) event.getPlayer();
        if (player == null) return;

        final MenuHolder holder = manager.getTrackedHolder(player.getUniqueId());
        if (holder == null || !holder.isPacketMode()) return;
        if (holder.getPacketWindowId() != windowId) return;

        Bukkit.getScheduler().runTask(plugin, () -> {
            Menu.closeMenu(plugin, player, false);
            manager.untrackHolder(player.getUniqueId());
            player.updateInventory();
        });
    }
}
