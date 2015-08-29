package sleepless.gather.sources.vk_http

import java.time.ZonedDateTime

import akka.actor._
import net.ruippeixotog.scalascraper.dsl.DSL.Extract._
import net.ruippeixotog.scalascraper.dsl.DSL._
import sleepless.gather.sources.vk_http.VkHttpWatcher.Commands
import sleepless.gather.{BooleanData, SensorProbe}

import scala.util._


class VkHttpWatcher(id: VkUserId) extends Actor with ActorLogging with HtmlScrapping {

  import context.dispatcher

  override def receive: Actor.Receive = {
    case Commands.UpdateData =>
        val webPage = getPage(s"https://m.vk.com/${id.id}")
      webPage andThen {
        case Success(resp) =>
          // <div class="pp_last_activity">заходил сегодня в 12:58</div>
          val lastActivityString = (browser.parseString(resp.entity.data.asString) >> text(".pp_last_activity"))
          log.info(s"Got status from $id: $lastActivityString")

          val probe = SensorProbe("VkHttp", id.id, ZonedDateTime.now(), BooleanData(VkHttpWatcher.parseLastActivity(lastActivityString)))
          context.system.eventStream.publish(probe)

        case Failure(err) =>
          log.warning(s"Failure during downloading user info, ${err.getMessage}", err)
      }

  }

}

object VkHttpWatcher {

  def props(id: VkUserId) = Props(classOf[VkHttpWatcher], id)

  object Commands {
    object UpdateData
  }

  private def parseLastActivity(activityString: String): Boolean = {
    !(activityString.contains("был") || activityString.contains("заходил"))
  }
}