package sleepless.gather.sources.vk_http

import akka.actor.{Props, Actor}
import akka.actor.Actor.Receive
import scala.collection.mutable

class VkHttpSupervisor extends Actor {
  val watchers = mutable.Map[String, VkHttpWatcher]()

  override def receive: Receive = {
    case userId:String => {
      if (!watchers.contains(userId)) {
        val newWatcher = context.actorOf(Props(classOf[VkHttpWatcher], VkAccountId(userId)))
        watchers + userId -> newWatcher
      }
    }
  }
}
