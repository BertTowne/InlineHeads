package com.berttowne.inlineheads.resourcepack;

import com.berttowne.inlineheads.InlineHeadsPlugin;
import com.berttowne.inlineheads.injection.Service;
import com.google.auto.service.AutoService;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.net.URI;
import java.net.URLConnection;
import java.security.MessageDigest;
import java.util.HexFormat;

@Singleton
@SuppressWarnings("unused")
@AutoService({Service.class, Listener.class})
public class ResourcePackService implements Service, Listener {

    private final InlineHeadsPlugin plugin;
    private final HexFormat hexFormat = HexFormat.of();

    private ResourcePackInfo resourcePackInfo;

    @Inject
    public ResourcePackService(InlineHeadsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onLoad() {
        // Load resource pack
        String rpUrl = plugin.getConfig().getString("resource-pack.url");
        String rpHash = plugin.getConfig().getString("resource-pack.hash");

        if (rpUrl == null) {
            plugin.getLogger().severe("** RESOURCE PACK URL NOT SET **");
            plugin.getServer().getPluginManager().disablePlugin(plugin);
            return;
        }

        if (plugin.getConfig().getBoolean("resource-pack.generate-hash") || rpHash == null) {
            plugin.getLogger().info("Generating resource pack hash...");

            try {
                rpHash = this.getHashFromUrl(rpUrl);
            } catch (Exception e) {
                plugin.getLogger().severe("** UNABLE TO GENERATE RESOURCE PACK HASH AUTOMATICALLY **");
                plugin.getServer().getPluginManager().disablePlugin(plugin);

                throw new RuntimeException(e);
            }
        }

        this.resourcePackInfo = ResourcePackInfo.resourcePackInfo()
                .uri(URI.create(rpUrl))
                .hash(rpHash)
                .build();
    }

    @EventHandler
    public void onPlayerJoin(@NotNull PlayerJoinEvent event) {
        event.getPlayer().sendResourcePacks(ResourcePackRequest.resourcePackRequest()
                .packs(resourcePackInfo)
                .required(true)
                .prompt(Component.text("\n")
                        .append(Component.text("RESOURCE PACK REQUIRED\n\n", NamedTextColor.RED, TextDecoration.BOLD))
                        .append(Component.text("For the best experience, we require the use of a\n", NamedTextColor.WHITE))
                        .append(Component.text("custom resource pack. Rejecting the resource\n", NamedTextColor.WHITE))
                        .append(Component.text("pack will result in you being kicked!", NamedTextColor.WHITE)))
                .build());
    }

    public ResourcePackInfo getResourcePack() {
        return resourcePackInfo;
    }

    public String getHashFromUrl(String url) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");

        final URLConnection urlConnection = new URI(url).toURL().openConnection();
        final int sizeInMB = urlConnection.getContentLength() / 1024 / 1024;
        final InputStream fis = urlConnection.getInputStream();

        int n = 0;
        byte[] buffer = new byte[8192];

        while (n != -1) {
            n = fis.read(buffer);
            if (n > 0) {
                digest.update(buffer, 0, n);
            }
        }

        fis.close();

        return hexFormat.formatHex(digest.digest());
    }

}