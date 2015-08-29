package sleepless.gather

import akka.actor.{ActorSystem, Props, Actor}
import sleepless.gather.sources.vk_http.{VkUserId, VkHttpSupervisor}
import concurrent.duration._

object Sleepless extends App {
  val system = ActorSystem("system")

  val users = Seq("dm", "kate_clapp", "daniilova_anya", "adam_moran").map(VkUserId)

  val vkSupervisor = system.actorOf(VkHttpSupervisor.props)

  val csvWriter = system.actorOf(CsvWriter.props)

  users.foreach(vkSupervisor ! VkHttpSupervisor.Commands.AddNewUser(_))

}
