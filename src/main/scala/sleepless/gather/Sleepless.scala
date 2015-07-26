package sleepless.gather

import akka.actor.{Props, Actor}
import sleepless.gather.sources.{UpdateData, SensorProbe, VkAccountId, VkHttpWatcher}


class SleeplessSupervisor extends Actor {
  override def preStart() = {
    val simple = context.actorOf(Props(classOf[VkHttpWatcher], VkAccountId("dm")))
    simple ! UpdateData
  }
  override def receive: Receive = {
    case data: SensorProbe => {
      println(s"Answer is ${data}")
      println(s"Stopping")
      context.stop(self)
    }
  }
}

object Sleepless extends App {
    System.out.println("Hello, Sleepless one!")
    akka.Main.main(Array(classOf[SleeplessSupervisor].getName))
}
