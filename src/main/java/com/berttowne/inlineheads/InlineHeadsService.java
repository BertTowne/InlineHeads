package com.berttowne.inlineheads;

import com.berttowne.inlineheads.injection.Service;
import com.google.auto.service.AutoService;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.inject.Singleton;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.event.Listener;

import javax.annotation.Nonnull;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.net.URL;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ExecutionException;

@Singleton
@SuppressWarnings("unused")
@AutoService({Service.class, Listener.class})
public class InlineHeadsService implements Service, Listener {

    private final LoadingCache<String, BufferedImage> headCache = CacheBuilder.newBuilder()
            .expireAfterAccess(Duration.of(10, ChronoUnit.MINUTES))
            .build(new CacheLoader<>() {
        @Nonnull
        @Override
        public BufferedImage load(@Nonnull String skullOwner) throws Exception {
            URL url = new URL("https://minotar.net/avatar/" + skullOwner + "/8.png");

            return ImageIO.read(url);
        }
    });

    public Component getHead(@Nonnull String skullOwner) {
        TextComponent.Builder component = Component.text("").toBuilder();

        try {
            BufferedImage image = headCache.get(skullOwner);

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
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }

        return component.build();
    }

}