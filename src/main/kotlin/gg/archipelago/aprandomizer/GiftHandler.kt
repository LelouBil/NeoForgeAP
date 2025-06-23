package gg.archipelago.aprandomizer

import dev.koifysh.archipelago.parts.NetworkPlayer
import gg.archipelago.aprandomizer.ap.APClient
import gg.archipelago.gifting.api.CanGiftResult
import gg.archipelago.gifting.api.GiftItem
import gg.archipelago.gifting.api.GiftTrait
import gg.archipelago.gifting.api.GiftingServiceImpl
import gg.archipelago.gifting.api.SendGiftResult
import gg.archipelago.gifting.remote.GiftTraitName
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.launch
import net.minecraft.core.Holder
import net.minecraft.core.HolderSet
import net.minecraft.core.component.TypedDataComponent
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.tags.TagKey
import net.minecraft.util.context.ContextMap
import net.minecraft.world.effect.MobEffect
import net.minecraft.world.effect.MobEffectCategory
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.ai.attributes.Attribute
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.ai.attributes.RangedAttribute
import net.minecraft.world.food.FoodProperties
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.ItemUseAnimation
import net.minecraft.world.item.Items
import net.minecraft.world.item.Rarity
import net.minecraft.world.item.alchemy.PotionContents
import net.minecraft.world.item.component.BundleContents
import net.minecraft.world.item.component.Consumable
import net.minecraft.world.item.component.DeathProtection
import net.minecraft.world.item.component.ItemAttributeModifiers
import net.minecraft.world.item.component.ItemContainerContents
import net.minecraft.world.item.component.SuspiciousStewEffects
import net.minecraft.world.item.component.Tool
import net.minecraft.world.item.component.Weapon
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect
import net.minecraft.world.item.consume_effects.ClearAllStatusEffectsConsumeEffect
import net.minecraft.world.item.consume_effects.PlaySoundConsumeEffect
import net.minecraft.world.item.consume_effects.RemoveStatusEffectsConsumeEffect
import net.minecraft.world.item.consume_effects.TeleportRandomlyConsumeEffect
import net.minecraft.world.item.crafting.RecipeType
import net.minecraft.world.item.equipment.Equippable
import net.minecraft.world.level.block.Block
import net.neoforged.neoforge.capabilities.Capabilities
import net.neoforged.neoforge.common.BooleanAttribute
import net.neoforged.neoforge.common.PercentageAttribute
import net.neoforged.neoforge.fluids.capability.IFluidHandler
import java.util.concurrent.CompletableFuture
import kotlin.collections.plus
import kotlin.math.max
import kotlin.streams.asSequence

enum class GiftTraitSource {
    CRAFTING, TAGS, ITEM_COMPONENTS, ITEM_SPECIFIC, CAPABILITIES, BASED_ON_OTHER_TRAITS, ATTRIBUTE_MODIFIERS,
}

private data class GiftTraitHolder(
    val trait: GiftTrait,
    val source: GiftTraitSource,
) {
    val name: GiftTraitName
        get() = trait.name
    val quality: Float
        get() = trait.quality
    val duration: Float
        get() = trait.duration

    fun copy(source: GiftTraitSource, quality: Float = this.quality, duration: Float = this.duration): GiftTraitHolder {
        return GiftTraitHolder(
            trait = GiftTrait(name, quality, duration), source = source
        )
    }
}

private fun GiftTrait.convert(source: GiftTraitSource): GiftTraitHolder {
    return GiftTraitHolder(
        trait = this, source = source
    )
}

private fun List<GiftTrait>.convert(source: GiftTraitSource): List<GiftTraitHolder> {
    return this.map { it.convert(source) }
}

private val CanGiftResult.CanGiftError.userFacing
    get() = when (this) {
        CanGiftResult.CanGiftError.DataStorageWriteError -> "Failed to write to data storage."
        is CanGiftResult.CanGiftError.DataVersionTooLow -> "The recipient's data version is too low to receive gifts. Minimum required: ${this.recipientMinimumVersion}."

        CanGiftResult.CanGiftError.GiftBoxClosed -> "The recipient's gift box is closed."
        is CanGiftResult.CanGiftError.NoMatchingTraits -> "The recipient does not accept any of the traits of this gift. Accepted traits: ${
            this.recipientAcceptedTraits.joinToString(
                ", "
            )
        }."

        is CanGiftResult.CanGiftError.PlayerSlotNotFound -> "The recipient's player slot ${this.playerSlot} was not found."
    }

private const val averageDurability = 250f

@OptIn(DelicateCoroutinesApi::class)
class GiftHandler(client: APClient) {
    fun openGiftBox(): CompletableFuture<Boolean> {
        return GlobalScope.async { giftingService.openGiftBox(true, emptyList()) }.asCompletableFuture()
    }

    private val giftingService = GiftingServiceImpl(client)
    private val matcher = BKTreeCloseTraitParser<ItemStack>()

    var recepTask: Job? = null
    fun startReception() {
        if (recepTask != null) return
        recepTask = GlobalScope.launch {
            println("Starting gift reception")
            giftingService.receivedGifts.collect { gift ->
                println("Received gift $gift")
                val itemStack: ItemStack? = resolveItem(gift.traits, gift.amount)
                if (itemStack == null) {
                    println("Failed to resolve item for gift: $gift")
                    giftingService.refundGift(gift)
                    return@collect
                }
                val recipient = APRandomizer.server!!.playerList.players.random()
                val res = recipient.addItem(itemStack)
                if (!res) {
                    println("Failed to add gift item to player ${recipient.name}. Item: $itemStack, Amount: ${gift.amount}")
                    giftingService.refundGift(gift)
                } else {
                    println("Gift item added to player ${recipient.name}. Item: $itemStack, Amount: ${gift.amount}")
                }

            }
        }
    }

    init {

        GlobalScope.launch {
            println("Registering all items as gifts")
            BuiltInRegistries.ITEM.forEach { item ->
                if (item == Items.AIR || item == Items.AIR.asItem()) return@forEach // skip air item
                println("Registering item: ${BuiltInRegistries.ITEM.getKey(item)}")
                val stack = ItemStack(item, 1)
                val traits = resolveItemTraits(stack).map { it.trait }
                matcher.RegisterAvailableGift(stack, traits)
            }
            println("Finished")
        }
    }


    private val craftTraitsCache = mutableMapOf<Item, List<GiftTraitHolder>>()
    private fun getCraftTraits(item: Item): List<GiftTraitHolder> {
        if (craftTraitsCache.containsKey(item)) {
            return craftTraitsCache[item]!!
        }
        craftTraitsCache[item] = emptyList() // todo find better way to avoid infinite recursion
        val recipeMap = APRandomizer.server!!.recipeManager.recipeMap()
        // find recipes outputting the item
        val recipesMaking = recipeMap.byType(RecipeType.CRAFTING).mapNotNull { recipe ->
            recipe.value.display()
                .flatMap { it.result().resolveForStacks(ContextMap.EMPTY) }
                .find { it.item == item }
                ?.let { recipe to it }
        }

        //find recipes which use the item as an ingredient
        val recipesUsing = recipeMap.byType(RecipeType.CRAFTING).filter { recipe ->
            recipe.value.placementInfo().ingredients().any { ing ->
                if (ing.isCustom) {
                    ing.customIngredient!!.items().anyMatch { it.value() == item }
                } else {
                    ing.values.any { it.value() == item }
                }
            }
        }

        val materialScore = (recipesUsing.count() / 10f).coerceAtMost(3f) // arbitrary
        val materialTrait = if (materialScore > 0) {
            KnownTraits.Material.copy(quality = materialScore).convert(GiftTraitSource.CRAFTING)
        } else {
            null
        }
        val traits = recipesMaking.map { (recipe,output) ->
            val ingredients = recipe.value.placementInfo().ingredients()
            val allIngredientTraits: List<GiftTraitHolder> = ingredients.flatMap { ing ->
                val itemSet = if (ing.isCustom) ing.customIngredient!!.items().toList() else ing.values
                val singleIngredientTraits = when (itemSet) {
                    is HolderSet.Named<Item> -> itemTagsToTraits(itemSet.key())
                    is HolderSet.ListBacked<Item> -> itemSet.stream().asSequence().map {
                        resolveItemTraits(ItemStack(it, 1))
                    }.reduce { acc, traits ->
                        val namesInter = acc.map { it.name }.intersect(traits.map { it.name })
                        (acc + traits).filter { it.name in namesInter }
                    }

                    else -> emptyList()
                }
                singleIngredientTraits.map {
                    it.copy(
                        GiftTraitSource.CRAFTING, quality = it.quality / output.count
                    )
                }
            }
            allIngredientTraits
        }.flatten()
            // average qualities of same traits
            .groupBy {
                it.name
            }.map { (name, traits) ->
                traits.reduce { acc, trait ->
                    trait.copy(
                        source = GiftTraitSource.CRAFTING,
                        quality = (acc.quality + trait.quality) / 2f,
                        duration = (acc.duration + trait.duration) / 2f
                    )
                }
            }.filter {
                it.quality > 0.01f // filter out traits with very low quality
            }
        return (traits + materialTrait?.let { listOf(it) }.orEmpty()).filter {
            craftingInheritedTraits.isMember(it)
        }.also { craftTraitsCache[item] = it }
    }

    fun closeGiftBox(): CompletableFuture<Boolean> {
        return GlobalScope.async {
            giftingService.closeGiftBox();
        }.asCompletableFuture()
    }

    /**
     * Checks if the item can be sent to the recipient.
     *
     * @param stack The item
     * @param recipient The player receiving the item.
     * @return A user-facing string indicating the reason if it cannot be sent, or null if it can be sent.
     */
    fun canSendItem(stack: ItemStack, recipient: NetworkPlayer): CompletableFuture<String?> {
        return GlobalScope.async {
            val res = giftingService.canGiftToPlayer(
                recipient.slot, recipient.team, getGiftItem(stack).traits.map { it.name })


            when (res) {
                is CanGiftResult.CanGiftError -> res.userFacing
                is CanGiftResult.CanGiftSuccess -> null
            }
        }.asCompletableFuture()
    }

    /**
     * Gifts the item to the recipient.
     *
     * @param stack The item to be gifted.
     * @param recipient The player receiving the item.
     * @return A user-facing string indicating the result of the gifting operation, or null if successful.
     */
    fun giftItem(stack: ItemStack, recipient: NetworkPlayer): CompletableFuture<String?> {
        return GlobalScope.async {
            val item = getGiftItem(stack)
            println("Gifting item: $item to recipient: ${recipient.name} (slot: ${recipient.slot}, team: ${recipient.team})")
            val res = giftingService.sendGift(
                item = item,
                amount = stack.count,
                recipientPlayerSlot = recipient.slot,
                recipientPlayerTeam = recipient.team
            )

            when (res) {
                SendGiftResult.SendGiftFailure.DataStorageWriteError -> "Failed to write to data storage."
                is SendGiftResult.SendGiftFailure.CannotGift -> "Cannot gift: ${res.reason.userFacing}"
                SendGiftResult.SendGiftSuccess -> null
            }
        }.asCompletableFuture()
    }

    private fun itemSpecificTraits(item: Item): List<GiftTraitHolder> = convertScope(GiftTraitSource.ITEM_SPECIFIC) {
        val res: List<GiftTraitHolder> = when (item) {
            Items.TNT -> listOf(KnownTraits.Bomb).convert()
            Items.TOTEM_OF_UNDYING -> listOf(KnownTraits.Artifact, KnownTraits.Ancient).convert()
            Items.ELYTRA -> listOf(KnownTraits.Artifact).convert()
            Items.SNOWBALL -> listOf(KnownTraits.Ice, KnownTraits.Throwing).convert()
            // because the automatic inheritance only works if a "milk" or "powder_snow" fluid exists
            Items.POWDER_SNOW_BUCKET -> listOf(KnownTraits.Ice) + resolveItemTraits(ItemStack(Items.BUCKET))
            Items.MILK_BUCKET -> resolveItemTraits(ItemStack(Items.BUCKET))
            Items.DECORATED_POT -> listOf(KnownTraits.Ceramic).convert()
            Items.ENCHANTED_BOOK -> listOf(KnownTraits.Scroll, KnownTraits.IQ, KnownTraits.Buff).convert()
            Items.BONE -> listOf(KnownTraits.Bone).convert()
            Items.BONE_BLOCK -> listOf(KnownTraits.Fossil, KnownTraits.Bone).convert()
            Items.BONE_MEAL -> listOf(KnownTraits.Bone).convert()
            Items.REDSTONE -> listOf(KnownTraits.Energy.copy(quality = 0.1f)).convert()
            Items.REDSTONE_BLOCK -> listOf(KnownTraits.Energy.copy(quality = 0.5f)).convert()
            Items.GLOWSTONE_DUST -> listOf(KnownTraits.Light.copy(quality = 0.1f)).convert()
            Items.GLOWSTONE -> listOf(KnownTraits.Light.copy(quality = 0.5f)).convert()
            Items.NOTE_BLOCK -> listOf(KnownTraits.Instrument).convert()
            Items.JUKEBOX -> listOf(KnownTraits.Instrument.copy(quality = 0.5f)).convert()
            Items.REPEATER -> listOf(KnownTraits.Electronics.copy(quality = 0.1f)).convert()
            Items.COMPARATOR -> listOf(KnownTraits.Electronics.copy(quality = 0.5f)).convert()
            Items.PAINTING -> listOf(KnownTraits.Luxury).convert()
            Items.ITEM_FRAME -> listOf(KnownTraits.Luxury.copy(quality = 0.5f)).convert()
            Items.ARMOR_STAND -> listOf(KnownTraits.Luxury.copy(quality = 0.1f), KnownTraits.Statue).convert()
            Items.GOAT_HORN -> listOf(KnownTraits.Instrument, KnownTraits.Goat).convert()
            Items.CLAY -> listOf(KnownTraits.Beach, KnownTraits.Clay).convert()
            Items.DEAD_BUSH -> listOf(KnownTraits.Bush, KnownTraits.Dry).convert()
            Items.HEART_OF_THE_SEA -> listOf(KnownTraits.Artifact, KnownTraits.Ocean, KnownTraits.Ancient).convert()
            Items.NAUTILUS_SHELL -> listOf(KnownTraits.Ocean, KnownTraits.Ancient).convert()
            Items.TRIDENT -> listOf(KnownTraits.Trident, KnownTraits.Ocean, KnownTraits.Ancient).convert()
            Items.PAPER -> listOf(KnownTraits.Paper).convert()
            Items.FIREWORK_ROCKET -> listOf(KnownTraits.Firework, KnownTraits.Rocket).convert()
            Items.SPLASH_POTION -> listOf(KnownTraits.Throwing).convert()
            Items.LINGERING_POTION -> listOf(KnownTraits.Throwing).convert()
            Items.ENDER_PEARL -> listOf(KnownTraits.Teleport, KnownTraits.Throwing).convert()
            else -> emptyList()
        } + match(item.toString()){
            "torch" includes KnownTraits.Light
            "soul" includes SpecificTraits.Nether
        } + if(item is BlockItem) {
            val emission = item.block.defaultBlockState().lightEmission / 15f // light emission is between 0 and 15
            if (emission > 0) {
                listOf(KnownTraits.Light.copy(quality = emission)).convert()
            } else emptyList()
        } else emptyList()

        res + if (APRandomizer.server!!.overworld().fuelValues().isFuel(ItemStack(item, 1))) {
            listOf(KnownTraits.Fuel).convert()
        } else {
            emptyList()
        }
    }

    private fun capabilities(item: ItemStack): List<GiftTraitHolder> = convertScope(GiftTraitSource.CAPABILITIES) {
        val traits = mutableListOf<GiftTraitHolder>()
        if (item.getCapability(Capabilities.ItemHandler.ITEM) != null) {
            traits.add(KnownTraits.Container.convert())
        }
        val fluidcap = item.getCapability(Capabilities.FluidHandler.ITEM)
        if (fluidcap != null) {
            traits.add(KnownTraits.Container.convert())
            traits.add(KnownTraits.LiquidContainer.convert())
            val fls = (0..fluidcap.tanks).map { fluidcap.getFluidInTank(it) }
            fls.forEach { fluid ->
                traits.add(
                    GiftTrait(
                        name = GiftTraitName(fluid.fluidHolder.key!!.location().path.capitalize()),
                        quality = fluid.amount / 1000f
                    ).convert()
                )
            }

            val copied = item.copy()
            val capability = copied.getCapability(Capabilities.FluidHandler.ITEM)
            for (i in 0 until capability!!.tanks) {
                val fluidInTank = capability.getFluidInTank(i)
                if (fluidInTank.isEmpty) continue
                capability.drain(fluidInTank.copyWithAmount(Int.MAX_VALUE), IFluidHandler.FluidAction.EXECUTE);
            }
            val itemWithoutFluid = capability.container
            if (itemWithoutFluid.item != item.item) {
                // add all traits that weren't already present
                val resolveItemTraits: List<GiftTraitHolder> = resolveItemTraits(itemWithoutFluid)
                resolveItemTraits.forEach { trait ->
                    if (traits.none { it.name == trait.name }) {
                        traits.add(trait)
                    }
                }
            }

        }
        val energyCap = item.getCapability(Capabilities.EnergyStorage.ITEM)
        if (energyCap != null) {
            traits.add(KnownTraits.Energy.copy(quality = energyCap.energyStored / 1000f).convert())
        }
        return traits
    }

    private fun resolveItemTraits(
        stack: ItemStack,
    ): List<GiftTraitHolder> {
        val item = stack.item
        val traits = (stack.components.flatMap {
            componentToTraits(it)
        } + when (item) {
            is BlockItem -> {
                BuiltInRegistries.BLOCK.wrapAsHolder(item.block).tags().toList().flatMap(::blockTagsToTraits)
            }

            else -> emptyList()
        } + BuiltInRegistries.ITEM.wrapAsHolder(item).tags().toList().flatMap(::itemTagsToTraits) + itemSpecificTraits(
            item
        ) + capabilities(stack) + if (stack.nextDamageWillBreak() || stack.isBroken) {
            listOf(KnownTraits.Broken).convert(GiftTraitSource.ITEM_COMPONENTS)
        } else {
            emptyList()
        })
        return removeDuplicates(traits + basedOn(stack, traits) + getCraftTraits(stack.item))
    }

    fun getGiftItem(itemStack: ItemStack): GiftItem {
        val item = itemStack.item
        return GiftItem(
            name = BuiltInRegistries.ITEM.getKey(item).toString(),
            traits = resolveItemTraits(itemStack).map { it.trait })
    }

    private fun removeDuplicates(traits: List<GiftTraitHolder>): List<GiftTraitHolder> {
        return traits.groupBy { it.trait.name }.map { (name, traits) ->
            traits.reduce { acc, trait ->
                when (trait.source) {
                    GiftTraitSource.ITEM_COMPONENTS -> {
                        // keep the item component traits as they are
                        when (acc.source) {
                            GiftTraitSource.ITEM_COMPONENTS -> {
                                // if the acc is also from item components, average the quality and duration
                                acc.copy(
                                    source = GiftTraitSource.ITEM_COMPONENTS,
                                    quality = max(acc.quality, trait.quality),
                                    duration = max(acc.duration, trait.duration)
                                )
                            }

                            else -> trait // if the acc is not from item components, just keep the current trait
                        }
                    }

                    GiftTraitSource.CRAFTING -> {
                        when (acc.source) {
                            GiftTraitSource.CRAFTING -> {
                                // if the acc is also from crafting, add quality and average duration
                                acc.copy(
                                    source = GiftTraitSource.CRAFTING,
                                    quality = (acc.quality + trait.quality),
                                    duration = (acc.duration + trait.duration) / 2f
                                )
                            }

                            else -> trait // if the acc is not from crafting, just keep the current trait
                        }
                    }

                    GiftTraitSource.TAGS -> {
                        trait.copy(source = GiftTraitSource.TAGS, quality = 1f)
                    }

                    else -> {
                        // for all other sources, just average the quality and duration
                        trait.copy(
                            source = acc.source,
                            quality = (acc.quality + trait.quality) / 2f,
                            duration = (acc.duration + trait.duration) / 2f
                        )
                    }
                }
            }

        }
    }

    private fun basedOn(itemStack: ItemStack, traits: List<GiftTraitHolder>): List<GiftTraitHolder> = convertScope(
        GiftTraitSource.BASED_ON_OTHER_TRAITS
    ) {


        val attackDamage = traits.find { it.name == KnownTraits.Damage.name }
        val attackSpeed = traits.find { it.name == ExtraTraits.AttackSpeed.name }
        val armorValue = traits.find { it.name == KnownTraits.Armor.name }
        val armorToughness = traits.find { it.name == ExtraTraits.ArmorToughness.name }
        val hasFire = traits.any { it.name == KnownTraits.Fire.name }

        return traits.flatMap { trait ->
            val duration = (itemStack.maxDamage - itemStack.damageValue) / averageDurability
            if (objectWithUsage.isMember(trait)) {
                listOf(
                    trait.copy(
                        source = GiftTraitSource.BASED_ON_OTHER_TRAITS,
                        duration = duration,
                    )
                )
            } else if (itemStack.isDamageableItem) {
                listOf(
                    trait, KnownTraits.Tool.copy(duration = duration, quality = 0.1f).convert()
                )
            } else listOf(trait)
        }.map { trait ->
            if (weaponTraits.isMember(trait)) {
                val weaponValue = (attackSpeed?.quality?.div(4f) ?: 1f) * (attackDamage?.quality ?: 1f)
                trait.copy(source = GiftTraitSource.BASED_ON_OTHER_TRAITS, quality = weaponValue)
            } else trait
        }.map { trait ->
            if (trait.name == KnownTraits.Armor.name) {
                val armorValue = armorValue?.quality ?: 0f
                val toughnessBonus = armorToughness?.quality?.div(20f) ?: 0f // Toughness is capped at 20
                trait.copy(
                    source = GiftTraitSource.BASED_ON_OTHER_TRAITS, quality = armorValue * (1 + toughnessBonus)
                )
            } else trait
        }.flatMap { trait ->
            if (compositionTraits.isMember(trait) && !objectWithUsage.isMember(trait)) {
                listOf(trait, KnownTraits.Resource.copy(quality = 0.1f).convert())
            } else listOf(trait)
        }.flatMap { trait ->
            if(trait.name == SpecificTraits.Nether.name && !hasFire) {
                // if the item is from the nether, but does not have fire, add fire trait
                listOf(trait, KnownTraits.Fire.copy(quality = 0.1f).convert())
            } else {
                listOf(trait)
            }
        }
    }

    //todo duration = durability (but take into account durability usage per use)
    private fun componentToTraits(component: TypedDataComponent<*>): List<GiftTraitHolder> =
        convertScope(GiftTraitSource.ITEM_COMPONENTS) {
            return when (val data = component.value) {
                is Consumable -> {
                    return listOf(KnownTraits.Consumable) + data.onConsumeEffects.flatMap {
                        return when (it) {
                            is ApplyStatusEffectsConsumeEffect -> effectsToTraits(it.effects)
                            is ClearAllStatusEffectsConsumeEffect -> listOf(KnownTraits.Cure).convert()
                            is TeleportRandomlyConsumeEffect -> listOf(KnownTraits.Teleport).convert()
                            is RemoveStatusEffectsConsumeEffect -> listOf() //todo ?
                            is PlaySoundConsumeEffect -> listOf() //todo ?
                            else -> error("unreachable")
                        }
                    } + when (data.animation) {
                        ItemUseAnimation.EAT -> listOf(KnownTraits.Food).convert()
                        ItemUseAnimation.DRINK -> listOf(KnownTraits.Drink).convert()
                        else -> emptyList()
                    }

                }

                is Rarity -> when (data) {
                    Rarity.COMMON -> emptyList()
                    Rarity.UNCOMMON -> emptyList()
                    Rarity.RARE -> emptyList()
                    Rarity.EPIC -> listOf(KnownTraits.Legendary).convert()
                }

                is SuspiciousStewEffects -> listOf(
                    ExtraTraits.Suspicious,
                    KnownTraits.Random,
                    KnownTraits.Food.copy(quality = 0.1f),
                    KnownTraits.Buff.copy(quality = 0.5f),
                    KnownTraits.Trap.copy(quality = 0.5f)
                ).convert()

                is Tool -> listOf(KnownTraits.Tool.copy(quality = data.defaultMiningSpeed)).convert() + data.rules.flatMap {
                    val tag = (it.blocks as? HolderSet.Named<Block>)?.key() ?: return@flatMap emptyList()
                    blockTagsToTraits(tag).map { t ->
                        t.copy(
                            GiftTraitSource.ITEM_COMPONENTS, quality = it.speed.orElse(data.defaultMiningSpeed)
                        )
                    }
                }

                is Weapon -> listOf(KnownTraits.Weapon).convert()
                is FoodProperties -> listOf(KnownTraits.Food.copy(quality = data.nutrition() / 3f)).convert()
                is Equippable -> when (data.slot) {
                    EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.BODY, EquipmentSlot.HEAD -> listOf(
                        KnownTraits.Armor
                    ).convert()

                    EquipmentSlot.SADDLE -> listOf(ExtraTraits.Saddle).convert()
                    else -> emptyList()
                } + if (data.slot == EquipmentSlot.HEAD) {
                    listOf(KnownTraits.Head).convert()
                } else {
                    emptyList()
                }

                is PotionContents -> effectsToTraits(data.allEffects)
                is DeathProtection -> listOf(
                    KnownTraits.Life.copy(quality = 10f),
                    KnownTraits.Buff.copy(quality = 10f),
                    KnownTraits.Invincible.copy(duration = 0.1f),

                    ).convert()

                is BundleContents -> listOf(KnownTraits.Container).convert()
                is ItemContainerContents -> listOf(KnownTraits.Container).convert()

                is ItemAttributeModifiers -> data.modifiers.flatMap {
                    // ignore slot since it should be based on other tags ?
                    // or maybe I should add specific conditions like, if slot is hand and modifier is attack damage, then add weapon trait?
                    modifierToTraits(it)
                }

                else -> return emptyList()
            }
        }

    private fun modifierToTraits(modifier: ItemAttributeModifiers.Entry): List<GiftTraitHolder> {
        return when (modifier.attribute) {
            Attributes.ATTACK_DAMAGE -> listOf(
                KnownTraits.Damage.copy(
                    quality = scaleQuality(
                        modifier.attribute.value(),
                        modifier.modifier,
                        2.0,
                    ).toFloat()
                ),
            )

            Attributes.ATTACK_SPEED -> listOf(
                ExtraTraits.AttackSpeed.copy(
                    quality = scaleQuality(
                        modifier.attribute.value(),
                        modifier.modifier,
                        2.0,
                    ).toFloat()
                ),
            ) // todo scale attack speed based on weapon averages
            Attributes.MOVEMENT_SPEED -> listOf(
                KnownTraits.Speed.copy(quality = scaleQuality(modifier.attribute.value(), modifier.modifier).toFloat()),
            )

            Attributes.MAX_HEALTH, Attributes.MAX_ABSORPTION -> listOf(
                KnownTraits.Life.copy(quality = scaleQuality(modifier.attribute.value(), modifier.modifier).toFloat()),
            )

            Attributes.ARMOR, Attributes.ARMOR_TOUGHNESS -> listOf(
                ExtraTraits.ArmorToughness.copy(
                    quality = scaleQuality(
                        modifier.attribute.value(), modifier.modifier
                    ).toFloat()
                ),
            )

            else -> emptyList()
        }.convert(GiftTraitSource.ATTRIBUTE_MODIFIERS)
    }

    private fun scaleQuality(
        attribute: Attribute, value: AttributeModifier, base: Double = attribute.defaultValue
    ): Double {
        return when (attribute) {
            is PercentageAttribute -> (applyOperation(attribute.defaultValue, value) / base) - 1f
            is RangedAttribute -> (applyOperation(attribute.defaultValue, value) / base) - 1f
            is BooleanAttribute -> value.amount
            else -> error("unreachable")
        }
    }

    private fun applyOperation(attribute: Double, value: AttributeModifier): Double {
        return when (value.operation) {
            AttributeModifier.Operation.ADD_VALUE -> attribute + value.amount
            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL -> attribute * (1 + value.amount)
            AttributeModifier.Operation.ADD_MULTIPLIED_BASE -> attribute * (1 + value.amount)
        }
    }

    private fun effectsToTraits(effects: Iterable<MobEffectInstance>): List<GiftTraitHolder> {
        return effects.flatMap {
            convertToTrait(it.effect).map { n ->
                GiftTrait(
                    name = n, quality = it.amplifier.toFloat(), duration = potionDurationToTraitDuration(it.duration)
                )
            } + when (it.effect.value().category) {
                MobEffectCategory.BENEFICIAL -> listOf(
                    KnownTraits.Buff.copy(
                        quality = it.amplifier.toFloat(), duration = potionDurationToTraitDuration(it.duration)
                    )
                )

                MobEffectCategory.HARMFUL -> listOf(
                    KnownTraits.Trap.copy(
                        quality = it.amplifier.toFloat(), duration = potionDurationToTraitDuration(it.duration)
                    )
                )

                MobEffectCategory.NEUTRAL -> emptyList()
            }
        }.convert(GiftTraitSource.ITEM_COMPONENTS)
    }

    private fun convertToTrait(effect: Holder<MobEffect>): List<GiftTraitName> {
        return when (effect) {
            MobEffects.SPEED -> listOf(KnownTraits.Speed.name.name)
            MobEffects.SLOWNESS -> listOf("Slowness")
            MobEffects.HASTE -> listOf("Haste")
            MobEffects.MINING_FATIGUE -> listOf("MiningFatigue")
            MobEffects.STRENGTH -> listOf("Strength")
            MobEffects.INSTANT_HEALTH -> listOf(KnownTraits.Heal.name.name)
            MobEffects.INSTANT_DAMAGE -> listOf("Damage")
            MobEffects.JUMP_BOOST -> listOf("JumpBoost")
            MobEffects.NAUSEA -> listOf("Nausea")
            MobEffects.REGENERATION -> listOf(KnownTraits.Heal.name.name)
            MobEffects.RESISTANCE -> listOf(KnownTraits.Armor.name.name)
            MobEffects.FIRE_RESISTANCE -> listOf(KnownTraits.Armor.name.name, KnownTraits.Fire.name.name)
            MobEffects.WATER_BREATHING -> listOf("WaterBreathing", KnownTraits.Water.name.name)
            MobEffects.INVISIBILITY -> listOf("Invisible")
            MobEffects.BLINDNESS -> listOf("Blindness")
            MobEffects.NIGHT_VISION -> listOf("NightVision")
            MobEffects.HUNGER -> listOf("Hunger")
            MobEffects.WEAKNESS -> listOf("Weakness")
            MobEffects.POISON -> listOf(KnownTraits.Poison.name.name)
            MobEffects.WITHER -> listOf(KnownTraits.Poison.name.name, "Wither")
            MobEffects.HEALTH_BOOST -> listOf(KnownTraits.Life.name.name)
            MobEffects.ABSORPTION -> listOf(KnownTraits.Life.name.name, "Absorption")
            MobEffects.SATURATION -> listOf(KnownTraits.Food.name.name, "Saturation")
            MobEffects.GLOWING -> listOf("Glowing", KnownTraits.Light.name.name)
            MobEffects.LEVITATION -> listOf("Levitation", KnownTraits.Flight.name.name)
            MobEffects.LUCK -> listOf(KnownTraits.Luck.name.name)
            MobEffects.UNLUCK -> listOf(KnownTraits.Unluck.name.name)
            MobEffects.SLOW_FALLING -> listOf("SlowFalling", KnownTraits.Flight.name.name)
            MobEffects.CONDUIT_POWER -> listOf(
                KnownTraits.Ocean.name.name, KnownTraits.Water.name.name, "NightVision", "WaterBreathing"
            )

            MobEffects.DOLPHINS_GRACE -> listOf(
                KnownTraits.Ocean.name.name, KnownTraits.Water.name.name, "DolphinsGrace", KnownTraits.Speed.name.name
            )

            MobEffects.BAD_OMEN -> listOf(ExtraTraits.Ominous.name.name, KnownTraits.Trap.name.name)
            MobEffects.HERO_OF_THE_VILLAGE -> listOf("HeroOfTheVillage")
            else -> emptyList()
        }.map { GiftTraitName(it) }


    }

    private interface IsScope {
        infix fun String.matches(trait: GiftTrait)
        fun String.matches(vararg traits: GiftTrait)
        infix fun String.containingMeans(trait: GiftTrait)

        infix fun List<String>.matches(trait: GiftTrait) {
            this.forEach { t ->
                t matches trait
            }
        }

        infix fun String.includes(trait: GiftTrait)
        infix fun List<String>.includes(trait: GiftTrait) {
            this.forEach { t ->
                t includes trait
            }
        }

        fun String.includes(vararg traits: GiftTrait)
    }


    private fun splat(t: String): List<String> {
        return t.split(":")[1].split( '/', '_', '-')
    }

    private inline fun ConvertScope.match(t: String, block: IsScope.() -> Unit): List<GiftTraitHolder> {
        val res = mutableListOf<GiftTraitHolder>()
        val obj = object : IsScope {
            override fun String.matches(trait: GiftTrait) {
                assert(this.contains(':'))
                if (this@matches == t) {
                    res.add(trait.convert())
                }
            }

            override fun String.matches(vararg traits: GiftTrait) {
                assert(this.contains(':'))
                if (this@matches == t) {
                    res.addAll(traits.toList().convert())
                }
            }

            override fun String.containingMeans(trait: GiftTrait) {
                assert(!this.contains(':'))
                if(t.split(":")[1].contains(this)){
                    res.add(trait.convert())
                }
            }

            override fun String.includes(trait: GiftTrait) {
                assert(!this.contains(':'))
                if (splat(t).contains(this@includes)) {
                    res.add(trait.convert())
                }
            }

            override fun String.includes(vararg traits: GiftTrait) {
                assert(!this.contains(':'))
                if (splat(t).contains(this@includes)) {
                    res.addAll(traits.toList().convert())
                }
            }
        }
        block.invoke(obj)
        return res
    }

    private fun commonTags(t: String): List<GiftTraitHolder> = convertScope(GiftTraitSource.TAGS) {
        if(t.contains("incorrect_for")
            || splat(t).contains("needs")
            || splat(t).contains("repleacables")
            ) {
            return@convertScope emptyList()
        }
        match(t) {
            "c:stones" matches KnownTraits.Stone
            "base_stone" containingMeans KnownTraits.Stone
            "c:foods/vegetable" matches KnownTraits.Vegetable
            "c:foods" matches KnownTraits.Food
            "c:raw_materials" matches KnownTraits.Material
            "c:drinks" matches KnownTraits.Drink
            "c:woods" matches KnownTraits.Wood
            "wooden" includes KnownTraits.Wood
            listOf(
                "c:grass",
                "c:grass_variants",
                "minecraft:leaves",
                "c:flowers",
                "minecraft:dirt",
                "minecraft:lush_plants_replaceable",
                "minecraft:replaceable_plants",
                "c:crops",
                "c:seeds"
            ) matches KnownTraits.Grass
            "c:ores" matches KnownTraits.Ore
            "c:eggs" matches KnownTraits.Egg
            "c:foods/cooked_egg" matches KnownTraits.Egg
            listOf("c:foods/cooked_fish", "c:foods/raw_fish") matches KnownTraits.Fish
            "c:tools" matches KnownTraits.Tool
            listOf(
                "minecraft:enchantable/weapon", "c:tools/melee_weapons", "c:tools/ranged_weapons"
            ) matches KnownTraits.Weapon
            "c:tools/melee_weapons" matches KnownTraits.MeleeWeapon
            "c:tools/ranged_weapons" matches KnownTraits.RangedWeapon
            listOf(
                "c:buckets/entity_water", "c:foods/raw_fish",
                "c:cooked_meat", "c:foods/raw_meat",
            ) matches KnownTraits.Animal
            "c:armors" matches KnownTraits.Armor
            "c:foods/fruit" matches KnownTraits.Fruit
            "copper" includes KnownTraits.Copper
            "coal" includes KnownTraits.Coal
            listOf("c:ingots", "minecraft:beacon_base_blocks") matches KnownTraits.Metal
            listOf("c:foods/raw_meat", "c:foods/cooked_meat") matches KnownTraits.Meat
            "gold" includes KnownTraits.Gold
            "cooked" includes KnownTraits.Cooking
            listOf("c:foods/cookie", "c:foods/soup") matches KnownTraits.Cooking
            "c:netherracks" matches SpecificTraits.Nether
            "nether" includes SpecificTraits.Nether
            "netherrack" includes SpecificTraits.Nether
            "c:rods/blaze" matches KnownTraits.Fire
            "c:flowers" matches KnownTraits.Flower
            "minecraft:planks" matches KnownTraits.Lumber
            "minecraft:stripped_logs" matches KnownTraits.Lumber
            "c:gems" matches KnownTraits.Gem
            listOf("c:stones","c:obsidians") matches KnownTraits.Mineral
            Items.SHULKER_BOX
            "silver" includes KnownTraits.Silver
            "coffee" includes KnownTraits.Coffee
            "c:buckets/water" matches KnownTraits.Water
            "c:drinks/watery" matches KnownTraits.Water
            listOf(
                "c:shulker_boxes", "c:chests", "c:barrels", "c:buckets"
            ) matches KnownTraits.Container

            listOf(
                "minecraft:ice", "minecraft:snow"
            ) matches KnownTraits.Ice
            "c:stones" matches KnownTraits.Rock
            "boulder" includes KnownTraits.Boulder
            "minecraft:bookshelf_books" matches KnownTraits.Book
            "minecraft:bookshelf_books" matches KnownTraits.Paper
            "c:seeds" matches KnownTraits.Seed
            "c:cave_vines" matches KnownTraits.Vine
            listOf("vine", "vines") includes KnownTraits.Vine
            "c:dyes" matches KnownTraits.Dye
            listOf("c:flower_pots", "minecraft:decorated_pot_sherds") matches KnownTraits.Ceramic
            "c:villager_currencies" matches KnownTraits.Currency
            "c:mushrooms" matches KnownTraits.Mushroom
            "c:bones" matches KnownTraits.Bone
            "iron" includes KnownTraits.Iron
            "c:foods/berry" matches KnownTraits.Berry
            listOf(
                "c:villager_job_sites", "c:player_workstations/furnaces", "c:player_workstations/crafting_tables"
            ) matches KnownTraits.Machine
            listOf("c:slime_balls", "c:storage_blocks/slime") matches KnownTraits.Goo
            listOf(
                "c:clusters", "c:buds",
            ) matches KnownTraits.Crystal
            listOf("amethyst", "quartz") includes KnownTraits.Crystal
            listOf("c:foods/cooked_chicken", "c:foods/raw_chicken") matches KnownTraits.Chicken
            "c:leathers" matches KnownTraits.Leather
            "c:dusts/salt" matches KnownTraits.Salted
            "platinum" includes KnownTraits.Platinum
            "insect" includes KnownTraits.Insect
            listOf("oil", "petrol", "petroleum") includes KnownTraits.Oil
            "diamond" includes KnownTraits.Diamond
            "c:drinks/juice" matches KnownTraits.Juice
            "milk" includes KnownTraits.Milk
            "milk" includes KnownTraits.AnimalProduct
            listOf("torch", "glowstone") includes KnownTraits.Light
            "c:potions" matches KnownTraits.Potion
            "c:sands" matches KnownTraits.Sand
            "c:sands" matches KnownTraits.Beach
            "c:fertilizers" matches KnownTraits.Fertilizer
            "c:fiber" matches KnownTraits.Fiber
            "c:player_workstations/furnaces" matches KnownTraits.Furnace
            "c:player_workstations/crafting_tables" matches KnownTraits.Crafting
            "minecraft:saplings" matches KnownTraits.TreeSeed
            "prismarine" includes KnownTraits.Water
            "prismarine" includes KnownTraits.Ocean
            "prismarine" includes KnownTraits.Prismarine
            "prismarine" includes KnownTraits.Aquamarine
            listOf("c:foods/cooked_beef", "c:foods/raw_beef", "c:milk", "c:milks") matches KnownTraits.Cow
            listOf("c:foods/cooked_beef", "c:foods/raw_beef") matches KnownTraits.Beef
            listOf("minecraft:logs", "minecraft:leaves", "minecraft:saplings") matches KnownTraits.Tree
            listOf("c:sand", "c:cactus") matches KnownTraits.Desert
            listOf("c:grain", "c:crops/grain") matches KnownTraits.Grain




            "c:gunpowders" matches KnownTraits.Explosive
            "c:dusts" matches KnownTraits.Powder
            diesAndDied()

        }
    }

    private fun IsScope.diesAndDied() {
        listOf("c:dyes/black", "c:dyed/black") matches GiftTrait(GiftTraitName("Black"))
        listOf("c:dyes/blue", "c:dyed/blue") matches GiftTrait(GiftTraitName("Blue"))
        listOf("c:dyes/brown", "c:dyed/brown") matches GiftTrait(GiftTraitName("Brown"))
        listOf("c:dyes/cyan", "c:dyed/cyan") matches GiftTrait(GiftTraitName("Cyan"))
        listOf("c:dyes/gray", "c:dyed/gray") matches GiftTrait(GiftTraitName("Gray"))
        listOf("c:dyes/green", "c:dyed/green") matches GiftTrait(GiftTraitName("Green"))
        listOf("c:dyes/light_blue", "c:dyed/light_blue") matches GiftTrait(GiftTraitName("LightBlue"))
        listOf("c:dyes/light_gray", "c:dyed/light_gray") matches GiftTrait(GiftTraitName("LightGray"))
        listOf("c:dyes/lime", "c:dyed/lime") matches GiftTrait(GiftTraitName("Lime"))
        listOf("c:dyes/magenta", "c:dyed/magenta") matches GiftTrait(GiftTraitName("Magenta"))
        listOf("c:dyes/orange", "c:dyed/orange") matches GiftTrait(GiftTraitName("Orange"))
        listOf("c:dyes/pink", "c:dyed/pink") matches GiftTrait(GiftTraitName("Pink"))
        listOf("c:dyes/purple", "c:dyed/purple") matches GiftTrait(GiftTraitName("Purple"))
        listOf("c:dyes/red", "c:dyed/red") matches GiftTrait(GiftTraitName("Red"))
        listOf("c:dyes/white", "c:dyed/white") matches GiftTrait(GiftTraitName("White"))
        listOf("c:dyes/yellow", "c:dyed/yellow") matches GiftTrait(GiftTraitName("Yellow"))

    }

    private fun blockTagsToTraits(tag: TagKey<Block>): List<GiftTraitHolder> {
        return commonTags(tag.location.toString()) + emptyList()
    }

    private fun itemTagsToTraits(tag: TagKey<Item>): List<GiftTraitHolder> {
        return commonTags(tag.location.toString()) + emptyList()
    }

    private fun resolveItem(traits: List<GiftTrait>, amount: Int): ItemStack? {
        val closest = matcher.FindClosestAvailableGift(traits)
        println("Closest item found: $closest")
        val itm = closest.getOrNull(0)?.copyWithCount(amount) ?: return null
        val durableTraits = traits.filter {
            destinationTraits.isMember(it)
        }
        if (durableTraits.isNotEmpty() && itm.isDamageableItem) {
            // set the damage value based on the average durability and the quality of the traits
            val averageDurability = durableTraits.map { it.duration }.average().toInt()
            itm.damageValue = (averageDurability * itm.maxDamage).toInt()
        }
        //todo handle containers or enchanting books or potions
        return itm
    }
}


fun potionDurationToTraitDuration(duration: Int): Float {
    return if (duration < 0) -1f else (duration.toFloat() / 20f) / 180f // 3 min is standard potion duration
}

private fun List<GiftTrait>.isMember(trait: GiftTrait): Boolean {
    return this.any { it.name == trait.name }
}

private fun List<GiftTrait>.isMember(trait: GiftTraitHolder): Boolean {
    return this.any { it.name == trait.name }
}


private interface ConvertScope {
    fun GiftTrait.convert(): GiftTraitHolder
    fun List<GiftTrait>.convert(): List<GiftTraitHolder> {
        return this.map { it.convert() }
    }

    operator fun List<GiftTrait>.plus(holder: GiftTraitHolder): List<GiftTraitHolder> {
        val map: List<GiftTraitHolder> = this.map { it.convert() }
        return map + holder
    }

    operator fun List<GiftTrait>.plus(holder: List<GiftTraitHolder>): List<GiftTraitHolder> {
        val map: List<GiftTraitHolder> = this.map { it.convert() }
        return map + holder
    }
}

private inline fun <T> convertScope(source: GiftTraitSource, block: ConvertScope.() -> T): T {
    val obj = object : ConvertScope {
        override fun GiftTrait.convert(): GiftTraitHolder {
            return GiftTraitHolder(
                trait = this, source = source
            )
        }
    }
    return obj.block()
}

private val weaponTraits = listOf(
    KnownTraits.Weapon,
    KnownTraits.MeleeWeapon,
    KnownTraits.RangedWeapon,
)

private val objectWithUsage = listOf(
    KnownTraits.Tool,
    KnownTraits.Weapon,
    KnownTraits.MeleeWeapon,
    KnownTraits.RangedWeapon,
    KnownTraits.Armor,
    ExtraTraits.Saddle,
)

// duration = durability
private val destinationTraits = listOf(
    KnownTraits.Consumable,
    KnownTraits.Tool,
    KnownTraits.Weapon,
    KnownTraits.MeleeWeapon,
    KnownTraits.RangedWeapon,
    KnownTraits.Armor,
    ExtraTraits.Saddle,
    KnownTraits.Throwing
)

private val effectTraits = listOf(
    KnownTraits.Buff,
    KnownTraits.Trap,
    KnownTraits.Heal,
    KnownTraits.Mana,
    KnownTraits.Cure,
    KnownTraits.Slowness,
    KnownTraits.Damage,
    KnownTraits.Fire,
    KnownTraits.Ice,
    ExtraTraits.AttackSpeed,
    ExtraTraits.Suspicious,
    ExtraTraits.Ominous,
    KnownTraits.Fertilizer
)

private val physicalStateTraits = listOf(
    KnownTraits.Goo,
    KnownTraits.Powder,
)

// inherited by crafting recipes
private val craftingInheritedTraits = listOf(
    KnownTraits.Resource,
    KnownTraits.Book,
    KnownTraits.Wood,
    KnownTraits.Stone,
    KnownTraits.Meat,
    KnownTraits.Vegetable,
    KnownTraits.Fruit,
    KnownTraits.Egg,
    KnownTraits.Metal,
    KnownTraits.Mineral,
    KnownTraits.Gem,
    KnownTraits.Crystal,
    KnownTraits.Rock,
    KnownTraits.Paper,
    KnownTraits.Fish,
    KnownTraits.Mushroom,
    KnownTraits.Copper,
    KnownTraits.Coal,
    KnownTraits.Gold,
    KnownTraits.Silver,
    KnownTraits.Platinum,
    KnownTraits.Diamond,
    KnownTraits.Prismarine,
    KnownTraits.Aquamarine,
    KnownTraits.Sand,
    KnownTraits.Bone,
    KnownTraits.Clay,
    KnownTraits.Iron,
    KnownTraits.Ceramic,
    KnownTraits.Teleport,
    KnownTraits.Fire,
    KnownTraits.Ice,
    KnownTraits.Container,
    KnownTraits.LiquidContainer,
    KnownTraits.Energy,
    KnownTraits.Electronics,
    KnownTraits.Leather,
    KnownTraits.AnimalProduct,
    KnownTraits.Explosive,

    )
// inherited by crafting recipes
private val compositionTraits = listOf(
    KnownTraits.Resource,
    KnownTraits.Wood,
    KnownTraits.Stone,
    KnownTraits.Ore,
    KnownTraits.Grass,
    KnownTraits.Meat,
    KnownTraits.Vegetable,
    KnownTraits.Fruit,
    KnownTraits.Egg,
    KnownTraits.Metal,
    KnownTraits.Mineral,
    KnownTraits.Gem,
    KnownTraits.Crystal,
    KnownTraits.Rock,
    KnownTraits.Boulder,
    KnownTraits.Paper,
    KnownTraits.Fish,
    KnownTraits.Fiber,
    KnownTraits.Seed,
    KnownTraits.Mushroom,
    KnownTraits.Bush,
    KnownTraits.Copper,
    KnownTraits.Coal,
    KnownTraits.Gold,
    KnownTraits.Silver,
    KnownTraits.Platinum,
    KnownTraits.Diamond,
    KnownTraits.Prismarine,
    KnownTraits.Aquamarine,
    KnownTraits.Sand,
    KnownTraits.Fertilizer,
    KnownTraits.Grain,
    KnownTraits.Bone,
    KnownTraits.Clay,
    KnownTraits.Iron,
    KnownTraits.Ceramic,
    KnownTraits.Tree,
    KnownTraits.TreeSeed,
    KnownTraits.Mineral,
    KnownTraits.Flower,
    KnownTraits.Teleport,
    KnownTraits.Fire,
    KnownTraits.Ice,
    KnownTraits.Container,
    KnownTraits.LiquidContainer,
    KnownTraits.Energy,
    KnownTraits.Electronics,
    KnownTraits.Salted,
    KnownTraits.Cooking,
    KnownTraits.Leather,
    KnownTraits.AnimalProduct,
    KnownTraits.Explosive,

    )

object ExtraTraits {
    val ArmorToughness: GiftTrait = GiftTrait(GiftTraitName("ArmorToughness"))
    val AttackSpeed = GiftTrait(GiftTraitName("AttackSpeed"))
    val Saddle = GiftTrait(GiftTraitName("Saddle"))
    val Suspicious = GiftTrait(GiftTraitName("Suspicious"))
    val Ominous = GiftTrait(GiftTraitName("Ominous"))
}


object KnownTraits {

    val Speed = GiftTrait(GiftTraitName("Speed"))
    val Consumable = GiftTrait(GiftTraitName("Consumable"))
    val Food = GiftTrait(GiftTraitName("Food"))
    val Drink = GiftTrait(GiftTraitName("Drink"))
    val Heal = GiftTrait(GiftTraitName("Heal"))
    val Mana = GiftTrait(GiftTraitName("Mana"))
    val Key = GiftTrait(GiftTraitName("Key"))
    val Trap = GiftTrait(GiftTraitName("Trap"))
    val Buff = GiftTrait(GiftTraitName("Buff"))
    val Life = GiftTrait(GiftTraitName("Life"))
    val Weapon = GiftTrait(GiftTraitName("Weapon"))
    val Armor = GiftTrait(GiftTraitName("Armor"))
    val Tool = GiftTrait(GiftTraitName("Tool"))
    val Fish = GiftTrait(GiftTraitName("Fish"))
    val Animal = GiftTrait(GiftTraitName("Animal"))
    val Cure = GiftTrait(GiftTraitName("Cure"))
    val Seed = GiftTrait(GiftTraitName("Seed"))
    val Metal = GiftTrait(GiftTraitName("Metal"))
    val Bomb = GiftTrait(GiftTraitName("Bomb"))
    val Monster = GiftTrait(GiftTraitName("Monster"))
    val Resource = GiftTrait(GiftTraitName("Resource"))
    val Material = GiftTrait(GiftTraitName("Material"))
    val Wood = GiftTrait(GiftTraitName("Wood"))
    val Stone = GiftTrait(GiftTraitName("Stone"))
    val Ore = GiftTrait(GiftTraitName("Ore"))
    val Grass = GiftTrait(GiftTraitName("Grass"))
    val Meat = GiftTrait(GiftTraitName("Meat"))
    val Vegetable = GiftTrait(GiftTraitName("Vegetable"))
    val Fruit = GiftTrait(GiftTraitName("Fruit"))
    val Egg = GiftTrait(GiftTraitName("Egg"))
    val Slowness = GiftTrait(GiftTraitName("Slowness"))
    val Damage = GiftTrait(GiftTraitName("Damage"))
    val Fire = GiftTrait(GiftTraitName("Fire"))
    val Ice = GiftTrait(GiftTraitName("Ice"))
    val Currency = GiftTrait(GiftTraitName("Currency"))
    val Energy = GiftTrait(GiftTraitName("Energy"))
    val Light = GiftTrait(GiftTraitName("Light"))

    val Copper = GiftTrait(GiftTraitName("Copper"))
    val Coal = GiftTrait(GiftTraitName("Coal"))
    val Gold = GiftTrait(GiftTraitName("Gold"))
    val Silver = GiftTrait(GiftTraitName("Silver"))
    val Platinum = GiftTrait(GiftTraitName("Platinum"))
    val Diamond = GiftTrait(GiftTraitName("Diamond"))
    val Gem = GiftTrait(GiftTraitName("Gem"))
    val Crystal = GiftTrait(GiftTraitName("Crystal"))
    val Rock = GiftTrait(GiftTraitName("Rock"))
    val Boulder = GiftTrait(GiftTraitName("Boulder"))
    val Book = GiftTrait(GiftTraitName("Book"))
    val Paper = GiftTrait(GiftTraitName("Paper"))
    val Dye = GiftTrait(GiftTraitName("Dye"))
    val Potion = GiftTrait(GiftTraitName("Potion"))
    val MeleeWeapon = GiftTrait(GiftTraitName("MeleeWeapon"))
    val RangedWeapon = GiftTrait(GiftTraitName("RangedWeapon"))
    val Container = GiftTrait(GiftTraitName("Container"))
    val LiquidContainer = GiftTrait(GiftTraitName("LiquidContainer"))
    val Electronics = GiftTrait(GiftTraitName("Electronics"))
    val Furnace = GiftTrait(GiftTraitName("Furnace"))
    val Crafting = GiftTrait(GiftTraitName("Crafting"))
    val Machine = GiftTrait(GiftTraitName("Machine"))
    val Luxury = GiftTrait(GiftTraitName("Luxury"))
    val Statue = GiftTrait(GiftTraitName("Statue"))
    val Goat = GiftTrait(GiftTraitName("Goat"))
    val Clay = GiftTrait(GiftTraitName("Clay"))
    val Cooking = GiftTrait(GiftTraitName("Cooking"))
    val Bush = GiftTrait(GiftTraitName("Bush"))
    val Dry = GiftTrait(GiftTraitName("Dry"))
    val Ocean = GiftTrait(GiftTraitName("Ocean"))
    val Trident = GiftTrait(GiftTraitName("Trident"))
    val Artifact = GiftTrait(GiftTraitName("Artifact"))
    val Ancient = GiftTrait(GiftTraitName("Ancient"))
    val Water = GiftTrait(GiftTraitName("Water"))
    val Aquamarine = GiftTrait(GiftTraitName("Aquamarine"))
    val Prismarine = GiftTrait(GiftTraitName("Prismarine"))
    val Insect = GiftTrait(GiftTraitName("Insect"))
    val Sand = GiftTrait(GiftTraitName("Sand"))
    val Desert = GiftTrait(GiftTraitName("Desert"))
    val Fertilizer = GiftTrait(GiftTraitName("Fertilizer"))
    val Fiber = GiftTrait(GiftTraitName("Fiber"))
    val Explosive = GiftTrait(GiftTraitName("Explosive"))
    val Powder = GiftTrait(GiftTraitName("Powder"))
    val Broken = GiftTrait(GiftTraitName("Broken"))
    val Goo = GiftTrait(GiftTraitName("Goo"))
    val Salted = GiftTrait(GiftTraitName("Salted"))
    val Leather = GiftTrait(GiftTraitName("Leather"))
    val Milk = GiftTrait(GiftTraitName("Milk"))
    val Chicken = GiftTrait(GiftTraitName("Chicken"))
    val Cow = GiftTrait(GiftTraitName("Cow"))
    val Beef = GiftTrait(GiftTraitName("Beef"))
    val Tree = GiftTrait(GiftTraitName("Tree"))
    val TreeSeed = GiftTrait(GiftTraitName("TreeSeed"))
    val Oil = GiftTrait(GiftTraitName("Oil"))
    val Juice = GiftTrait(GiftTraitName("Juice"))
    val AnimalProduct = GiftTrait(GiftTraitName("AnimalProduct"))
    val Beach = GiftTrait(GiftTraitName("Beach"))
    val Grain = GiftTrait(GiftTraitName("Grain"))
    val Mushroom = GiftTrait(GiftTraitName("Mushroom"))
    val Bone = GiftTrait(GiftTraitName("Bone"))
    val Iron = GiftTrait(GiftTraitName("Iron"))
    val Unluck = GiftTrait(GiftTraitName("Unluck"))
    val Luck = GiftTrait(GiftTraitName("Luck"))
    val Vine = GiftTrait(GiftTraitName("Vine"))
    val Ceramic = GiftTrait(GiftTraitName("Ceramic"))
    val Berry = GiftTrait(GiftTraitName("Berry"))
    val Coffee = GiftTrait(GiftTraitName("Coffee"))
    val Flower = GiftTrait(GiftTraitName("Flower"))
    val Lumber = GiftTrait(GiftTraitName("Lumber"))
    val Mineral = GiftTrait(GiftTraitName("Mineral"))
    val Poison = GiftTrait(GiftTraitName("Poison"))
    val Flight = GiftTrait(GiftTraitName("Flight"))
    val Head = GiftTrait(GiftTraitName("Head"))
    val Invincible = GiftTrait(GiftTraitName("Invincible"))
    val Random = GiftTrait(GiftTraitName("Random"))
    val Legendary = GiftTrait(GiftTraitName("Legendary"))
    val Instrument = GiftTrait(GiftTraitName("Instrument"))
    val Firework = GiftTrait(GiftTraitName("Firework"))
    val Rocket = GiftTrait(GiftTraitName("Rocket"))
    val Fuel = GiftTrait(GiftTraitName("Fuel"))
    val Throwing = GiftTrait(GiftTraitName("Throwing"))
    val Scroll = GiftTrait(GiftTraitName("Scroll"))
    val IQ = GiftTrait(GiftTraitName("IQ"))
    val Fossil = GiftTrait(GiftTraitName("Fossil"))
    val Teleport = GiftTrait(GiftTraitName("Teleport"))


}

object SpecificTraits {
    val Nether = KnownTraits.Fire.copy(quality = 0.3f)
}
