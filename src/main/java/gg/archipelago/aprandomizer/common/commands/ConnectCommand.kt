package gg.archipelago.aprandomizer.common.commands

import com.mojang.brigadier.Command
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import gg.archipelago.aprandomizer.APRandomizer
import gg.archipelago.aprandomizer.ap.storage.APMCData
import gg.archipelago.aprandomizer.common.Utils.Utils
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.RegisterCommandsEvent
import java.net.URISyntaxException

@EventBusSubscriber
object ConnectCommand {
    //build our command structure and submit it
    fun Register(dispatcher: CommandDispatcher<CommandSourceStack?>) {
        dispatcher.register(
            Commands.literal("connect") //base slash command is "connect"
                .executes(Command { context: CommandContext<CommandSourceStack?>? ->
                    connectToAPServer(
                        context,
                        null,
                        -2,
                        null
                    )
                }) //take the first argument as a string and name it "Address"
                .then(
                    Commands.argument<String?>("Address", StringArgumentType.string())
                        .executes(Command { context: CommandContext<CommandSourceStack?>? ->
                            connectToAPServer(
                                context,
                                StringArgumentType.getString(context, "Address"),
                                -1,
                                null
                            )
                        })
                        .then(
                            Commands.argument<Int?>("Port", IntegerArgumentType.integer())
                                .executes(Command { context: CommandContext<CommandSourceStack?>? ->
                                    connectToAPServer(
                                        context,
                                        StringArgumentType.getString(context, "Address"),
                                        IntegerArgumentType.getInteger(context, "Port"),
                                        null
                                    )
                                })
                                .then(
                                    Commands.argument<String?>("Password", StringArgumentType.string())
                                        .executes(Command { context: CommandContext<CommandSourceStack?>? ->
                                            connectToAPServer(
                                                context,
                                                StringArgumentType.getString(context, "Address"),
                                                IntegerArgumentType.getInteger(context, "Port"),
                                                StringArgumentType.getString(context, "Password")
                                            )
                                        })
                                )
                        )
                )
        )
    }

    private fun connectToAPServer(
        commandContext: CommandContext<CommandSourceStack?>?,
        hostname: String?,
        port: Int,
        password: String?
    ): Int {
        var hostname = hostname
        var port = port
        val data = APRandomizer.getApmcData()
        if (hostname == null) {
            hostname = data.server
            port = data.port
        }
        if (data.state == APMCData.State.VALID) {
            val APClient = APRandomizer.getAP()
            if (APClient == null) return 0

            APClient.setName(data.player_name)
            APClient.setPassword(password)
            val address = if (port == -1) hostname else (hostname + ":" + port)
            Utils.sendMessageToAll("Connecting to Archipelago server at " + address)
            try {
                APClient.connect(address)
            } catch (e: URISyntaxException) {
                Utils.sendMessageToAll("Malformed address " + address)
            }
        } else if (data.state == APMCData.State.MISSING) Utils.sendMessageToAll("no .apmc file found. please stop the server,  place .apmc file in './APData/', delete the world folder, then relaunch the server.")
        else if (data.state == APMCData.State.INVALID_VERSION) Utils.sendMessageToAll("APMC data file wrong version.")
        else if (data.state == APMCData.State.INVALID_SEED) Utils.sendMessageToAll("Current Minecraft world has been used for a previous game. please stop server, delete the world and relaunch the server.")

        return 1
    }

    //wait for register commands event then register us as a command.
    @SubscribeEvent
    fun onRegisterCommandsEvent(event: RegisterCommandsEvent) {
        Register(event.getDispatcher())
    }
}
