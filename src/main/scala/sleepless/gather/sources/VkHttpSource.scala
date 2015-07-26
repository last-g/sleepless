package sleepless.gather.sources

import java.time.ZonedDateTime

import akka.actor._

import scala.concurrent.Future
import scala.util.{Success, Failure}

import spray.http._
import spray.client.pipelining._

import net.ruippeixotog.scalascraper.browser.Browser
import net.ruippeixotog.scalascraper.dsl.DSL._
import net.ruippeixotog.scalascraper.dsl.DSL.Extract._
import net.ruippeixotog.scalascraper.dsl.DSL.Parse._



case class SensorProbe(sensorType:String, sensorId:String, timestamp: ZonedDateTime, data: SensorData)

sealed trait SensorData
case class StringData(data:String) extends SensorData
case class DateTimeData(data:ZonedDateTime) extends SensorData
case class IntegerData(data:Long) extends SensorData

final case class VkAccountId(id:String)

case object UpdateData

class VkHttpSource extends Actor {
  override def receive: Receive = {
    case _ => {}
  }
}

class VkHttpWatcher(id: VkAccountId) extends Actor {
  implicit val execContext = context.system.dispatcher
  val pipeline: HttpRequest => Future[HttpResponse] = sendReceive
  val browser = new Browser()

  override def receive: Actor.Receive = {
    case UpdateData => {

      val webPage = pipeline(Get(s"https://m.vk.com/${id.id}"))
      webPage.andThen({

        case Success(resp) => {
          // <div class="pp_last_activity">заходил сегодня в 12:58</div>

          val lastActivityString = (browser.parseString(resp.entity.data.asString) >> text(".pp_last_activity"))
          sender() ! SensorProbe("VkHttp", id.id, ZonedDateTime.now(), StringData(lastActivityString))
        }
        case Failure(err) => println(s"Some failure $err")
      })
    }
  }
}