package gg.archipelago.aprandomizer.common.events

import gg.archipelago.aprandomizer.APRandomizer
import gg.archipelago.aprandomizer.APRegistries
import gg.archipelago.aprandomizer.ap.storage.APMCData
import gg.archipelago.aprandomizer.locations.APLocation
import gg.archipelago.aprandomizer.locations.AdvancementLocation
import net.minecraft.advancements.DisplayInfo
import net.minecraft.server.level.ServerPlayer
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.entity.player.AdvancementEvent.AdvancementEarnEvent
import net.neoforged.neoforge.event.entity.player.AdvancementEvent.AdvancementProgressEvent
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.util.function.Consumer

@EventBusSubscriber
object OnAdvancement {
    // Directly reference a log4j logger.
    private val LOGGER: Logger = LogManager.getLogger()

    @SubscribeEvent
    fun onAdvancementEvent(event: AdvancementProgressEvent) {
        val server = APRandomizer.getServer()
        if (server == null) return

        server.execute(Runnable {
            for (progress in event.getAdvancementProgress().getCompletedCriteria()) {
                for (p in server.getPlayerList().getPlayers()) {
                    p.getAdvancements().award(event.getAdvancement(), progress)
                }
            }
        })
    }

    @SubscribeEvent
    fun onAdvancementEvent(event: AdvancementEarnEvent) {
        val server = APRandomizer.getServer()
        if (server == null) return  // !?


        //dont do any checking if the apmcdata file is not valid.
        if (APRandomizer.getApmcData().state != APMCData.State.VALID) return

        val player = event.getEntity() as ServerPlayer
        val advancement = event.getAdvancement().value()
        val id = event.getAdvancement().id()

        val am = APRandomizer.getAdvancementManager()
        if (am == null) return
        val locations = server.registryAccess().lookupOrThrow<APLocation?>(APRegistries.ARCHIPELAGO_LOCATION)
        //don't do anything if this advancement has already been had, or is not on our list of tracked advancements.
        for (entry in locations.entrySet()) {
            val value = entry.value
            if (value !is AdvancementLocation) continue
            if (!value.advancement.equals(id)) continue
            if(am.hasAdvancement(entry.key)) continue
            if (am.getAdvancementID(entry.key) == 0L) continue
            LOGGER.debug("{} has gotten the advancement {}", player.getDisplayName()!!.getString(), id)
            am.addAdvancement(entry.key)
            am.syncAdvancement(entry.key, entry.value)
            advancement.display().ifPresent(Consumer { it: DisplayInfo? ->
                server.getPlayerList().broadcastSystemMessage(
                    advancement.display().get().getType().createAnnouncement(event.getAdvancement(), player),
                    false
                )
            })
        }
    }
}
