package sleepless.gather.sources.vk_api

import java.util.UUID

import sleepless.gather.sources.vk_api.ApiHelper.BatchId
import spray.json.JsValue

case class VkApiResponse(id: Either[UUID, BatchId], result: JsValue)
