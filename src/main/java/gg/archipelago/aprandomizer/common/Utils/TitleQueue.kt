package gg.archipelago.aprandomizer.common.Utils

import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.tick.ServerTickEvent
import java.util.*

@EventBusSubscriber
object TitleQueue {
    val titleQueue: MutableList<QueuedTitle> = LinkedList<QueuedTitle>()

    var titleTime: Int = 0

    @SubscribeEvent
    fun serverTick(tick: ServerTickEvent.Post?) {
        if (!titleQueue.isEmpty()) {
            if (titleTime <= 0) {
                val title: QueuedTitle = titleQueue.first()
                titleQueue.removeFirst()
                titleTime = title.getTicks()
                title.sendTitle()
            }
        }
        if (titleTime > 0) {
            titleTime -= 1
        }
    }

    @JvmStatic
    fun queueTitle(queuedTitle: QueuedTitle?) {
        titleQueue.add(queuedTitle!!)
    }

    fun clearTitleQueue() {
        titleQueue.clear()
    }
}
