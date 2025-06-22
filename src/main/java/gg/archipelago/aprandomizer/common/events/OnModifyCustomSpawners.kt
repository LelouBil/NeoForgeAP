package gg.archipelago.aprandomizer.common.events

import net.minecraft.world.entity.npc.CatSpawner
import net.minecraft.world.level.CustomSpawner
import net.minecraft.world.level.Level
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.level.ModifyCustomSpawnersEvent

@EventBusSubscriber
object OnModifyCustomSpawners {
    @SubscribeEvent
    fun onModifyCustomSpawners(event: ModifyCustomSpawnersEvent) {
        if (event.getLevel().dimension() !== Level.OVERWORLD && event.getCustomSpawners().stream()
                .noneMatch { spawner: CustomSpawner? -> spawner is CatSpawner }
        ) {
            event.addCustomSpawner(CatSpawner())
        }
    }
}
