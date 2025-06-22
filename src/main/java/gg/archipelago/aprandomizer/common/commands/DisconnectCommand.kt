package gg.archipelago.aprandomizer.common.commands

import com.mojang.brigadier.Command
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.context.CommandContext
import gg.archipelago.aprandomizer.ap.APClient
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.RegisterCommandsEvent

@EventBusSubscriber
object DisconnectCommand {
    //build our command structure and submit it
    fun Register(dispatcher: CommandDispatcher<CommandSourceStack?>) {
        dispatcher.register(
            Commands.literal("disconnect") //base slash command is "connect"
                //take the first argument as a string and name it "Address"
                .executes(DisconnectCommand::disconnect)
        )
    }

    private fun disconnect(commandContext: CommandContext<CommandSourceStack?>?): Int {
        APClient.client.disconnect()
        //Utils.sendMessageToAll("Disconnected.");
        return 1
    }

    //wait for register commands event then register us as a command.
    @SubscribeEvent
    fun onRegisterCommandsEvent(event: RegisterCommandsEvent) {
        Register(event.getDispatcher())
    }
}
