package gg.archipelago.aprandomizer.common.events

import gg.archipelago.aprandomizer.managers.itemmanager.ItemManager
import net.minecraft.server.level.ServerPlayer
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent

@EventBusSubscriber
object OnPlayerChangedDimension {
    @SubscribeEvent
    fun onChangeDimension(event: PlayerChangedDimensionEvent) {
        val player = event.getEntity()
        if (player !is ServerPlayer) return
        ItemManager.refreshCompasses(player)
    }
}
