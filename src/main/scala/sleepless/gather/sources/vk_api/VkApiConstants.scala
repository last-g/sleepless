package sleepless.gather.sources.vk_api

import java.util.concurrent.TimeUnit
import concurrent.duration._
import com.typesafe.config.ConfigFactory


object VkApiConstants {

    private val config = ConfigFactory.load()

    val ApiRate = config.getDuration("gather.vkApi.requestRate", TimeUnit.MILLISECONDS).millis

    val GatheringRate = config.getDuration("gather.vkApi.gatheringRate", TimeUnit.MILLISECONDS).millis

    val VkApiBatchSize = config.getInt("gather.vkApi.batchSize")
}
