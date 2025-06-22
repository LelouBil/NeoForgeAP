package gg.archipelago.aprandomizer.common.events

import gg.archipelago.aprandomizer.APRandomizer
import net.minecraft.network.chat.Component
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.CommandEvent
import java.util.List

@EventBusSubscriber
object OnCommand {
    private val ALLOWED_COMMANDS: MutableList<String> = List.of<String?>(
        "connect",
        "sync",
        "start",
        "stop",
        "kick",
        "ban",
        "ban-ip",
        "pardon",
        "pardon-ip",
        "whitelist",
        "me",
        "say"
    )

    @SubscribeEvent
    fun onPlayerLoginEvent(event: CommandEvent) {
        if (!APRandomizer.isRace()) {
            return
        }
        val source = event.getParseResults().getContext().getSource()
        val command = event.getParseResults().getReader().getRead()
        for (allowedCommand in ALLOWED_COMMANDS) if (command.startsWith(allowedCommand) || command.startsWith("/" + allowedCommand)) return

        event.setCanceled(true)
        source.sendFailure(Component.literal("Non-essential commands are disabled in race mode."))
    } //    @EventBusSubscriber
    //    public static class onDimensionChange {
    //
    //        @SubscribeEvent
    //        public static void onChange1(PlayerEvent.PlayerChangedDimensionEvent event) {
    //            if(!(event.getEntity() instanceof ServerPlayer player)) return;
    //            ItemManager.refreshCompasses(player);
    //        }
    //
    //        @SubscribeEvent
    //        public static void onChange1(PlayerEvent.PlayerRespawnEvent event) {
    //            if(!(event.getEntity() instanceof ServerPlayer player)) return;
    //            ItemManager.refreshCompasses(player);
    //        }
    //    }
}
