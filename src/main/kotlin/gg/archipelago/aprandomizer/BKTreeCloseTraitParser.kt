package gg.archipelago.aprandomizer

import gg.archipelago.gifting.api.GiftTrait
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.MutableList
import kotlin.collections.MutableMap
import kotlin.math.abs
import kotlin.math.min

class BKTreeCloseTraitParser<T>(distanceDelegate: DistanceDelegate? = null) {
    private val _items: MutableList<T>
    private val _traits: MutableMap<String, Pair<Double, Double>>
    private val _children: MutableMap<Double, BKTreeCloseTraitParser<T>>

    fun interface DistanceDelegate {
        fun invoke(
            giftTraits: List<GiftTrait>,
            traits: MutableMap<String, Pair<Double, Double>>,
            isCompatible: BooleanWrapper
        ): Double
    }

    private val _distance: DistanceDelegate

    init {
        _items = mutableListOf()
        _traits = HashMap<String, Pair<Double, Double>>()
        _children = HashMap<Double, BKTreeCloseTraitParser<T>>()
        _distance =
            distanceDelegate
                ?: DistanceDelegate { giftTraits: List<GiftTrait>, traits: MutableMap<String, Pair<Double, Double>>, isCompatible: BooleanWrapper ->
                    Companion.DefaultDistance(
                        giftTraits, traits, isCompatible
                    )
                }
    }

    fun RegisterAvailableGift(availableGift: T, traits: List<GiftTrait>) {
        if (_items.isEmpty()) {
            _items.add(availableGift)
            for (giftTrait in traits) {
                _traits.merge(
                    giftTrait.name.name,
                    Pair(
                        giftTrait.quality.toDouble(),
                        giftTrait.duration.toDouble()
                    )
                ) { oldValue: Pair<Double, Double>, newValue: Pair<Double, Double> ->
                    Pair(
                        oldValue!!.first!! + newValue!!.first!!,
                        oldValue.second!! + newValue.second!!
                    )
                }
            }

            return
        }

        val isCompatible = BooleanWrapper()
        val distance = _distance.invoke(traits, _traits, isCompatible)
        if (distance == 0.0) {
            _items.add(availableGift)
            return
        }

        var child = _children.get(distance)
        if (child == null) {
            child = BKTreeCloseTraitParser(_distance)
            _children.put(distance, child)
        }

        child.RegisterAvailableGift(availableGift, traits)
    }

    private fun FindClosestAvailableGift(
        giftTraits: List<GiftTrait>,
        bestDistance: DoubleWrapper,
        closestItems: MutableList<T>
    ) {
        val isCompatible = BooleanWrapper()
        val distance = _distance.invoke(giftTraits, _traits, isCompatible)
        if (isCompatible.value) {
            if (abs(distance - bestDistance.value) < 0.0001) {
                closestItems.addAll(_items)
            } else if (distance < bestDistance.value) {
                closestItems.clear()
                closestItems.addAll(_items)
                bestDistance.value = distance
            }
        }

        for (keyValuePair in _children.entries) {
            if (distance - keyValuePair.key!! < bestDistance.value + 0.0001) {
                keyValuePair.value!!.FindClosestAvailableGift(giftTraits, bestDistance, closestItems)
            }
        }
    }

    fun FindClosestAvailableGift(giftTraits: List<GiftTrait>): MutableList<T> {
        val closestItems: MutableList<T> = mutableListOf()
        val bestDistance = DoubleWrapper(Double.Companion.MAX_VALUE)
        FindClosestAvailableGift(giftTraits, bestDistance, closestItems)
        return closestItems
    }


    class BooleanWrapper {
        var value: Boolean = false
    }

    class DoubleWrapper(var value: Double)

    companion object {
        private fun DefaultDistance(
            giftTraits: List<GiftTrait>, traits: MutableMap<String, Pair<Double, Double>>,
            isCompatible: BooleanWrapper
        ): Double {
            val traitsCopy: MutableMap<String?, Pair<Double?, Double?>> = traits.toMutableMap()
            var distance = 0.0
            for (giftTrait in giftTraits) {
                if (traitsCopy.containsKey(giftTrait.name.name)) {
                    val values: Pair<Double?, Double?> = traitsCopy.remove(
                        giftTrait.name.name
                    )!!
                    if (values.first!! * giftTrait.quality <= 0) {
                        distance += 1.0
                    } else {
                        val d = values.first!! / giftTrait.quality
                        distance += 1 - min(1 / d, d)
                    }

                    if (values.second!! * giftTrait.duration <= 0) {
                        distance += 1.0
                    } else {
                        val d = values.second!! / giftTrait.duration
                        distance += 1 - min(1 / d, d)
                    }
                } else {
                    distance += 1.0
                }
            }

            distance += traitsCopy.size.toDouble()
            isCompatible.value = traitsCopy.size != traits.size
            return distance
        }
    }
}
