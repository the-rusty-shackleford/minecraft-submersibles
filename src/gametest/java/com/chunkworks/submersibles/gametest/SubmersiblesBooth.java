/*
 * Submersibles - a submarine protocol, layered on Vanilla Wheels.
 * Copyright (C) 2026 Rusty Shackleford and nfx
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.chunkworks.submersibles.gametest;

import com.chunkworks.submersibles.FitOutMenu;
import com.chunkworks.submersibles.Submarine;
import com.chunkworks.submersibles.SubmersiblesContent;
import com.chunkworks.submersibles.Torpedo;
import com.chunkworks.submersibles.client.FitOutScreen;
import com.chunkworks.vanillawheels.Vehicle;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The box submarine on film, in a glass tank of water in a flat world. In order:
 * <ol>
 * <li>floating at its draft at noon;</li>
 * <li>a real Left Shift dives it (the booth's player at the controls, Shift never letting them
 *     off), and let go it holds its depth;</li>
 * <li>the pilot, in survival, breathes at depth;</li>
 * <li>at night its lamps light the water ahead, with and without them;</li>
 * <li>first person;</li>
 * <li>the use key at the controls fires a torpedo down the tank;</li>
 * <li>R lets the pilot out at the hatch;</li>
 * <li>the fit-out with three upgrades, a tube and a dye, its readout;</li>
 * <li>the hold.</li>
 * </ol>
 * One {@code booth: PASS} or {@code booth: FAIL} line per check; the Gradle task reads them. Client
 * only, active only under {@code submersibles.photobooth}. Silent: the master volume is at nothing
 * from the first tick.
 */
@EventBusSubscriber(modid = GameTestMod.MOD_ID, value = Dist.CLIENT)
public final class SubmersiblesBooth {
    private SubmersiblesBooth() {}

    private static final Logger LOG = LoggerFactory.getLogger("Submersibles booth");
    private static final boolean ACTIVE = Boolean.getBoolean("submersibles.photobooth");

    private enum Phase { TITLE, LOADING, PLACING, RUNNING, DONE }

    private record Step(int at, Runnable action) {}

    private static final int HOLD = 100;
    /** The tank's inside: x and z 0..19, ten blocks of water over the ground. */
    private static final int SIZE = 20, DEPTH = 10;

    private static boolean muted = false;
    private static Phase phase = Phase.TITLE;
    private static int tick = 0;
    private static List<Step> steps;
    private static UUID subId;
    private static double ground;
    private static float brightOn = -1.0f;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!ACTIVE) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (!muted) {
            mc.options.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.MASTER).set(0.0);
            muted = true;
        }
        switch (phase) {
            case TITLE -> {
                if (mc.screen instanceof TitleScreen && mc.getOverlay() == null) {
                    phase = Phase.LOADING;
                    createWorld(mc);
                }
            }
            case LOADING -> {
                MinecraftServer server = mc.getSingleplayerServer();
                if (mc.level != null && mc.player != null && mc.screen == null && server != null
                        && mc.level.hasChunkAt(mc.player.blockPosition())) {
                    phase = Phase.PLACING;
                    mc.options.hideGui = true;
                    steps = plan(mc);
                    onServer(mc, SubmersiblesBooth::setUp);
                }
            }
            case PLACING -> {
                if (mc.player != null && client(mc) != null) {
                    phase = Phase.RUNNING;
                    tick = 0;
                }
            }
            case RUNNING -> {
                for (Step step : steps) {
                    if (step.at() == tick) {
                        step.action().run();
                    }
                }
                tick++;
            }
            case DONE -> { }
        }
    }

    private static void createWorld(Minecraft mc) {
        GameRules rules = new GameRules();
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
        rules.getRule(GameRules.RULE_RANDOMTICKING).set(0, null);
        LevelSettings settings = new LevelSettings("Submersibles booth", GameType.CREATIVE, false, Difficulty.PEACEFUL,
                true, rules, WorldDataConfiguration.DEFAULT);
        WorldOptions options = new WorldOptions(1L, false, false);
        mc.createWorldOpenFlows().createFreshLevel("submersibles-booth", settings, options,
                registries -> registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT)
                        .value().createWorldDimensions(),
                mc.screen);
    }

    /** Noon; the glass tank filled; the box submarine set down floating at its draft, facing south down the tank. */
    private static void setUp(ServerPlayer sp) {
        ServerLevel level = sp.serverLevel();
        level.setDayTime(6000L);
        ground = level.getMinBuildHeight() + 4;
        int g = (int) ground;
        for (int x = -1; x <= SIZE; x++) {
            for (int z = -1; z <= SIZE; z++) {
                boolean wall = x == -1 || z == -1 || x == SIZE || z == SIZE;
                for (int y = g; y < g + DEPTH + 1; y++) {
                    level.setBlock(new BlockPos(x, y, z), wall ? Blocks.GLASS.defaultBlockState()
                            : y < g + DEPTH ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
        // A spectator for the opening shots, which never falls: a creative flyer told to fly as it was moved
        // could take the flying while it still stood at the world's spawn, lose it on its next step (the game
        // lands a flyer that touches the ground) and drop into the pool, the camera under water. Boarding
        // makes it a survival player.
        sp.setGameMode(GameType.SPECTATOR);
        sp.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        double surface = g + DEPTH - 1 + 0.888;
        Vehicle v = Vehicle.create(level, Rigs.BOX_SUB, new Vec3(SIZE / 2.0, surface - 0.7 * 34.0 / 16.0, 4.5), 0.0f);
        if (!(v instanceof Submarine s)) {
            LOG.error("booth: FAIL the box sub is a submarine -- {}", v);
            throw new IllegalStateException("no submarine");
        }
        s.setFuel(s.tank().capacity());
        level.addFreshEntity(s);
        subId = s.getUUID();
        aim(sp, s.position().add(6.0, 4.0, 7.0), s.position().add(0.0, 0.8, 0.5));
    }

    /** effects: puts the booth's player at {@code from}, looking at {@code at} */
    private static void aim(ServerPlayer sp, Vec3 from, Vec3 at) {
        Vec3 d = at.subtract(from);
        float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z)));
        sp.teleportTo(sp.serverLevel(), from.x, from.y - sp.getEyeHeight(), from.z, yaw, pitch);
    }

    private static List<Step> plan(Minecraft mc) {
        List<Step> s = new ArrayList<>();
        int t = HOLD;
        double[] y = new double[2];
        s.add(new Step(t, () -> {
            Submarine c = client(mc);
            double wet = c == null ? -1.0 : c.submersion();
            verdict("the box submarine floats at its draft", () -> Math.abs(wet - 0.7) <= 0.1 ? null : "under water " + wet);
            int yellow = count(mc, SubmersiblesBooth::yellow);
            shoot(mc, "booth-floating");
            verdict("its yellow paint shows", () -> yellow > 3000 ? null : "yellow pixels " + yellow);
            onServer(mc, sp -> {
                sp.setGameMode(GameType.SURVIVAL);
                server(sp).ifPresent(sub -> sp.startRiding(sub, true));
            });
        }));
        s.add(new Step(t += 20, () -> {
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            Submarine c = client(mc);
            verdict("the booth's player takes the controls", () -> c != null && c.getControllingPassenger() == mc.player ? null : "riding " + mc.player.getVehicle());
            verdict("the boarding line names the get-out key, not Shift", () -> namesGetOut(mc));
            if (c != null) {
                // Look along the hull, the way it faces, a little down: the third-person camera follows the rider's eye.
                mc.player.setYRot(c.getYRot());
                mc.player.yRotO = c.getYRot();
                mc.player.setXRot(20.0f);
            }
            y[0] = c == null ? Double.NaN : c.getY();
            xkey("down", "Shift_L");
        }));
        s.add(new Step(t += 30, () -> shoot(mc, "booth-diving")));
        s.add(new Step(t += 40, () -> {
            Submarine c = client(mc);
            double dy = c == null ? Double.NaN : c.getY() - y[0];
            verdict("a real Left Shift dives it", () -> dy < -3.0 ? null : "moved " + dy);
            verdict("Shift held, the pilot stays aboard", () -> mc.player.getVehicle() == c ? null : "riding " + mc.player.getVehicle());
            xkey("up", "Shift_L");
        }));
        s.add(new Step(t += 30, () -> {
            Submarine c = client(mc);
            y[1] = c == null ? Double.NaN : c.getY();
        }));
        s.add(new Step(t += 40, () -> {
            Submarine c = client(mc);
            double dy = c == null ? Double.NaN : c.getY() - y[1];
            verdict("let go, it holds its depth", () -> Math.abs(dy) < 0.3 ? null : "moved " + dy);
            onServer(mc, sp -> sp.serverLevel().setDayTime(18000L));
        }));
        s.add(new Step(t += 60, () -> {
            verdict("the pilot breathes at depth", () -> mc.player.isEyeInFluid(net.minecraft.tags.FluidTags.WATER)
                    && mc.player.getAirSupply() >= mc.player.getMaxAirSupply() - 2 ? null : "air " + mc.player.getAirSupply() + ", eyes under water " + mc.player.isEyeInFluid(net.minecraft.tags.FluidTags.WATER));
            onServer(mc, sp -> server(sp).ifPresent(sub -> {
                for (int i = 0; i < Vehicle.Lights.values().length && sub.lights() != Vehicle.Lights.ON; i++) {
                    sub.cycleLights();
                }
            }));
        }));
        s.add(new Step(t += 60, () -> {
            brightOn = brightness(mc);
            shoot(mc, "booth-depth-lamps-on");
            onServer(mc, sp -> server(sp).ifPresent(sub -> {
                for (int i = 0; i < Vehicle.Lights.values().length && sub.lights() != Vehicle.Lights.OFF; i++) {
                    sub.cycleLights();
                }
            }));
        }));
        s.add(new Step(t += 60, () -> {
            float off = brightness(mc);
            shoot(mc, "booth-depth-lamps-off");
            verdict("at night under water its lamps light the water ahead", () -> brightOn > off * 1.3f + 2.0f ? null : "brightness on " + brightOn + ", off " + off);
            onServer(mc, sp -> server(sp).ifPresent(sub -> {
                for (int i = 0; i < Vehicle.Lights.values().length && sub.lights() != Vehicle.Lights.ON; i++) {
                    sub.cycleLights();
                }
                sub.fitOut().setItem(sub.weaponSlot(0), new ItemStack(SubmersiblesContent.TORPEDO_TUBE.get()));
                sub.getItemStacks().set(0, new ItemStack(SubmersiblesContent.TORPEDO_ITEM.get(), 4));
            }));
            mc.options.setCameraType(CameraType.FIRST_PERSON);
        }));
        s.add(new Step(t += 40, () -> {
            shoot(mc, "booth-first-person");
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            KeyMapping.click(mc.options.keyUse.getKey());
        }));
        s.add(new Step(t += 8, () -> {
            int torpedoes = torpedoes(mc);
            shoot(mc, "booth-torpedo");
            verdict("the use key at the controls fires a torpedo", () -> torpedoes == 1 ? null : "torpedoes under way " + torpedoes);
            xkey("down", "r");
        }));
        s.add(new Step(t += 4, () -> xkey("up", "r")));
        s.add(new Step(t += 20, () -> {
            Submarine c = client(mc);
            verdict("R lets the pilot out", () -> mc.player.getVehicle() == null ? null : "riding " + mc.player.getVehicle());
            int glass = 0;
            for (int x = 0; x < SIZE; x++) {
                for (int yy = (int) ground; yy < (int) ground + DEPTH; yy++) {
                    if (mc.level.getBlockState(new BlockPos(x, yy, SIZE)).is(Blocks.GLASS)) {
                        glass++;
                    }
                }
            }
            int whole = glass;
            verdict("the torpedo struck the tank's far wall and every pane stands", () -> whole == SIZE * DEPTH && torpedoes(mc) == 0
                    ? null : "panes " + whole + " of " + SIZE * DEPTH + ", torpedoes still under way " + torpedoes(mc));
            Vec3 hatch = c == null ? Vec3.ZERO : c.position().add(c.rotate(c.profile().localBlocks(c.submarine().hatch().orElseThrow())));
            double off = mc.player.position().distanceTo(hatch);
            verdict("at the hatch", () -> off < 1.5 ? null : "off by " + off);
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            onServer(mc, sp -> {
                sp.setGameMode(GameType.CREATIVE);
                sp.getAbilities().flying = true;
                sp.onUpdateAbilities();
                sp.serverLevel().setDayTime(6000L);
                server(sp).ifPresent(sub -> {
                    sub.fitOut().setItem(sub.upgradeSlot(0), new ItemStack(Items.BLAZE_ROD));
                    sub.fitOut().setItem(sub.upgradeSlot(1), new ItemStack(Items.SLIME_BALL));
                    sub.fitOut().setItem(sub.upgradeSlot(2), new ItemStack(Items.COMPASS));
                    sub.fitOut().setItem(sub.paintSlot(), new ItemStack(Items.RED_DYE));
                    aim(sp, sub.position().add(-4.0, 3.0, 2.0), sub.position().add(0.0, 0.8, 0.0));   // within the eight blocks a menu stays open
                    sub.openFitOut(sp);
                });
            });
        }));
        s.add(new Step(t += 30, () -> {
            boolean open = mc.screen instanceof FitOutScreen;
            FitOutMenu menu = open ? ((FitOutScreen) mc.screen).getMenu() : null;
            verdict("the fit-out opens", () -> open ? null : "screen " + mc.screen);
            verdict("its readout: a Nether and an Eco Engine make 120% speed and 55% fuel", () -> menu != null && menu.readout(FitOutMenu.SPEED) == 120
                    && menu.readout(FitOutMenu.FUEL) == 55 && menu.readout(FitOutMenu.INSTRUMENTS) == 1
                    ? null : menu == null ? "no menu" : "speed " + menu.readout(FitOutMenu.SPEED) + ", fuel " + menu.readout(FitOutMenu.FUEL) + ", instruments " + menu.readout(FitOutMenu.INSTRUMENTS));
            shoot(mc, "booth-fit-out");
            onServer(mc, sp -> {
                sp.closeContainer();
                server(sp).ifPresent(sub -> sub.openChest(sp, 0));
            });
        }));
        s.add(new Step(t += 30, () -> {
            boolean open = mc.screen instanceof ContainerScreen;
            verdict("the hold opens", () -> open ? null : "screen " + mc.screen);
            shoot(mc, "booth-hold");
            onServer(mc, ServerPlayer::closeContainer);
        }));
        s.add(new Step(t += 20, () -> {
            LOG.info("booth: PASS all checks ran");
            phase = Phase.DONE;
            mc.stop();
        }));
        return s;
    }

    /** effects: a yellow pixel: red and green high, blue well under them */
    private static boolean yellow(int rgb) {
        int r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, b = rgb & 0xFF;
        return r > 120 && g > 100 && b < Math.min(r, g) - 50;
    }

    /** effects: returns how many pixels of the frame's middle (rows 15..85 %, columns 10..90 %) satisfy {@code test} */
    private static int count(Minecraft mc, java.util.function.IntPredicate test) {
        try (NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            int n = 0;
            int w = image.getWidth(), h = image.getHeight();
            for (int y = (int) (h * .15); y < (int) (h * .85); y++) {
                for (int x = (int) (w * .1); x < (int) (w * .9); x++) {
                    int abgr = image.getPixelRGBA(x, y);
                    if (test.test((abgr & 0xFF) << 16 | (abgr >> 8 & 0xFF) << 8 | (abgr >> 16 & 0xFF))) {
                        n++;
                    }
                }
            }
            return n;
        }
    }

    /** effects: returns the mean brightness, 0..255, of the frame's middle third: what lamps ahead of a submarine light */
    private static float brightness(Minecraft mc) {
        try (NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            long sum = 0, n = 0;
            int w = image.getWidth(), h = image.getHeight();
            for (int y = h / 3; y < 2 * h / 3; y++) {
                for (int x = w / 3; x < 2 * w / 3; x++) {
                    int abgr = image.getPixelRGBA(x, y);
                    sum += (abgr & 0xFF) + (abgr >> 8 & 0xFF) + (abgr >> 16 & 0xFF);
                    n++;
                }
            }
            return n == 0 ? 0.0f : sum / (3.0f * n);
        }
    }

    // --- plumbing --------------------------------------------------------

    /**
     * effects: null if the line the game showed on boarding names the get-out key, else what it
     * said; the game's own line names Shift, which dives (Vanilla Wheels' D-0031)
     */
    @org.jetbrains.annotations.Nullable
    private static String namesGetOut(Minecraft mc) {
        String want = net.minecraft.network.chat.Component.translatable("mount.onboard",
                com.chunkworks.vanillawheels.client.Keys.GET_OUT.getTranslatedKeyMessage()).getString();
        String line;
        try {
            java.lang.reflect.Field f = net.minecraft.client.gui.Gui.class.getDeclaredField("overlayMessageString");
            f.setAccessible(true);
            line = f.get(mc.gui) instanceof net.minecraft.network.chat.Component c ? c.getString() : null;
        } catch (ReflectiveOperationException e) {
            line = "unreadable: " + e;
        }
        return want.equals(line) ? null : "line \"" + line + "\", want \"" + want + "\"";
    }

    /** effects: returns the client's submarine, or null */
    @org.jetbrains.annotations.Nullable
    private static Submarine client(Minecraft mc) {
        if (mc.level == null || subId == null) {
            return null;
        }
        for (var e : mc.level.entitiesForRendering()) {
            if (e.getUUID().equals(subId) && e instanceof Submarine s) {
                return s;
            }
        }
        return null;
    }

    /** effects: returns how many torpedoes this client sees under way */
    private static int torpedoes(Minecraft mc) {
        int n = 0;
        if (mc.level != null) {
            for (var e : mc.level.entitiesForRendering()) {
                if (e instanceof Torpedo) {
                    n++;
                }
            }
        }
        return n;
    }

    /** effects: returns the server's submarine, if it is there */
    private static java.util.Optional<Submarine> server(ServerPlayer sp) {
        return sp.serverLevel().getEntity(subId) instanceof Submarine s ? java.util.Optional.of(s) : java.util.Optional.empty();
    }

    private static void onServer(Minecraft mc, Consumer<ServerPlayer> action) {
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null || mc.player == null) {
            return;
        }
        server.execute(() -> {
            ServerPlayer sp = server.getPlayerList().getPlayer(mc.player.getUUID());
            if (sp != null) {
                action.accept(sp);
            }
        });
    }

    /**
     * effects: starts devtools/booth/xkey.py pressing ({@code down}) or letting go ({@code up}) of
     * {@code keysym} on this client's display, and returns it; null, with a FAIL line, if it would
     * not start. The helper refuses a display with a window manager, so it never types on a desktop.
     */
    @org.jetbrains.annotations.Nullable
    private static Process xkey(String action, String keysym) {
        Path script = Path.of(System.getProperty("user.dir"), "..", "..", "devtools", "booth", "xkey.py").normalize();
        Path uv = Path.of(System.getProperty("user.home"), ".local", "bin", "uv");
        try {
            return new ProcessBuilder(Files.isExecutable(uv) ? uv.toString() : "uv", "run", "--no-project", "--with", "python-xlib",
                    "python", script.toString(), action, keysym).inheritIO().start();
        } catch (IOException e) {
            LOG.error("booth: FAIL the key helper starts ({} {}) -- {}", action, keysym, e.toString());
            return null;
        }
    }

    private static void shoot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(),
                message -> LOG.info("booth: {}", message.getString()));
    }

    /** Runs {@code check}; null is a pass, anything else the failure's detail. */
    private static void verdict(String what, Supplier<String> check) {
        String detail;
        try {
            detail = check.get();
        } catch (RuntimeException e) {
            detail = e.toString();
        }
        if (detail == null) {
            LOG.info("booth: PASS {}", what);
        } else {
            LOG.error("booth: FAIL {} -- {}", what, detail);
        }
    }
}
