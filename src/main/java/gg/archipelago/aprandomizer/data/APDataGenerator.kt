package gg.archipelago.aprandomizer.data

import gg.archipelago.aprandomizer.APRandomizer
import gg.archipelago.aprandomizer.APRegistries
import gg.archipelago.aprandomizer.APStructures
import gg.archipelago.aprandomizer.data.advancements.APAdvancementProvider
import gg.archipelago.aprandomizer.data.advancements.AfterAdvancementProvider
import gg.archipelago.aprandomizer.data.advancements.ReceivedAdvancementProvider
import gg.archipelago.aprandomizer.data.advancements.VanillaOverrideAdvancementProvider
import gg.archipelago.aprandomizer.data.datamaps.APDataMapProvider
import gg.archipelago.aprandomizer.data.loot.APAddedLootTableProvider
import gg.archipelago.aprandomizer.data.loot.APGlobalLootModifierProvider
import gg.archipelago.aprandomizer.data.recipes.APRecipeProvider
import gg.archipelago.aprandomizer.data.tags.APBiomeTagsProvider
import gg.archipelago.aprandomizer.data.tags.APDamageTypeTagsProvider
import gg.archipelago.aprandomizer.data.tags.APStructureTagsProvider
import gg.archipelago.aprandomizer.dimensions.APDimensionTypes
import gg.archipelago.aprandomizer.items.APItem
import gg.archipelago.aprandomizer.items.APItems
import gg.archipelago.aprandomizer.locations.APLocation
import gg.archipelago.aprandomizer.locations.APLocations
import gg.archipelago.aprandomizer.modifiers.APStructureModifiers
import gg.archipelago.aprandomizer.structures.APStructureSets
import gg.archipelago.aprandomizer.structures.APTemplatePools
import net.minecraft.core.HolderLookup
import net.minecraft.core.RegistrySetBuilder
import net.minecraft.core.registries.Registries
import net.minecraft.data.advancements.AdvancementProvider
import net.minecraft.data.advancements.AdvancementSubProvider
import net.minecraft.data.advancements.packs.*
import net.minecraft.data.loot.LootTableProvider
import net.minecraft.data.loot.LootTableProvider.SubProviderEntry
import net.minecraft.data.worldgen.BootstrapContext
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.dimension.DimensionType
import net.minecraft.world.level.levelgen.structure.Structure
import net.minecraft.world.level.levelgen.structure.StructureSet
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool
import net.minecraft.world.level.storage.loot.LootTable
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.fml.common.EventBusSubscriber.Bus
import net.neoforged.neoforge.common.data.DatapackBuiltinEntriesProvider
import net.neoforged.neoforge.common.world.StructureModifier
import net.neoforged.neoforge.data.event.GatherDataEvent
import net.neoforged.neoforge.registries.NeoForgeRegistries
import java.util.List
import java.util.Set
import java.util.function.Function

@EventBusSubscriber(bus = Bus.MOD, modid = APRandomizer.MODID)
object APDataGenerator {
    @SubscribeEvent
    fun onDataGen(event: GatherDataEvent.Client) {
        val registries = event.addProvider<DatapackBuiltinEntriesProvider?>(
            DatapackBuiltinEntriesProvider(
                event.getGenerator().getPackOutput(), event.getLookupProvider(),
                RegistrySetBuilder()
                    .add<StructureModifier?>(
                        NeoForgeRegistries.Keys.STRUCTURE_MODIFIERS,
                        RegistrySetBuilder.RegistryBootstrap { context: BootstrapContext<StructureModifier?>? ->
                            APStructureModifiers.bootstrap(context)
                        })
                    .add<Structure?>(
                        Registries.STRUCTURE,
                        RegistrySetBuilder.RegistryBootstrap { context: BootstrapContext<Structure?>? ->
                            APStructures.bootstrap(context)
                        })
                    .add<StructureTemplatePool?>(
                        Registries.TEMPLATE_POOL,
                        RegistrySetBuilder.RegistryBootstrap { context: BootstrapContext<StructureTemplatePool?>? ->
                            APTemplatePools.bootstrap(context)
                        })
                    .add<StructureSet?>(
                        Registries.STRUCTURE_SET,
                        RegistrySetBuilder.RegistryBootstrap { context: BootstrapContext<StructureSet?>? ->
                            APStructureSets.bootstrap(context)
                        })
                    .add<APLocation?>(
                        APRegistries.ARCHIPELAGO_LOCATION,
                        RegistrySetBuilder.RegistryBootstrap { context: BootstrapContext<APLocation?>? ->
                            APLocations.bootstrap(context)
                        })
                    .add<APItem?>(
                        APRegistries.ARCHIPELAGO_ITEM,
                        RegistrySetBuilder.RegistryBootstrap { context: BootstrapContext<APItem?>? ->
                            APItems.bootstrap(context)
                        })
                    .add<DimensionType?>(
                        Registries.DIMENSION_TYPE,
                        RegistrySetBuilder.RegistryBootstrap { context: BootstrapContext<DimensionType?>? ->
                            APDimensionTypes.bootstrap(context)
                        }),
                Set.of<String?>(APRandomizer.MODID, "minecraft")
            )
        ).getRegistryProvider()
        event.addProvider<AdvancementProvider?>(
            AdvancementProvider(
                event.getGenerator().getPackOutput(), registries, List.of<AdvancementSubProvider?>(
                    APAdvancementProvider(),
                    ReceivedAdvancementProvider(),
                    AfterAdvancementProvider(
                        List.of<AdvancementSubProvider?>(
                            VanillaStoryAdvancements(),
                            VanillaNetherAdvancements(),
                            VanillaTheEndAdvancements(),
                            VanillaHusbandryAdvancements(),
                            VanillaAdventureAdvancements()
                        ),
                        Function { id: ResourceLocation? ->
                            ResourceLocation.fromNamespaceAndPath(
                                APRandomizer.MODID,
                                "vanilla/" + id!!.getPath() + "_after"
                            )
                        }),
                    VanillaOverrideAdvancementProvider()
                )
            )
        )
        event.addProvider<APDamageTypeTagsProvider?>(
            APDamageTypeTagsProvider(
                event.getGenerator().getPackOutput(),
                registries
            )
        )
        event.addProvider<APRecipeProvider.Runner?>(
            APRecipeProvider.Runner(
                event.getGenerator().getPackOutput(),
                registries
            )
        )
        event.addProvider<APDataMapProvider?>(APDataMapProvider(event.getGenerator().getPackOutput(), registries))
        event.addProvider<APBiomeTagsProvider?>(APBiomeTagsProvider(event.getGenerator().getPackOutput(), registries))
        event.addProvider<APStructureTagsProvider?>(
            APStructureTagsProvider(
                event.getGenerator().getPackOutput(),
                registries
            )
        )
        event.addProvider<APGlobalLootModifierProvider?>(
            APGlobalLootModifierProvider(
                event.getGenerator().getPackOutput(), registries
            )
        )
        event.addProvider<LootTableProvider?>(
            LootTableProvider(
                event.getGenerator().getPackOutput(), mutableSetOf<ResourceKey<LootTable?>?>(),
                List.of<SubProviderEntry?>(
                    SubProviderEntry(Function { registries: HolderLookup.Provider? ->
                        APAddedLootTableProvider(
                            registries
                        )
                    }, LootContextParamSets.ALL_PARAMS)
                ),
                registries
            )
        )
    }
}
