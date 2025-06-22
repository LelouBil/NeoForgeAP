package gg.archipelago.aprandomizer.common.events

import gg.archipelago.aprandomizer.APRandomizer
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.level.BlockEvent.BreakEvent

@EventBusSubscriber
object OnBlockBreak {
    @SubscribeEvent
    fun onPlayerBlockInteract(event: BreakEvent) {
        if (!APRandomizer.isJailPlayers()) return
        event.setCanceled(true)
        val player = event.getPlayer()
        if (player is ServerPlayer) player.sendSystemMessage(Component.literal("No!"))
    }
}
