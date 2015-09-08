package sleepless.gather.sources.vk_api

import java.util.UUID


case class VkApiRequest(uuid: UUID = UUID.randomUUID(), method: VkApiMethod)
