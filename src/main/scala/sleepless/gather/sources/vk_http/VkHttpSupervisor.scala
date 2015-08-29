package sleepless.gather.sources.vk_http

import akka.actor.{ActorRef, Props, Actor}
import scala.concurrent.duration._
import scala.collection.mutable
import sleepless.gather.Constants._

class VkHttpSupervisor extends Actor {

  import VkHttpSupervisor._
  import context.dispatcher

  val watchers = mutable.Map.empty[VkUserId, ActorRef]

  context.system.scheduler.schedule(WatcherRequestTime, WatcherRequestTime) {
    watchers.values.foreach(_ ! VkHttpWatcher.Commands.UpdateData)
  }

  override def receive: Receive = {
    case Commands.AddNewUser(userId) =>
      watchers.getOrElseUpdate(userId, context.actorOf(VkHttpWatcher.props(userId)))
  }
}


object VkHttpSupervisor {

  def props = Props[VkHttpSupervisor]

  object Commands {

    case class AddNewUser(vkUserId: VkUserId)

  }

}