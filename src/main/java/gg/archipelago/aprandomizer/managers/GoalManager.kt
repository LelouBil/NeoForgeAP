package gg.archipelago.aprandomizer.managers

import dev.koifysh.archipelago.ClientStatus
import gg.archipelago.aprandomizer.APRandomizer
import gg.archipelago.aprandomizer.ap.storage.APMCData
import gg.archipelago.aprandomizer.ap.storage.APMCData.Bosses
import gg.archipelago.aprandomizer.common.Utils.Utils
import gg.archipelago.aprandomizer.data.WorldData
import gg.archipelago.aprandomizer.managers.advancementmanager.AdvancementManager
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.bossevents.CustomBossEvent
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.BossEvent
import net.minecraft.world.entity.boss.enderdragon.EnderDragon
import net.minecraft.world.entity.boss.wither.WitherBoss
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent
import java.awt.Color

@EventBusSubscriber
class GoalManager(
    private val server: MinecraftServer,
    private val apmc: APMCData,
    private val advancementManager: AdvancementManager,
    private val worldData: WorldData
) {
    var advancementsRequired: Int
    var dragonEggShardsRequired: Int
    var totalDragonEggShards: Int

    private var advancementInfoBar: CustomBossEvent? = null
    private var eggInfoBar: CustomBossEvent? = null
    private var connectionInfoBar: CustomBossEvent? = null

    init {
        advancementsRequired = apmc.advancements_required
        dragonEggShardsRequired = apmc.egg_shards_required
        totalDragonEggShards = apmc.egg_shards_available
        initializeInfoBar()
    }

    fun initializeInfoBar() {
        val bossInfoManager = server.getCustomBossEvents()
        advancementInfoBar = bossInfoManager.create(
            ResourceLocation.fromNamespaceAndPath(APRandomizer.MODID, "advancementinfobar"),
            Component.literal("")
        )
        advancementInfoBar!!.setMax(advancementsRequired)
        advancementInfoBar!!.setColor(BossEvent.BossBarColor.BLUE)
        advancementInfoBar!!.setOverlay(BossEvent.BossBarOverlay.NOTCHED_10)

        eggInfoBar = bossInfoManager.create(
            ResourceLocation.fromNamespaceAndPath(APRandomizer.MODID, "egginfobar"),
            Component.literal("")
        )
        eggInfoBar!!.setMax(dragonEggShardsRequired)
        eggInfoBar!!.setColor(BossEvent.BossBarColor.WHITE)
        eggInfoBar!!.setOverlay(BossEvent.BossBarOverlay.NOTCHED_6)

        connectionInfoBar = bossInfoManager.create(
            ResourceLocation.fromNamespaceAndPath(APRandomizer.MODID, "connectioninfobar"),
            Component.literal("Not connected to Archipelago").withStyle(
                Style.EMPTY.withColor(ChatFormatting.RED)
            )
        )
        connectionInfoBar!!.setMax(1)
        connectionInfoBar!!.setValue(1)
        connectionInfoBar!!.setColor(BossEvent.BossBarColor.RED)
        connectionInfoBar!!.setOverlay(BossEvent.BossBarOverlay.PROGRESS)

        updateInfoBar()
        advancementInfoBar!!.setVisible((advancementsRequired > 0))
        eggInfoBar!!.setVisible((dragonEggShardsRequired > 0))
        connectionInfoBar!!.setVisible(true)
    }

    fun updateGoal(canFinish: Boolean) {
        updateInfoBar()
        if (canFinish) checkGoalCompletion()
        checkBossMessages()
    }


    val advancementRemainingString: String
        get() {
            if (advancementsRequired > 0) {
                return String.format(
                    " Advancements (%d / %d)",
                    advancementManager.getFinishedAmount(),
                    advancementsRequired
                )
            }
            return ""
        }

    val eggShardsRemainingString: String
        get() {
            if (dragonEggShardsRequired > 0) {
                return String.format(" Dragon Egg Shards (%d / %d)", currentEggShards(), dragonEggShardsRequired)
            }
            return ""
        }

    private fun currentEggShards(): Int {
        return worldData.getDragonEggShards()
    }

    fun incrementDragonEggShards() {
        worldData.incrementDragonEggShards()
    }

    fun updateInfoBar() {
        val server = APRandomizer.getServer()
        if (server == null || advancementInfoBar == null || connectionInfoBar == null || eggInfoBar == null) return
        server.execute(Runnable {
            val players = server.getPlayerList().getPlayers()
            advancementInfoBar!!.setPlayers(players)
            eggInfoBar!!.setPlayers(players)
            connectionInfoBar!!.setPlayers(players)
        })

        advancementInfoBar!!.setValue(advancementManager.getFinishedAmount())
        eggInfoBar!!.setValue(currentEggShards())

        connectionInfoBar!!.setVisible(!APRandomizer.isConnected())

        advancementInfoBar!!.setName(Component.literal(this.advancementRemainingString))
        eggInfoBar!!.setName(Component.literal(this.eggShardsRemainingString))
    }

    fun checkGoalCompletion() {
        if (!APRandomizer.isConnected()) return
        val apClient = APRandomizer.getAP()
        if (apClient == null) return  // checked by isConnected but a failsafe doesn't hurt

        var hasGoal = goalsDone()
        if (apmc.required_bosses.hasDragon()) hasGoal =
            hasGoal && (APRandomizer.getWorldData() != null && APRandomizer.getWorldData()!!
                .isDragonKilled())
        if (apmc.required_bosses.hasWither()) hasGoal =
            hasGoal && (APRandomizer.getWorldData() != null && APRandomizer.getWorldData()!!
                .isWitherKilled())

        if (hasGoal) apClient.setGameState(ClientStatus.CLIENT_GOAL)
    }

    fun checkBossMessages() {
        val worldData = APRandomizer.getWorldData()
        if (worldData == null) return

        //check if the dragon message has been sent, and send it if needed.
        if (goalsDone() && worldData.getDragonState() == WorldData.ASLEEP && isBossRequired(Bosses.ENDER_DRAGON)) {
            worldData.setDragonState(WorldData.WAITING)
            Utils.PlaySoundToAll(SoundEvents.ENDER_DRAGON_AMBIENT)
            Utils.sendMessageToAll("The Dragon is waiting...")
            Utils.sendTitleToAll(
                Component.literal("The Dragon")
                    .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(Color.ORANGE.getRGB()))),
                Component.literal("is waiting..."),
                40,
                120,
                40
            )
        }

        //check if the wither message has been sent, and send it if needed.
        if (goalsDone() && worldData.getWitherState() == WorldData.ASLEEP && isBossRequired(Bosses.WITHER)) {
            worldData.setWitherState(WorldData.WAITING)
            Utils.PlaySoundToAll(SoundEvents.WITHER_AMBIENT)
            Utils.sendMessageToAll("The Darkness is calling...")
            Utils.sendTitleToAll(
                Component.literal("The Darkness")
                    .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(Color.BLACK.getRGB()))),
                Component.literal("is calling..."),
                40,
                120,
                40
            )
        }
    }

    fun goalsDone(): Boolean {
        return advancementManager.getFinishedAmount() >= advancementsRequired && this.currentEggShards() >= dragonEggShardsRequired
    }


    companion object {
        //subscribe to living death event to check for wither/dragon kills;
        @SubscribeEvent
        fun onBossDeath(event: LivingDeathEvent) {
            val mob = event.getEntity()
            val goalManager = APRandomizer.getGoalManager()
            if (goalManager == null) return
            val worldData = APRandomizer.getWorldData()
            if (worldData == null) return
            if (mob is EnderDragon && goalManager.goalsDone() && isBossRequired(Bosses.ENDER_DRAGON)) {
                worldData.setDragonKilled()
                Utils.sendMessageToAll("She is no more...")
                goalManager.updateGoal(false)
            }
            if (mob is WitherBoss && goalManager.goalsDone() && isBossRequired(Bosses.WITHER)) {
                worldData.setWitherKilled()
                Utils.sendMessageToAll("The Darkness has lifted...")
                goalManager.updateGoal(true)
            }
        }

        // check APMC.required_bosses to see if the boss is required
        fun isBossRequired(boss: Bosses?): Boolean {
            val required = APRandomizer.getApmcData().required_bosses

            // if it matches our goal its true
            if (required == boss) return true
            // a boss is required and you asked about none.
            if (boss == Bosses.NONE) return false
            // if both bosses are required, and you didn't ask about none, return ture;
            return required == Bosses.BOTH
        }
    }
}
