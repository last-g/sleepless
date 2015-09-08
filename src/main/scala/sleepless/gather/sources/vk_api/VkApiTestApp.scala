package sleepless.gather.sources.vk_api


import java.util.UUID

import akka.actor.ActorSystem
import akka.http.scaladsl._
import akka.http.scaladsl.model.{HttpResponse, HttpRequest}
import akka.stream._
import akka.stream.scaladsl._
import akka.http.scaladsl.unmarshalling.Unmarshal
import sleepless.gather.models.vk.{JustString, VkUserId}
import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success, Try}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import spray.json._
import scala.concurrent.duration._
import ApiHelper._

object VkApiTestApp extends App {

    val accessTokens = Seq() //TODO enter your access token


    implicit val system = ActorSystem("sleepless")
    implicit val ec = system.dispatcher
    implicit val materializer = ActorMaterializer()


    case class LastSeen(time: Long, platform: Option[Int])

    sealed trait User {
        def id: Long
    }


/*    case class ActiveUser(id: Long,
                          first_name: String,
                          last_name: String,
                          last_seen: LastSeen)
        extends User

    case class DeactivatedUser(id: Long) extends User

    case class R00tJsonObject(response: List[User])

    object MyJsonProtocol extends DefaultJsonProtocol {

        implicit object DeactivatedUserReader extends RootJsonFormat[User] {
            override def write(obj: User): JsValue = throw new UnsupportedOperationException

            def read(value: JsValue) = value match {
                case obj: JsObject if (obj.fields.get("deactivated").isDefined) => value.convertTo[DeactivatedUser]
                case obj: JsObject => value.convertTo[ActiveUser]
            }
        }

        implicit val LastSeenFormat = jsonFormat2(LastSeen)
        implicit val deactivatedUserFormat = jsonFormat1(DeactivatedUser)
        implicit val activeUserFormat = jsonFormat4(ActiveUser)
        implicit val R00tJsonObjectFormat = jsonFormat1(R00tJsonObject)
    }

    import MyJsonProtocol._*/


    val vkApiRate = 500.millis

    val gatheringRate = vkApiRate

    val vkApiBatchtLimit = 100


    def vkUri(methodName: String, params: String) = s"https://api.vk.com/method/$methodName?$params"

    def throttle[T](rate: FiniteDuration): Flow[T, T, Unit] = {
        Flow() { implicit builder =>
            import akka.stream.scaladsl.FlowGraph.Implicits._
            val zip = builder.add(Zip[T, Unit.type]())
            Source(rate, rate, Unit) ~> zip.in1
            (zip.in0, zip.out)
        }.map(_._1)
    }

    val httpPool = Http().superPool[ResponseId]()

    val singleVkRequestToHttpRequest: Flow[VkApiRequest, (HttpRequest, ResponseId), Unit] = Flow[VkApiRequest].map { request =>
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
                val params = requests.flatMap(_.method.params.toSeq).groupBy(_._1).map { case (paramName, valuesSeq) =>
                    val values = valuesSeq.map(_._2).distinct.mkString(",")
                    s"$paramName=$values"
                }.mkString("&")

                HttpRequest(uri = vkUri(methodName, params)) -> Right(batchId)
        }


    val responseToTimestamp: Flow[(Try[HttpResponse], ResponseId), VkApiResponse, Unit] = Flow[(Try[HttpResponse], ResponseId)].mapAsync(4) {
        case (Success(resp), uuid) =>
            //{resp.entity.toStrict(5 seconds).map(d => println(d.data.decodeString("UTF-8")))}
            Unmarshal(resp.entity).to[String].map { str =>
                VkApiResponse(uuid, JustString(str))
            } /*.map {
                json =>
                    val users = json.response
                    users.map {
                        case au: ActiveUser => VkUserId(au.id) -> Some(au.last_seen.time)
                        case du: DeactivatedUser => VkUserId(du.id) -> None
                    }
                    ???
            }*/
        case (Failure(e), _) =>
            println(e.getMessage)
            throw e
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

    val exit = Sink.foreach(println)

    def batchAdvertiser(sink: Sink[RequestsBatched, Any]): Flow[(Batch, BatchId), (Batch, BatchId), Unit] =
        Flow() { implicit b =>
            import FlowGraph.Implicits._

            val broadcast = b.add(Broadcast[(Batch, BatchId)](2))

            broadcast.out(0).map { case (batch, id) => RequestsBatched(id, batch.map(_.uuid).toSet) } ~> sink

            (broadcast.in, broadcast.out(1))
        }

    val requestBatchCreator: Flow[VkApiRequest, (Batch, BatchId), Unit] =
        Flow[VkApiRequest]
            .groupBy(_.method.name)
            .map { case (methodName, requests) => requests.grouped(vkApiBatchtLimit).map(reqs => reqs -> BatchId())}
            .flatten(FlattenStrategy.concat)
            .via(batchAdvertiser(exit))

    val vkRequestToHttpRequest: Flow[VkApiRequest, (HttpRequest, ResponseId), Unit] =
        Flow() { implicit b =>
            import FlowGraph.Implicits._

            val broadcast = b.add(Broadcast[VkApiRequest](2))
            val merge     = b.add(Merge[(HttpRequest, ResponseId)](2))

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

    val vkApiRequestToVkResult: Flow[VkApiRequest, VkApiResponse, Unit] =
        Flow[VkApiRequest]
            .via(vkRequestToHttpRequest)
            .via(requestBalancer(accessTokens))
            .via(responseToTimestamp)


    Source(1 to 3000).map(id => VkApiRequest(method = Users.Get(userId = id, fields = Set("last_seen"), canBeGrouped = id % 10 != 0)))
        .via(vkApiRequestToVkResult)
        .runForeach {
        println
    }


}
