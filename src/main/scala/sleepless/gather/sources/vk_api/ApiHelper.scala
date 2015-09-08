package sleepless.gather.sources.vk_api

import java.util.UUID

object ApiHelper {

    case class BatchId(id: UUID = UUID.randomUUID())

    case class RequestsBatched(batchId: BatchId, requestsIds: Set[UUID])

    type ResponseId = Either[UUID, BatchId]

    type Batch = Seq[VkApiRequest]
}
