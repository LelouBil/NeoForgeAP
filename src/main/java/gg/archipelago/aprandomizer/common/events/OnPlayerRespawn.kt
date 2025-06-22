package gg.archipelago.aprandomizer.common.events

import gg.archipelago.aprandomizer.APRandomizer
import gg.archipelago.aprandomizer.managers.itemmanager.ItemManager
import net.minecraft.server.level.ServerPlayer
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerRespawnEvent

@EventBusSubscriber
object OnPlayerRespawn {
    @SubscribeEvent
    fun onPlayerRespawn(event: PlayerRespawnEvent) {
        val player = event.getEntity()
        if (player !is ServerPlayer) return
        ItemManager.refreshCompasses(player)

        if (APRandomizer.isJailPlayers()) {
            val jail = APRandomizer.getJailPosition()
            player.teleportTo(jail.getX().toDouble(), jail.getY().toDouble(), jail.getZ().toDouble())
        }

        //if we are leaving because the dragon is dead check if our goals are all done!
        val goalManager = APRandomizer.getGoalManager()
        if (goalManager != null && APRandomizer.getWorldData() != null && APRandomizer.getWorldData()!!
                .isDragonKilled()
        ) goalManager.checkGoalCompletion()
    }
}
