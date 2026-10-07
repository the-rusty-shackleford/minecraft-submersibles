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
package com.chunkworks.submersibles;

import com.chunkworks.submersibles.api.Submersibles;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Every game object the protocol registers: the entity type that makes its submarines, the torpedo,
 * the Torpedo Tube and the torpedo item, the component a packed submarine keeps its fit-out in, and
 * the fit-out's menu. The submarine type's own size is a placeholder: a vehicle sizes itself from
 * its profile, as every Vanilla Wheels vehicle does.
 */
public final class SubmersiblesContent {
    private SubmersiblesContent() {}

    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, Submersibles.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Submersibles.MOD_ID);
    private static final DeferredRegister<DataComponentType<?>> COMPONENTS = DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, Submersibles.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, Submersibles.MOD_ID);
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Submersibles.MOD_ID);

    /** Every submarine: which one, its Vanilla Wheels profile says; how it dives, its submarine profile. */
    public static final DeferredHolder<EntityType<?>, EntityType<Submarine>> SUBMARINE = ENTITIES.register("submarine",
            () -> EntityType.Builder.<Submarine>of(Submarine::new, MobCategory.MISC)
                    .sized(1.5f, 1.0f)
                    .clientTrackingRange(12)
                    .updateInterval(2)
                    .build("submarine"));

    /** A torpedo under way. */
    public static final DeferredHolder<EntityType<?>, EntityType<Torpedo>> TORPEDO = ENTITIES.register("torpedo",
            () -> EntityType.Builder.<Torpedo>of(Torpedo::new, MobCategory.MISC)
                    .sized(0.4f, 0.4f)
                    .clientTrackingRange(8)
                    .updateInterval(1)
                    .build("torpedo"));

    /** A packed submarine's fit-out: its upgrade slots, its weapon slots and its paint slot, in that order. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ItemContainerContents>> FITTED = COMPONENTS.register("fitted",
            () -> DataComponentType.<ItemContainerContents>builder().persistent(ItemContainerContents.CODEC)
                    .networkSynchronized(ItemContainerContents.STREAM_CODEC).cacheEncoding().build());

    /** The Torpedo Tube: fitted in a weapon slot, it fires torpedoes from the hold (D-0003). */
    public static final DeferredItem<Item> TORPEDO_TUBE = ITEMS.registerSimpleItem("torpedo_tube", new Item.Properties().stacksTo(1));

    /** A torpedo, a tube's round, carried in the hold. */
    public static final DeferredItem<Item> TORPEDO_ITEM = ITEMS.registerSimpleItem("torpedo", new Item.Properties().stacksTo(16));

    /** The fit-out: a submarine's upgrade, weapon and paint slots, opened with a crouching click on its hull. */
    public static final DeferredHolder<MenuType<?>, MenuType<FitOutMenu>> FIT_OUT = MENUS.register("fit_out",
            () -> IMenuTypeExtension.create(FitOutMenu::new));

    /** A tube firing: an underwater whoosh, cut from a CC0 recording (devtools/art/sounds/SOURCES.md). */
    public static final DeferredHolder<SoundEvent, SoundEvent> TORPEDO_LAUNCH = SOUNDS.register("torpedo_launch",
            () -> SoundEvent.createVariableRangeEvent(Submersibles.id("torpedo_launch")));

    /** effects: registers everything on {@code modBus} */
    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
        ITEMS.register(modBus);
        COMPONENTS.register(modBus);
        MENUS.register(modBus);
        SOUNDS.register(modBus);
    }

    /** Vanilla Wheels' own tab, where every vehicle already is: the tube and the torpedo go beside them, and in Combat. */
    private static final ResourceKey<CreativeModeTab> VANILLA_WHEELS_TAB = ResourceKey.create(Registries.CREATIVE_MODE_TAB,
            com.chunkworks.vanillawheels.api.VanillaWheels.id("main"));

    static void buildCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == VANILLA_WHEELS_TAB || event.getTabKey() == CreativeModeTabs.COMBAT) {
            event.accept(TORPEDO_TUBE);
            event.accept(TORPEDO_ITEM);
        }
    }
}
