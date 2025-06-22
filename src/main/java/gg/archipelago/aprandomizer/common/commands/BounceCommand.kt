package gg.archipelago.aprandomizer.common.commands

import com.mojang.brigadier.Command
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.context.CommandContext
import dev.koifysh.archipelago.network.client.BouncePacket
import gg.archipelago.aprandomizer.APRandomizer
import net.minecraft.commands.CommandBuildContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.CompoundTagArgument
import net.minecraft.commands.arguments.ResourceArgument
import net.minecraft.commands.synchronization.SuggestionProviders
import net.minecraft.core.Holder
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.EntityType
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.RegisterCommandsEvent
import java.util.Map
import java.util.function.Predicate

@EventBusSubscriber
object BounceCommand {
    //build our command structure and submit it
    fun Register(dispatcher: CommandDispatcher<CommandSourceStack?>, pContext: CommandBuildContext) {
        dispatcher.register(
            Commands.literal("bounce") //base slash command is "connect"
                // first make sure its NOT a dedicated server (aka single player or hosted via in game client, OR user has an op level of 1)
                .requires(Predicate { CommandSource: CommandSourceStack? ->
                    (!CommandSource!!.getServer().isDedicatedServer() || CommandSource.hasPermission(1))
                }) //take the first argument as a string and name it "Address"
                .then(
                    Commands.argument<Holder.Reference<EntityType<*>?>?>(
                        "entity",
                        ResourceArgument.resource<EntityType<*>?>(pContext, Registries.ENTITY_TYPE)
                    )
                        .suggests(SuggestionProviders.SUMMONABLE_ENTITIES)
                        .executes(Command { context: CommandContext<CommandSourceStack?>? ->
                            bounceEntity(
                                context!!.getSource(),
                                ResourceArgument.getSummonableEntityType(context, "entity"),
                                CompoundTag()
                            )
                        }
                        )
                        .then(
                            Commands.argument<CompoundTag?>("nbt", CompoundTagArgument.compoundTag())
                                .executes(Command { context: CommandContext<CommandSourceStack?>? ->
                                    bounceEntity(
                                        context!!.getSource(),
                                        ResourceArgument.getSummonableEntityType(context, "entity"),
                                        CompoundTagArgument.getCompoundTag<CommandSourceStack?>(context, "nbt")
                                    )
                                }
                                )
                        )
                )


        )
    }

    private fun bounceEntity(
        commandSource: CommandSourceStack?,
        entity: Holder.Reference<EntityType<*>?>,
        nbt: CompoundTag
    ): Int {
        val apClient = APRandomizer.getAP()
        if (apClient == null) return 0

        val packet = BouncePacket()
        packet.tags = arrayOf<String>("MC35")
        packet.setData(
            HashMap<String?, Any?>(
                Map.of(
                    "enemy", entity.toString(),
                    "source", apClient.getSlot(),
                    "nbt", nbt.toString()
                )
            )
        )
        apClient.sendBounce(packet)
        return 1
    }

    //wait for register commands event then register us as a command.
    @SubscribeEvent
    fun onRegisterCommandsEvent(event: RegisterCommandsEvent) {
        Register(event.getDispatcher(), event.getBuildContext())
    }
}
