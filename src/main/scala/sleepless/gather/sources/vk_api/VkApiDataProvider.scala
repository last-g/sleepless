package sleepless.gather.sources.vk_api

import java.time.{ZoneId, ZonedDateTime, ZoneOffset, LocalDateTime}

import akka.actor.ActorSystem
import akka.http.scaladsl._
import akka.http.scaladsl.model.{HttpResponse, HttpRequest}
import akka.stream._
import akka.stream.scaladsl._
import akka.http.scaladsl.unmarshalling.Unmarshal
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success, Try}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import spray.json._
import scala.concurrent.duration._

object VkApiDataProvider extends App {

    val access_token = ""


    implicit val system = ActorSystem("sleepless")
    implicit val ec = system.dispatcher
    implicit val materializer = ActorMaterializer()

    case class VkUserId(id: Long)

    case class LastSeen(time: Long,
                        platform: Option[Int])

    sealed trait User {
        def id: Long
    }

    case class ActiveUser(id: Long,
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

    val vkApiRate = 333.millis

    def throttle[T](rate: FiniteDuration): Flow[T, T, Unit] = {
        Flow() { implicit builder =>
            import akka.stream.scaladsl.FlowGraph.Implicits._
            val zip = builder.add(Zip[T, Unit.type]())
            Source(rate, rate, Unit) ~> zip.in1
            (zip.in0, zip.out)
        }.map(_._1)
    }


    val httpRequestSender = Http().superPool[Unit]()

    val vkIdToHttpRequest: Flow[Seq[VkUserId], (HttpRequest, Unit), Unit] = Flow[Seq[VkUserId]].map {
        case l: Seq[VkUserId] =>
            val idString = l.map(_.id).mkString(",")
            val uri = s"https://api.vk.com/method/users.get?user_ids=$idString&fields=last_seen&v=5.37&access_token=$access_token"
            HttpRequest(uri = uri) -> Unit
    }

    import MyJsonProtocol._

    val responseToTimestamp: Flow[(Try[HttpResponse], Unit), List[(VkUserId, Option[Long])], Unit] = Flow[(Try[HttpResponse], Unit)].mapAsync(4) {
        case (Success(resp), _) =>
            //{resp.entity.toStrict(5 seconds).map(d => println(d.data.decodeString("UTF-8")))}
            Unmarshal(resp.entity).to[R00tJsonObject].map {
                json =>
                val users = json.response
                users.map {
                case au: ActiveUser => VkUserId(au.id) -> Some(au.last_seen.time)
                case du: DeactivatedUser => VkUserId(du.id) -> None
            }}
        case (Failure(e), _) =>
            println(e.getMessage)
            throw e
    }

    val userIdsToLastSeen: Flow[VkUserId, (VkUserId, Option[ZonedDateTime]), Unit] =
        Flow[VkUserId].groupedWithin(500, vkApiRate)
            .via(vkIdToHttpRequest)
            .via(throttle(vkApiRate))
            .via(httpRequestSender)
            .via(responseToTimestamp)
            .mapConcat(batch => batch.map {
            case (userId, Some(timestamp)) =>
                userId -> Some(ZonedDateTime.ofLocal(LocalDateTime.ofEpochSecond(timestamp, 0, ZoneOffset.UTC), ZoneId.of("UTC"), null))
            case noTimestamp @ (userId, None) =>
                userId -> None
        })

    Source(1 to 3000).map(id => VkUserId(id))
        .via(userIdsToLastSeen)
        .runForeach { case (VkUserId(userId), timestamp) => println(s"userId: $userId, timeStamp: $timestamp") }


}
