package gg.archipelago.aprandomizer.common.commands

import com.mojang.brigadier.Command
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.brigadier.context.CommandContext
import gg.archipelago.aprandomizer.APRandomizer
import gg.archipelago.aprandomizer.SlotData
import gg.archipelago.aprandomizer.common.Utils.TitleQueue
import gg.archipelago.aprandomizer.common.Utils.Utils
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.RegisterCommandsEvent
import java.util.function.Supplier

@EventBusSubscriber
object APCommand {
    //build our command structure and submit it
    fun Register(dispatcher: CommandDispatcher<CommandSourceStack?>) {
        dispatcher.register(
            Commands.literal("ap") //base slash command is "ap"
                //First sub-command to set/retreive deathlink status
                .then(
                    Commands.literal("deathlink")
                        .executes(APCommand::queryDeathLink)
                        .then(
                            Commands.argument<Boolean?>("value", BoolArgumentType.bool())
                                .executes(APCommand::setDeathLink)
                        )
                ) //Second sub-command to set/retreive MC35 status
                .then(
                    Commands.literal("mc35")
                        .executes(APCommand::queryMC35)
                        .then(
                            Commands.argument<Boolean?>("value", BoolArgumentType.bool())
                                .executes(APCommand::setMC35)
                        )
                ) //third sub-command to stop titlequeue
                .then(
                    Commands.literal("clearTitleQueue")
                        .executes(APCommand::clearTitleQueue)
                )

        )
    }

    private fun clearTitleQueue(commandSourceStackCommandContext: CommandContext<CommandSourceStack?>?): Int {
        Utils.sendMessageToAll("Title Queue Cleared")
        TitleQueue.clearTitleQueue()
        return 1
    }

    private fun queryDeathLink(source: CommandContext<CommandSourceStack?>): Int {
        var slotData: SlotData? = null
        if (APRandomizer.getAP() == null || (APRandomizer.getAP()!!.getSlotData().also { slotData = it }) == null) {
            source.getSource()!!.sendFailure(Component.literal("Must be connected to an AP server to use this command"))
            return 0
        }
        val enabled = if (slotData!!.deathlink) "enabled" else "disabled"
        source.getSource()!!.sendSuccess(Supplier { Component.literal("DeathLink is " + enabled) }, false)
        return 1
    }

    private fun setDeathLink(source: CommandContext<CommandSourceStack?>): Int {
        var slotData: SlotData? = null
        if (APRandomizer.getAP() == null || (APRandomizer.getAP()!!.getSlotData().also { slotData = it }) == null) {
            source.getSource()!!.sendFailure(Component.literal("Must be connected to an AP server to use this command"))
            return 0
        }

        slotData!!.deathlink = BoolArgumentType.getBool(source, "value")
        val deathlink = slotData.deathlink
        if (deathlink) {
            APRandomizer.getAP()!!.addTag("DeathLink")
        } else {
            APRandomizer.getAP()!!.removeTag("DeathLink")
        }

        val enabled = if (slotData.deathlink) "enabled" else "disabled"
        source.getSource()!!.sendSuccess(Supplier { Component.literal("DeathLink is now " + enabled) }, false)
        return 1
    }

    private fun queryMC35(source: CommandContext<CommandSourceStack?>): Int {
        var slotData: SlotData? = null
        if (APRandomizer.getAP() == null || (APRandomizer.getAP()!!.getSlotData().also { slotData = it }) == null) {
            source.getSource()!!.sendFailure(Component.literal("Must be connected to an AP server to use this command"))
            return 0
        }

        val enabled = if (slotData!!.MC35) "enabled" else "disabled"
        source.getSource()!!.sendSuccess(Supplier { Component.literal("MC35 is " + enabled) }, false)
        return 1
    }

    private fun setMC35(source: CommandContext<CommandSourceStack?>): Int {
        var slotData: SlotData? = null
        if (APRandomizer.getAP() == null || (APRandomizer.getAP()!!.getSlotData().also { slotData = it }) == null) {
            source.getSource()!!.sendFailure(Component.literal("Must be connected to an AP server to use this command"))
            return 0
        }

        slotData!!.MC35 = BoolArgumentType.getBool(source, "value")
        val mc35 = slotData.MC35
        if (mc35) {
            APRandomizer.getAP()!!.addTag("MC35")
        } else {
            APRandomizer.getAP()!!.removeTag("MC35")
        }

        val enabled = if (slotData.MC35) "enabled" else "disabled"
        source.getSource()!!.sendSuccess(Supplier { Component.literal("MC35 is " + enabled) }, false)
        return 1
    }

    //wait for register commands event then register us as a command.
    @SubscribeEvent
    fun onRegisterCommandsEvent(event: RegisterCommandsEvent) {
        Register(event.getDispatcher())
    }
}
