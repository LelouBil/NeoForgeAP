package gg.archipelago.aprandomizer.common.events

import gg.archipelago.aprandomizer.managers.itemmanager.ItemManager
import net.minecraft.server.level.ServerPlayer
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerRespawnEvent

@EventBusSubscriber
object OnDimensionChange {
    @SubscribeEvent
    fun onChange1(event: PlayerChangedDimensionEvent) {
        val player = event.getEntity()
        if (player !is ServerPlayer) return
        ItemManager.refreshCompasses(player)
    }

    @SubscribeEvent
    fun onChange1(event: PlayerRespawnEvent) {
        val player = event.getEntity()
        if (player !is ServerPlayer) return
        ItemManager.refreshCompasses(player)
    }
}
