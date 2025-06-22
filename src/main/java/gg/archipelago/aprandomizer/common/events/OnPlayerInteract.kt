package gg.archipelago.aprandomizer.common.events

import gg.archipelago.aprandomizer.APRandomizer
import gg.archipelago.aprandomizer.attachments.APAttachmentTypes
import gg.archipelago.aprandomizer.attachments.APPlayerAttachment
import gg.archipelago.aprandomizer.items.CompassReward
import gg.archipelago.aprandomizer.managers.itemmanager.ItemManager
import net.minecraft.core.HolderLookup
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.NbtOps
import net.minecraft.nbt.Tag
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.level.block.Blocks
import net.neoforged.bus.api.ICancellableEvent
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.*

@EventBusSubscriber
object OnPlayerInteract {
    fun onPlayerInteract(event: PlayerInteractEvent) {
        if (event.getSide().isClient()) return
        //stop all right click interactions if game has not started.
        if (APRandomizer.isJailPlayers() && event is ICancellableEvent) event.setCanceled(true)
    }

    @SubscribeEvent
    fun onLeftClickBlock(event: LeftClickBlock) {
        onPlayerInteract(event)
    }

    @SubscribeEvent
    fun onPlayerBlockInteract(event: RightClickBlock) {
        onPlayerInteract(event)

        if (event.getSide().isClient()) return

        if (!event.getItemStack().has(DataComponents.CUSTOM_DATA) || !event.getItemStack()
                .getOrDefault<CustomData?>(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().contains("structure")
        ) return

        val block = event.getLevel().getBlockState(event.getHitVec().getBlockPos())
        if (block.`is`(Blocks.LODESTONE)) event.setCanceled(true)

        event.getEntity().getInventory().setChanged()
        event.getEntity().inventoryMenu.broadcastChanges()
    }

    @SubscribeEvent
    fun onPlayerInteractEvent(event: RightClickItem) {
        onPlayerInteract(event)

        if (event.getSide().isClient()) return
        val player = event.getEntity()
        if (player !is ServerPlayer) return

        if (event.getItemStack().getItem() != Items.COMPASS) return

        val compass = event.getItemStack()
        val customData = compass.get<CustomData?>(DataComponents.CUSTOM_DATA)
        if (customData == null) return

        val nbt = customData.copyTag()

        //fetch our current compass list.
        val compasses =
            event.getEntity().getData<APPlayerAttachment?>(APAttachmentTypes.AP_PLAYER).getUnlockedCompassRewards()

        val registries: HolderLookup.Provider = event.getLevel().registryAccess()
        val currentCompassReward = nbt.read<CompassReward?>(
            "structure",
            CompassReward.CODEC,
            registries.createSerializationContext<Tag?>(NbtOps.INSTANCE)
        )
        val currentCompassIndex = nbt.getInt("index")

        if (currentCompassReward.isEmpty() || currentCompassIndex.isEmpty()) return

        var newCompassIndex = currentCompassIndex.get() + 1
        if (compasses.size <= newCompassIndex) {
            newCompassIndex = 0
        }
        nbt.putInt("index", newCompassIndex)
        compass.set<CustomData?>(DataComponents.CUSTOM_DATA, CustomData.of(nbt))
        val newCompassReward = compasses.get(newCompassIndex)

        ItemManager.updateCompassLocation(newCompassReward, player, compass)
    }
}
