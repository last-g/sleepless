package sleepless.gather

import akka.actor.{Props, Actor}
import sleepless.gather.sources.vk_api.VkApiResponse


class ConsoleWriter extends Actor {

  context.system.eventStream.subscribe(self, classOf[VkApiResponse])

  override def receive: Receive = {
    case VkApiResponse(uuid, json) => println(json)
  }

}

object ConsoleWriter {
  def props = Props[ConsoleWriter]
}