package sleepless.gather

import akka.actor.{ActorSystem, Props, Actor}
import sleepless.gather.sources.vk_http.{VkHttpWatcher, UpdateData, VkAccountId}
import concurrent.duration._

class SleeplessSupervisor extends Actor {

  import context.dispatcher

  override def preStart() = {
    val simple = context.actorOf(Props(classOf[VkHttpWatcher], VkAccountId("dm")))
    context.system.scheduler.schedule(0.seconds, 5.seconds, simple, UpdateData)
  }

  override def receive: Receive = {
    case data: SensorProbe => {
      println(s"Answer is ${data}")
    }
  }
}

object Sleepless extends App {
    System.out.println("Hello, Sleepless one!")
    val system = ActorSystem("system")
    system.actorOf(Props[SleeplessSupervisor])
    system.actorOf(Props[CsvWriter])
}
