package com.berttowne.inlineheads.resourcepack;

import com.berttowne.inlineheads.InlineHeadsPlugin;
import com.berttowne.inlineheads.injection.Service;
import com.berttowne.inlineheads.util.TriConsumer;
import com.google.auto.service.AutoService;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

import javax.annotation.Nonnull;
import java.io.*;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Set;
import java.util.UUID;

@Singleton
@SuppressWarnings("unused")
@AutoService({Service.class, Listener.class})
public class ResourcePackService implements Service, Listener {

    private static final char[] hexCode = "0123456789ABCDEF".toCharArray();

    private static int hexToBin(char ch) {
        if ('0' <= ch && ch <= '9')
            return ch - '0';

        if ('A' <= ch && ch <= 'F')
            return ch - 'A' + 10;

        if ('a' <= ch && ch <= 'f')
            return ch - 'a' + 10;

        return -1;
    }

    private final InlineHeadsPlugin plugin;
    private final Set<UUID> pendingRP;

    private ResourcePack resourcePack;

    @Inject
    public ResourcePackService(InlineHeadsPlugin plugin) {
        this.plugin = plugin;
        this.pendingRP = Sets.newHashSet();
    }

    @Override
    public void onLoad() {
        // Load resource pack
        String rpUrl = plugin.getConfig().getString("resource-pack.url");
        String rpHash = plugin.getConfig().getString("resource-pack.hash");

        if (plugin.getConfig().getBoolean("resource-pack.generate-hash")) {
            plugin.getLogger().info("Generating resource pack hash...");

            try {
                rpHash = this.getHashFromUrl(rpUrl);
            } catch (Exception e) {
                plugin.getLogger().severe("** UNABLE TO GENERATE RESOURCE PACK HASH AUTOMATICALLY **");
                plugin.getServer().shutdown();

                throw new RuntimeException(e);
            }
        }

        try {
            this.performPackCheck(rpUrl, rpHash, (urlHash, configHash, match) -> {
                if (!match) {
                    plugin.getLogger().severe("** HASH DOESN'T MATCH PROVIDED URL **");
                    plugin.getLogger().severe("URL Hash Returned: " + urlHash);
                    plugin.getLogger().severe("Config-Provided Hash: " + configHash);
                    plugin.getServer().shutdown();
                }
            });
        } catch (Exception e) {
            plugin.getLogger().severe("** INVALID RESOURCE PACK HASH **");
            plugin.getServer().shutdown();

            throw new RuntimeException(e);
        }

        this.resourcePack = new ResourcePack(rpUrl, rpHash);
    }

    @EventHandler
    @SuppressWarnings("deprecation")
    public void onPlayerResourcePackStatus(@Nonnull PlayerResourcePackStatusEvent event) {
        final Player player = event.getPlayer();

        if (pendingRP.contains(player.getUniqueId())) {
            final PlayerResourcePackStatusEvent.Status status = event.getStatus();

            plugin.getLogger().info(status.name());

            switch (status) {
                case DECLINED -> player.kickPlayer(
                        ChatColor.RED + "" + ChatColor.BOLD + """
                                RESOURCE PACK REQUIRED!
                                                        
                                """ + ChatColor.WHITE + """
                                For the best experience, we require the use
                                of our custom resource pack!""");

                case FAILED_DOWNLOAD -> player.kickPlayer(
                        ChatColor.RED + "" + ChatColor.BOLD + """
                                RESOURCE PACK REQUIRED!
                                                        
                                """ + ChatColor.WHITE + """
                                There was an error attempting to download
                                the custom resource pack!""");

                case SUCCESSFULLY_LOADED -> player.sendMessage(Component.text("Resource pack loaded!").color(NamedTextColor.GREEN));
            }
        }
    }

    @EventHandler
    public void onPlayerJoin(@Nonnull PlayerJoinEvent event) {
        pendingRP.add(event.getPlayer().getUniqueId());

        Bukkit.getScheduler().runTask(this.plugin, () -> {
            if (pendingRP.contains(event.getPlayer().getUniqueId())) {
                sendResourcePack(event.getPlayer().getUniqueId(), this.getResourcePack());
            }
        });
    }

    @EventHandler
    public void onPlayerQuit(@Nonnull PlayerQuitEvent event) {
        pendingRP.remove(event.getPlayer().getUniqueId());
    }

    public ResourcePack getResourcePack() {
        return resourcePack;
    }

    public void sendResourcePack(UUID uuid, ResourcePack resourcePack) {
        final Player player = Bukkit.getPlayer(uuid);

        if (player == null) return;

        player.setResourcePack(resourcePack.url(), parseHexBinary(resourcePack.hash()), Component
                .text("\n").append(Component.text("RESOURCE PACK REQUIRED\n\n").color(NamedTextColor.RED).decorate(TextDecoration.BOLD))
                .append(Component.text("For the best experience, we require the use of a\n").color(NamedTextColor.WHITE)
                        .append(Component.text("custom resource pack. Rejecting the resource\n").color(NamedTextColor.WHITE)
                                .append(Component.text("pack will result in you being kicked!").color(NamedTextColor.WHITE))))
                .asComponent(), true);
    }

    public String getHashFromUrl(String url) throws Exception {
        // This is not done async on purpose. We don't want the server to start without having checked this first.
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        InputStream fis = new URL(url).openStream();
        int n = 0;
        byte[] buffer = new byte[8192];

        while (n != -1) {
            n = fis.read(buffer);
            if (n > 0) {
                digest.update(buffer, 0, n);
            }
        }

        fis.close();
        final byte[] urlBytes = digest.digest();

        return printHexBinary(urlBytes);
    }

    public byte[] parseHexBinary(@Nonnull String s) {
        final int len = s.length();

        // "111" is not a valid hex encoding.
        if (len % 2 != 0)
            throw new IllegalArgumentException("hexBinary needs to be even-length: " + s);

        byte[] out = new byte[len / 2];

        for (int i = 0; i < len; i += 2) {
            int h = hexToBin(s.charAt(i));
            int l = hexToBin(s.charAt(i + 1));

            if (h == -1 || l == -1)
                throw new IllegalArgumentException("contains illegal character for hexBinary: " + s);

            out[i / 2] = (byte) (h * 16 + l);
        }

        return out;
    }

    public String printHexBinary(@Nonnull byte[] data) {
        StringBuilder r = new StringBuilder(data.length * 2);

        for (byte b : data) {
            r.append(hexCode[(b >> 4) & 0xF]);
            r.append(hexCode[(b & 0xF)]);
        }

        return r.toString();
    }

    public void performPackCheck(String url, String hash, @Nonnull TriConsumer<String, String, Boolean> consumer) throws Exception {
        // This is not done async on purpose. We don't want the server to start without having checked this first.
        final String urlCheckHash = getHashFromUrl(url);

        consumer.accept(urlCheckHash, hash, urlCheckHash.equalsIgnoreCase(hash));
    }

}