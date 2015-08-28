package sleepless.gather.sources.vk_http

import java.time.ZonedDateTime

import akka.actor._
import net.ruippeixotog.scalascraper.browser.Browser
import net.ruippeixotog.scalascraper.dsl.DSL.Extract._
import net.ruippeixotog.scalascraper.dsl.DSL._
import sleepless.gather.{BooleanData, SensorProbe}
import spray.client.pipelining._
import spray.http._

import scala.concurrent.Future
import scala.util.{Failure, Success}

final case class VkAccountId(id:String)

case object UpdateData

class VkHttpWatcher(id: VkAccountId) extends Actor with ActorLogging{
  implicit val execContext = context.system.dispatcher
  val pipeline: HttpRequest => Future[HttpResponse] = sendReceive
  val browser = new Browser()

  override def receive: Actor.Receive = {
    case UpdateData => {

      val webPage = pipeline(Get(s"https://m.vk.com/${id.id}"))
      val snd = sender()

      webPage.andThen({

        case Success(resp) => {
          // <div class="pp_last_activity">заходил сегодня в 12:58</div>

          val lastActivityString = (browser.parseString(resp.entity.data.asString) >> text(".pp_last_activity"))

          log.info(s"Got status from $id: $lastActivityString")

          val probe = SensorProbe("VkHttp", id.id, ZonedDateTime.now(), BooleanData(VkHttpWatcher.parseLastActivity(lastActivityString)))

          context.system.eventStream.publish(probe)
        }
        case Failure(err) => println(s"Some failure $err")
      })
    }
  }

}

object VkHttpWatcher
{
  private def parseLastActivity(activityString: String): Boolean = {
    !(activityString.contains("был") || activityString.contains("заходил"))
  }
}