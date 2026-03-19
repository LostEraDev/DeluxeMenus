package com.extendedclip.deluxemenus.velocity;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import org.slf4j.Logger;

import com.google.inject.Inject;

@Plugin(
        id = "deluxemenus-velocity",
        name = "DeluxeMenus-Velocity",
        version = "1.0.0",
        description = "Companion plugin for DeluxeMenus - executes proxy commands from [proxy] action",
        authors = {"LostEra"}
)
public class DeluxeMenusVelocity {

    private static final MinecraftChannelIdentifier CHANNEL =
            MinecraftChannelIdentifier.from("deluxemenus:proxy");

    private final ProxyServer server;
    private final Logger logger;

    @Inject
    public DeluxeMenusVelocity(final ProxyServer server, final Logger logger) {
        this.server = server;
        this.logger = logger;
    }

    @Subscribe
    public void onProxyInitialization(final ProxyInitializeEvent event) {
        server.getChannelRegistrar().register(CHANNEL);
        logger.info("DeluxeMenus-Velocity loaded - listening on channel deluxemenus:proxy");
    }

    @Subscribe
    public void onProxyShutdown(final ProxyShutdownEvent event) {
        server.getChannelRegistrar().unregister(CHANNEL);
    }

    @Subscribe
    public void onPluginMessage(final PluginMessageEvent event) {
        if (!event.getIdentifier().equals(CHANNEL)) {
            return;
        }

        event.setResult(PluginMessageEvent.ForwardResult.handled());

        if (!(event.getTarget() instanceof Player player)) {
            return;
        }

        try {
            final ByteArrayDataInput in = ByteStreams.newDataInput(event.getData());
            final String subchannel = in.readUTF();

            if (!"ProxyCommand".equals(subchannel)) {
                return;
            }

            final String command = in.readUTF();
            logger.info("Executing proxy command '{}' for player {}", command, player.getUsername());

            server.getCommandManager().executeAsync(player, command);
        } catch (final Exception e) {
            logger.error("Failed to process proxy command for player",  e);
        }
    }
}
