package com.berttowne.inlineheads;

import com.berttowne.inlineheads.injection.Service;
import com.google.auto.service.AutoService;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.github.miniplaceholders.api.Expansion;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.tag.Tag;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.net.URI;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ExecutionException;

@Singleton
@SuppressWarnings("unused")
@AutoService({Service.class, Listener.class})
public class InlineHeadsService implements Service, Listener {

    /**
     * A cache of player heads from minotar.net, with a 10 minute expiry to avoid spamming the service.
     */
    private final LoadingCache<String, Component> headCache = CacheBuilder.newBuilder()
            .expireAfterAccess(Duration.of(10, ChronoUnit.MINUTES))
            .build(new CacheLoader<>() {
        @NotNull
        @Override
        public Component load(@NotNull String skullOwner) throws Exception {
            final URI uri = new URI("https://minotar.net/avatar/" + skullOwner + "/8.png");
            final BufferedImage image = ImageIO.read(uri.toURL());
            final TextComponent.Builder component = Component.text("").toBuilder();

            for (int i = 1; i <= 64; i++) {
                int row = i == 64 ? 0 : 7 - (i / 8);
                int col = i == 64 ? 7 : (i - 1) % 8;

                if (col == 7 && i < 64) row++;

                component.append(Component.translatable("pixel.eighth-" + i).font(Key.key("pixelized", "pixelized")).color(TextColor.color(image.getRGB(col, row))));
                component.append(Component.translatable("space.-" + ((i % 8) + 1)));

                if (i >= 8 && i % 8 == 0 && i != 64) {
                    component.append(Component.translatable("space.-8"));
                }
            }

            return component.build();
        }
    });

    @Inject private InlineHeadsPlugin plugin;

    @Override
    public void onLoad() {
        if (!plugin.getServer().getPluginManager().isPluginEnabled("MiniPlaceholders")) {
            plugin.getLogger().warning("MiniPlaceholders is not installed! InlineHeads will only serve as a developer API.");
            return;
        }

        Expansion.builder("player")
                .globalPlaceholder("head", (args, context) -> {
                    String skullOwner = args.popOr("player name expected").value();

                    try {
                        return Tag.inserting(getHead(skullOwner));
                    } catch (ExecutionException e) {
                        throw new RuntimeException(e);
                    }
                }).build().register();
    }

    /**
     * Get a component representing the head of the given player.
     * <b>WARNING:</b> This method will block the current thread while it fetches the head from minotar.net if it isn't already cached, so it is recommended to call this method asynchronously.
     *
     * @param skullOwner The name of the player to get the head of.
     * @return A component representing the head of the given player.
     * @throws ExecutionException If the head could not be fetched from minotar.net.
     */
    @NotNull
    public Component getHead(@NotNull String skullOwner) throws ExecutionException {
        return headCache.get(skullOwner);
    }

}