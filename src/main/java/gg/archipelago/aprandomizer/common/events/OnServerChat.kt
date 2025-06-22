package gg.archipelago.aprandomizer.common.events

import gg.archipelago.aprandomizer.APRandomizer
import net.minecraft.network.chat.Component
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.ServerChatEvent
import java.util.*
import java.util.function.Supplier

@EventBusSubscriber(bus = EventBusSubscriber.Bus.GAME, modid = APRandomizer.MODID)
object OnServerChat {
    @SubscribeEvent
    fun onServerChatEvent(event: ServerChatEvent) {
        val apClient = APRandomizer.getAP()
        if (apClient == null || !apClient.isConnected()) return

        val player = event.getPlayer()

        val message = event.getMessage().getString()

        if (message.startsWith("!")) apClient.sendChat(message)
        else apClient.sendChat(
            "(" + Objects.requireNonNullElseGet<Component?>(
                player.getDisplayName(),
                Supplier { player.getName() }).getString() + ") " + message
        )
    }
}
