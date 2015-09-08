package sleepless.gather.sources.vk_api

import java.util.UUID

import sleepless.gather.models.vk.VkObject
import sleepless.gather.sources.vk_api.ApiHelper.BatchId

case class VkApiResponse(id: Either[UUID, BatchId], result: VkObject)
