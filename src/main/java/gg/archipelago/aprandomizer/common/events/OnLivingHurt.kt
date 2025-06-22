package gg.archipelago.aprandomizer.common.events

import dev.koifysh.archipelago.network.client.BouncePacket
import gg.archipelago.aprandomizer.APRandomizer
import net.minecraft.advancements.AdvancementProgress
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.damagesource.DamageTypes
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.animal.Pig
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.util.Map

@EventBusSubscriber
object OnLivingHurt {
    // Directly reference a log4j logger.
    private val LOGGER: Logger = LogManager.getLogger()

    @SubscribeEvent
    fun onLivingDeathEvent(event: LivingDeathEvent) {
        val damageSource = event.getSource().getEntity()
        if (damageSource == null || damageSource.getType() !== EntityType.PLAYER) return

        val apClient = APRandomizer.getAP()
        if (apClient == null || !apClient.isConnected()) return

        val slotData = apClient.getSlotData()
        if (slotData == null || !slotData.MC35) return

        val name = event.getEntity().getEncodeId()
        if (name == null) return  // TODO: more robust mechanism


        val nbt = event.getEntity().saveWithoutId(CompoundTag())
        nbt.remove("UUID")
        nbt.remove("Motion")
        nbt.remove("Health")

        val packet = BouncePacket()
        packet.tags = arrayOf<String>("MC35")
        packet.setData(
            HashMap<String?, Any?>(
                Map.of(
                    "enemy", name,
                    "source", APRandomizer.getAP()!!.getSlot(),
                    "nbt", nbt.toString()
                )
            )
        )
        APRandomizer.sendBounce(packet)
    }

    @SubscribeEvent
    fun onLivingHurtEvent(event: LivingDamageEvent.Post) {
        val entity = event.getEntity()
        val server = entity.getServer()
        if (server == null) return
        if (entity is Pig && event.getSource().`is`(DamageTypes.FALL)) {
            if (entity.getPassengers().isEmpty()) return
            val player = entity.getPassengers().first()
            if (player is ServerPlayer) {
                val advancement = server.getAdvancements()
                    .get(ResourceLocation.fromNamespaceAndPath(APRandomizer.MODID, "archipelago/ride_pig"))
                if (advancement == null) {
                    LOGGER.warn("Missing pigs fly achievement")
                } else {
                    val ap: AdvancementProgress = player.getAdvancements().getOrStartProgress(advancement)
                    if (!ap.isDone()) {
                        for (s in ap.getRemainingCriteria()) {
                            player.getAdvancements().award(advancement, s)
                        }
                    }
                }
            }

            val e = event.getSource().getEntity()
            if (e is ServerPlayer && event.getNewDamage() >= 18 && !event.getSource()
                    .`is`(DamageTypes.EXPLOSION) && !event.getSource().`is`(DamageTypes.FIREBALL)
            ) {
                //Utils.sendMessageToAll("damage type: "+ event.getSource().getMsgId());
                val a = server.getAdvancements()
                    .get(ResourceLocation.fromNamespaceAndPath(APRandomizer.MODID, "archipelago/overkill"))
                if (a == null) {
                    LOGGER.warn("Missing overkill achievement")
                } else {
                    val ap = e.getAdvancements().getOrStartProgress(a)
                    if (!ap.isDone()) {
                        for (s in ap.getRemainingCriteria()) {
                            e.getAdvancements().award(a, s)
                        }
                    }
                }
            }
        }
    }
}
