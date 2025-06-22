package gg.archipelago.aprandomizer.common.commands

import com.mojang.brigadier.Command
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.mojang.brigadier.suggestion.SuggestionProvider
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import dev.koifysh.archipelago.parts.NetworkPlayer
import gg.archipelago.aprandomizer.APRandomizer
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.RegisterCommandsEvent
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer

@EventBusSubscriber
object GiftCommand {
    //build our command structure and submit it
    fun Register(dispatcher: CommandDispatcher<CommandSourceStack?>) {
        dispatcher.register(
            Commands.literal("gift")
                .then(
                    Commands
                        .argument<String?>("recipient", StringArgumentType.string())
                        .suggests(APPlayersSuggestionProvider())
                        .executes(GiftCommand::gift)
                )
        )
    }

    @Throws(CommandSyntaxException::class)
    private fun gift(commandSourceCommandContext: CommandContext<CommandSourceStack?>): Int {
        if (!APRandomizer.isConnected()) {
            commandSourceCommandContext.getSource()!!
                .sendFailure(Component.literal("Please connect to the Archipelago server before gifting."))
            return 1
        }
        if (APRandomizer.isJailPlayers()) {
            commandSourceCommandContext.getSource()!!
                .sendFailure(Component.literal("You cannot gift items before the game starts."))
            return 1
        }

        val server = APRandomizer.getServer()
        if (server == null) return 0
        val overworld = server.getLevel(Level.OVERWORLD)
        if (overworld == null) return 0
        val apClient = APRandomizer.getAP()
        if (apClient == null) {
            commandSourceCommandContext.getSource()!!
                .sendFailure(Component.literal("Archipelago client is not initialized."))
            return 0
        }
        val player = commandSourceCommandContext.getSource()!!.getPlayerOrException()
        val heldItem = player.getMainHandItem()
        if (heldItem.isEmpty()) {
            commandSourceCommandContext.getSource()!!
                .sendFailure(Component.literal("You must hold an item in your hand to gift it."))
            return 0
        }

        val recipientName = StringArgumentType.getString(commandSourceCommandContext, "recipient")
        val recipient =
            apClient.getRoomInfo().networkPlayers.stream().filter { p: NetworkPlayer? -> p!!.name == recipientName }
                .findFirst().orElse(null)
        if (recipient == null) {
            commandSourceCommandContext.getSource()!!
                .sendFailure(Component.literal("No player found with the name: " + recipientName))
            return 0
        }
        // Check if the item can be sent
        val giftHandler = APRandomizer.getGiftHandler()

        giftHandler.giftItem(heldItem, recipient).whenComplete { giftingError,v ->
            commandSourceCommandContext.getSource()!!
                .sendFailure(Component.literal("Error while gifting item: " + giftingError))
            player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY) // Clear the item from the player's hand
        }

        return 0;
    }

    //wait for register commands event then register us as a command.
    @SubscribeEvent
    fun onRegisterCommandsEvent(event: RegisterCommandsEvent) {
        // I'm assuming this fires after the server loads lol
        Register(event.getDispatcher())
    }

    class APPlayersSuggestionProvider : SuggestionProvider<CommandSourceStack?> {
        @Throws(CommandSyntaxException::class)
        override fun getSuggestions(
            context: CommandContext<CommandSourceStack?>,
            builder: SuggestionsBuilder
        ): CompletableFuture<Suggestions?>? {
            val recipientName =
                runCatching { context.getArgument("recipient", String::class.java) }.getOrDefault("")
            if (APRandomizer.getAP() == null) {
                return Suggestions.empty()
            }
            APRandomizer.getAP()!!
                .getRoomInfo().networkPlayers.filterNot { it.slot == 0 }.forEach(Consumer { player: NetworkPlayer ->
                    if (recipientName.isBlank() || player.name.startsWith(recipientName)) {
                        builder.suggest(player.name)
                    }
                })
            return builder.buildFuture()
        }
    }
}
