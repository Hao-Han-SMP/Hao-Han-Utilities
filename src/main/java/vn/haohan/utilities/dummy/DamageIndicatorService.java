package vn.haohan.utilities.dummy;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

public final class DamageIndicatorService {
    private final Plugin plugin;
    private final NamespacedKey indicatorTag;
    private final AtomicBoolean nextSideLeft = new AtomicBoolean(true);

    public DamageIndicatorService(Plugin plugin) {
        this.plugin = plugin;
        this.indicatorTag = new NamespacedKey(plugin, "damage_indicator");
    }

    public void spawnIndicator(Location baseLocation, double damage, boolean isCritical, boolean isProjectile) {
        spawnText(baseLocation, buildDamageText(formatDamage(damage), isCritical, isProjectile), isCritical);
    }

    public void spawnHeal(Location baseLocation, double amount) {
        spawnText(baseLocation, Component.text("+" + formatDamage(amount) + " ❤", NamedTextColor.GREEN, TextDecoration.BOLD), false);
    }

    public void spawnText(Location baseLocation, Component textComponent, boolean isCritical) {
        if (!plugin.getConfig().getBoolean("dummy.show-damage-indicator", true)) {
            return;
        }

        ThreadLocalRandom random = ThreadLocalRandom.current();
        boolean goLeft = nextSideLeft.getAndSet(!nextSideLeft.get());
        float sideSign = goLeft ? -1.0F : 1.0F;

        // Slight jitter around chest/shoulder height so consecutive hits feel organic
        double startY = 1.30 + (random.nextDouble() - 0.5) * 0.16;
        Location spawnLoc = baseLocation.clone().add(0.0, startY, 0.0);
        // Local horizontal X offset in billboard space (since Billboard.CENTER always faces the viewer,
        // local +X is always to the viewer's right and local -X is always to the viewer's left!)
        float sideDist = (0.72F + (float) random.nextDouble() * 0.24F) * sideSign;
        float peakY = 0.44F + (float) random.nextDouble() * 0.14F;
        float peakScale = isCritical ? 1.40F : 1.15F;
        float settleScale = isCritical ? 1.10F : 0.95F;

        spawnLoc.getWorld().spawn(spawnLoc, TextDisplay.class, display -> {
            display.getPersistentDataContainer().set(indicatorTag, PersistentDataType.BYTE, (byte) 1);
            display.text(textComponent);
            display.setBillboard(Display.Billboard.CENTER);
            display.setShadowed(true);
            display.setDefaultBackground(false);
            display.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));

            // Start slightly outside the dummy torso and pop out to the side
            display.setTextOpacity((byte) 28);
            display.setTransformation(new Transformation(
                    new Vector3f(sideSign * 0.34F, 0.0F, 0.0F),
                    new Quaternionf(),
                    new Vector3f(0.15F, 0.15F, 0.15F),
                    new Quaternionf()
            ));

            final int totalTicks = Math.max(18, plugin.getConfig().getInt("dummy.indicator-duration-ticks", 22));
            final int[] tick = {0};

            Bukkit.getScheduler().runTaskTimer(plugin, task -> {
                if (!display.isValid()) {
                    task.cancel();
                    return;
                }

                int t = tick[0]++;
                if (t > totalTicks) {
                    display.remove();
                    task.cancel();
                    return;
                }

                float progress = (float) t / (float) totalTicks;

                // Horizontal motion: fast ease-out from 0.34F out to sideDist
                float easeOut = 1.0F - (float) Math.pow(1.0F - progress, 2.4);
                float startX = sideSign * 0.34F;
                float curX = startX + (sideDist - startX) * easeOut;

                // Vertical motion: parabolic pop-up arc that peaks at progress ~ 0.45 and gently settles
                float curY = (float) Math.sin(progress * Math.PI * 0.82) * peakY + progress * 0.12F;

                // Scale & Opacity across 3 phases:
                // Phase 1 (0.00 - 0.22): Pop in & Fade in (opacity 28 -> 255, scale 0.15 -> peakScale)
                // Phase 2 (0.22 - 0.65): Settle & Hold (opacity 255, scale peakScale -> settleScale)
                // Phase 3 (0.65 - 1.00): Fade out & shrink (opacity 255 -> 26, scale settleScale -> 0.45)
                float curScale;
                int alpha;
                if (progress < 0.22F) {
                    float p = progress / 0.22F;
                    curScale = 0.15F + (peakScale - 0.15F) * p;
                    alpha = (int) (28 + (255 - 28) * p);
                } else if (progress < 0.65F) {
                    float p = (progress - 0.22F) / (0.65F - 0.22F);
                    curScale = peakScale - (peakScale - settleScale) * p;
                    alpha = 255;
                } else {
                    float p = (progress - 0.65F) / (1.00F - 0.65F);
                    curScale = settleScale - (settleScale - 0.45F) * p;
                    alpha = (int) (255 - (255 - 26) * p);
                }

                alpha = Math.max(26, Math.min(255, alpha));
                display.setTextOpacity((byte) alpha);
                display.setInterpolationDuration(1);
                display.setInterpolationDelay(0);
                display.setTransformation(new Transformation(
                        new Vector3f(curX, curY, 0.0F),
                        new Quaternionf(),
                        new Vector3f(curScale, curScale, curScale),
                        new Quaternionf()
                ));
            }, 0L, 1L);
        });
    }

    private String formatDamage(double damage) {
        if (Math.abs(damage - Math.round(damage)) < 0.05) {
            return String.valueOf(Math.round(damage));
        }
        return String.format(Locale.US, "%.1f", damage);
    }

    private Component buildDamageText(String damage, boolean isCritical, boolean isProjectile) {
        if (isCritical) {
            return Component.empty()
                    .append(Component.text("⚡ -", NamedTextColor.YELLOW, TextDecoration.BOLD))
                    .append(Component.text(damage, NamedTextColor.GOLD, TextDecoration.BOLD));
        }
        return Component.text("-" + damage, NamedTextColor.WHITE, TextDecoration.BOLD);
    }
}
