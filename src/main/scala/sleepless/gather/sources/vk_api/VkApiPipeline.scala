package sleepless.gather.sources.vk_api

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpResponse, HttpRequest}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import akka.stream.scaladsl._
import sleepless.gather.sources.vk_api.ApiHelper._
import spray.json._
import scala.concurrent.duration._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import scala.concurrent.duration.FiniteDuration
import scala.util.{Success, Try}


object VkApiPipeline {

    private val httpPool = Http().superPool[ResponseId]()

    def createNew[In](accessTokens: Set[String],
              source: Source[VkApiRequest, In],
              sink: Sink[VkApiResponse, Any],
              onRequestsBatched: Sink[RequestsBatched, Any])
             (implicit system: ActorSystem): RunnableGraph[In] = {

        implicit val ec = system.dispatcher
        implicit val materializer = ActorMaterializer()

        val vkApiRate = VkApiConstants.ApiRate

        val gatheringRate = VkApiConstants.GatheringRate

        val vkApiBatchtLimit = VkApiConstants.VkApiBatchSize

        val vkApiRequestToVkResult: Flow[VkApiRequest, VkApiResponse, Unit] = {
            def vkUri(methodName: String, params: String) = s"https://api.vk.com/method/$methodName?$params"

            def throttle[T](rate: FiniteDuration): Flow[T, T, Unit] = {
                Flow() { implicit builder =>
                    import akka.stream.scaladsl.FlowGraph.Implicits._
                    val zip = builder.add(Zip[T, Unit.type]())
                    Source(rate, rate, Unit) ~> zip.in1
                    (zip.in0, zip.out)
                }.map(_._1)
            }

            val singleVkRequestToHttpRequest: Flow[VkApiRequest, (HttpRequest, ResponseId), Unit] =
                Flow[VkApiRequest].map { request =>
                    val methodName = request.method.name
                    val params = request.method.params.map {
                        case (paramName, paramValue) => s"$paramName=$paramValue"
                    }.mkString("&")

                    HttpRequest(uri = vkUri(methodName, params)) -> Left(request.uuid)
                }

            val batchVkRequestToHttpRequest: Flow[(Batch, BatchId), (HttpRequest, ResponseId), Unit] =
                Flow[(Batch, BatchId)].map {
                    case (requests, batchId) =>
                        val methodName = requests.head.method.name
                        val params = requests.flatMap(_.method.params.toSeq).groupBy(_._1).map {
                            case (paramName, valuesSeq) =>
                                val values = valuesSeq.map(_._2).distinct.mkString(",")
                                s"$paramName=$values"
                        }.mkString("&")

                        HttpRequest(uri = vkUri(methodName, params)) -> Right(batchId)
                }


            val httpResponseToJson: Flow[(Try[HttpResponse], ResponseId), VkApiResponse, Unit] =
                Flow[(Try[HttpResponse], ResponseId)].mapAsync(4) {
                    case (Success(resp), respId) =>
                        Unmarshal(resp.entity).to[JsValue].map(json => VkApiResponse(respId, json))
                }

            def requestBalancer(accessTokens: Seq[String]): Flow[(HttpRequest, ResponseId), (Try[HttpResponse], ResponseId), Unit] = {
                import FlowGraph.Implicits._

                val workerCount = accessTokens.length

                Flow() { implicit b =>
                    val balancer = b.add(Balance[(HttpRequest, ResponseId)](workerCount))
                    val merge = b.add(Merge[(Try[HttpResponse], ResponseId)](workerCount))

                    for (i <- 0 until workerCount) {
                        val token = accessTokens(i)
                        balancer ~>
                            Flow[(HttpRequest, ResponseId)].map {
                                case (req, uuid) => req.copy(uri = req.uri + s"&v=5.37&access_token=$token") -> uuid
                            }.via(throttle(vkApiRate)) ~>
                            httpPool ~>
                            merge
                    }

                    (balancer.in, merge.out)
                }
            }


            val batchAdvertiser: Flow[(Batch, BatchId), (Batch, BatchId), Unit] =
                Flow() { implicit b =>
                    import FlowGraph.Implicits._

                    val broadcast = b.add(Broadcast[(Batch, BatchId)](2))

                    broadcast.out(0).map {
                        case (batch, id) => RequestsBatched(id, batch.map(_.uuid).toSet)
                    } ~> onRequestsBatched

                    (broadcast.in, broadcast.out(1))
                }


            val requestBatchCreator: Flow[VkApiRequest, (Batch, BatchId), Unit] =
                Flow[VkApiRequest]
                    .groupBy(_.method.name)
                    .map { case (methodName, requests) => requests.groupedWithin(vkApiBatchtLimit, gatheringRate).map(reqs => reqs -> BatchId()) }
                    .flatten(FlattenStrategy.concat)
                    .via(batchAdvertiser)

            val vkRequestToHttpRequest: Flow[VkApiRequest, (HttpRequest, ResponseId), Unit] =
                Flow() { implicit b =>
                    import FlowGraph.Implicits._

                    val broadcast = b.add(Broadcast[VkApiRequest](2))
                    val merge = b.add(Merge[(HttpRequest, ResponseId)](2))

                    def isGroupable(req: VkApiRequest) = req.method match {
                        case method: VkApiMethod with Groupable => method.canBeGrouped
                        case _ => false
                    }

                    val groupableFilter = Flow[VkApiRequest].filter(r => isGroupable(r))
                    val singleFilter = Flow[VkApiRequest].filter(r => !isGroupable(r))

                    broadcast.out(0) ~> groupableFilter ~> requestBatchCreator ~> batchVkRequestToHttpRequest ~> merge.in(0)
                    broadcast.out(1) ~> singleFilter ~> singleVkRequestToHttpRequest ~> merge.in(1)

                    (broadcast.in, merge.out)
                }

            Flow[VkApiRequest]
                .via(vkRequestToHttpRequest)
                .via(requestBalancer(accessTokens.toSeq))
                .via(httpResponseToJson)
        }

        source.via(vkApiRequestToVkResult).to(sink)
    }
}