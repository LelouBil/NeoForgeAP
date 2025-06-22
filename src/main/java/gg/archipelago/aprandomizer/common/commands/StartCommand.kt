package gg.archipelago.aprandomizer.common.commands

import com.mojang.brigadier.Command
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.context.CommandContext
import dev.koifysh.archipelago.ClientStatus
import gg.archipelago.aprandomizer.APRandomizer
import gg.archipelago.aprandomizer.common.Utils.Utils
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.stats.Stats
import net.minecraft.world.level.GameRules
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.RegisterCommandsEvent

@EventBusSubscriber
object StartCommand {
    //build our command structure and submit it
    fun Register(dispatcher: CommandDispatcher<CommandSourceStack?>) {
        dispatcher.register(
            Commands.literal("start") //base slash command is "start"
                .executes(Command { context: CommandContext<CommandSourceStack?>? ->
                    StartCommand.Start(
                        context!!,
                        false
                    )
                })
        )

        dispatcher.register(
            Commands.literal("forcestart") //base slash command is "start"
                .executes(Command { context: CommandContext<CommandSourceStack?>? ->
                    StartCommand.Start(
                        context!!,
                        true
                    )
                })
        )
    }

    private fun Start(commandSourceCommandContext: CommandContext<CommandSourceStack?>, force: Boolean): Int {
        if (!APRandomizer.isConnected() && !force) {
            commandSourceCommandContext.getSource()!!
                .sendFailure(Component.literal("Please connect to the Archipelago server before starting."))
            return 1
        }
        if (!APRandomizer.isJailPlayers()) {
            commandSourceCommandContext.getSource()!!
                .sendFailure(Component.literal("The game has already started! what are you doing? START PLAYING!"))
            return 1
        }
        Utils.sendMessageToAll("GO!")
        if (APRandomizer.isConnected()) {
            checkNotNull(APRandomizer.getAP()) // safe because isConnected verifies this
            APRandomizer.getAP()!!.setGameState(ClientStatus.CLIENT_PLAYING)
        }
        APRandomizer.setJailPlayers(false)
        val server = APRandomizer.getServer()
        if (server == null) return 0
        val overworld = server.getLevel(Level.OVERWORLD)
        if (overworld == null) return 0
        val itemManager = APRandomizer.getItemManager()
        if (itemManager == null) return 0
        val spawn = overworld.getSharedSpawnPos()
        val jailStruct =
            overworld.getStructureManager().get(ResourceLocation.fromNamespaceAndPath(APRandomizer.MODID, "spawnjail"))
                .orElseThrow()
        val jailPos = BlockPos(spawn.getX() + 5, 300, spawn.getZ() + 5)
        for (blockPos in BlockPos.betweenClosed(jailPos, jailPos.offset(jailStruct.getSize()))) {
            overworld.setBlock(blockPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS)
        }
        server.getGameRules().getRule<GameRules.BooleanValue?>(GameRules.RULE_DAYLIGHT).set(true, server)
        server.getGameRules().getRule<GameRules.BooleanValue?>(GameRules.RULE_WEATHER_CYCLE).set(true, server)
        server.getGameRules().getRule<GameRules.BooleanValue?>(GameRules.RULE_DOFIRETICK).set(true, server)
        server.getGameRules().getRule<GameRules.IntegerValue?>(GameRules.RULE_RANDOMTICKING).set(3, server)
        server.getGameRules().getRule<GameRules.BooleanValue?>(GameRules.RULE_DO_PATROL_SPAWNING).set(true, server)
        server.getGameRules().getRule<GameRules.BooleanValue?>(GameRules.RULE_DO_TRADER_SPAWNING).set(true, server)
        server.getGameRules().getRule<GameRules.BooleanValue?>(GameRules.RULE_MOBGRIEFING).set(true, server)
        server.getGameRules().getRule<GameRules.BooleanValue?>(GameRules.RULE_DOMOBSPAWNING).set(true, server)
        server.getGameRules().getRule<GameRules.BooleanValue?>(GameRules.RULE_DO_IMMEDIATE_RESPAWN).set(false, server)
        server.getGameRules().getRule<GameRules.BooleanValue?>(GameRules.RULE_DOMOBLOOT).set(true, server)
        server.getGameRules().getRule<GameRules.BooleanValue?>(GameRules.RULE_DOENTITYDROPS).set(true, server)
        server.execute(Runnable {
            for (player in server.getPlayerList().getPlayers()) {
                player.getFoodData().eat(20, 20f)
                player.setHealth(20f)
                player.getInventory().clearContent()
                player.resetStat(Stats.CUSTOM.get(Stats.TIME_SINCE_REST))
                player.teleportTo(spawn.getX().toDouble(), spawn.getY().toDouble(), spawn.getZ().toDouble())

                if (APRandomizer.isConnected()) {
                    checkNotNull(APRandomizer.getAP()) // safe because isConnected verifies this
                    val slotData = APRandomizer.getAP()!!.getSlotData()
                    if (slotData != null) {
                        for (iStack in slotData.startingItemStacks) {
                            Utils.giveItemToPlayer(player, iStack.copy())
                        }
                    }
                }
            }
            itemManager.catchUp(server)
            APRandomizer.getGiftHandler().openGiftBox()
            APRandomizer.giftHandler.startReception()
        })
        return 1
    }

    //wait for register commands event then register us as a command.
    @SubscribeEvent
    fun onRegisterCommandsEvent(event: RegisterCommandsEvent) {
        // I'm assuming this fires after the server loads lol
        Register(event.getDispatcher())
    }
}
