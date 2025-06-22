package gg.archipelago.aprandomizer.common.events

import gg.archipelago.aprandomizer.APRandomizer
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.GameRules
import net.neoforged.bus.api.EventPriority
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent
import java.util.*
import java.util.function.Supplier

@EventBusSubscriber
object OnDeath {
    @JvmField
    var sendDeathLink: Boolean = true

    @SubscribeEvent(priority = EventPriority.LOWEST)
    fun onDeathEvent(event: LivingDeathEvent) {
        if (!APRandomizer.isConnected()) return
        checkNotNull(APRandomizer.getAP()) // safe because isConnected verifies this

        //only trigger on player death
        val player = event.getEntity()
        if (player !is ServerPlayer) return
        val slotData = APRandomizer.getAP()!!.getSlotData()
        if (slotData == null || !slotData.deathlink) return
        //don't send deathlink if the cause of this death was a deathlink
        if (!sendDeathLink) return

        // TODO: these args are backwards on kono master
        APRandomizer.getAP()!!.sendDeathlink(
            event.getSource().getLocalizedDeathMessage(player).getString(),
            Objects.requireNonNullElseGet<Component?>(player.getDisplayName(), Supplier { player.getName() })
                .getString()
        )

        val server = APRandomizer.getServer()
        if (server == null) return

        val deathMessages = server.getGameRules().getRule<GameRules.BooleanValue>(GameRules.RULE_SHOWDEATHMESSAGES)
        val death = deathMessages.get()
        deathMessages.set(false, server)
        sendDeathLink = false
        for (serverPlayer in APRandomizer.getServer()!!.getPlayerList().getPlayers()) {
            if (serverPlayer !== player) {
                serverPlayer.kill(serverPlayer.serverLevel())
            }
        }
        deathMessages.set(death, server)
        sendDeathLink = true
    }
}
