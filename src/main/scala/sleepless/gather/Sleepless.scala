package sleepless.gather

import java.util.UUID

import akka.actor.{ActorSystem, Props, Actor}
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.stream.scaladsl.{Sink, Source}
import sleepless.gather.sources.vk_api.{VkApiResponse, Users, VkApiRequest, VkApiPipeline}
import sleepless.gather.sources.vk_http.{VkUserId, VkHttpSupervisor}
import spray.json.JsString
import concurrent.duration._

object Sleepless extends App {
    implicit val system = ActorSystem("system")
    implicit val materializer = ActorMaterializer()
    import system.dispatcher

    val users = Seq("dm", "kate_clapp", "daniilova_anya", "adam_moran").map(VkUserId)

    val vkSupervisor = system.actorOf(VkHttpSupervisor.props)

    val csvWriter = system.actorOf(CsvWriter.props)

    val consoleWriter = system.actorOf(ConsoleWriter.props)

    val accessTokens = Set("")

    val eventStreamSource = Source.actorRef(1000, OverflowStrategy.dropNew)

    val eventStreamSink = Sink.foreach(system.eventStream.publish)

    val actor = VkApiPipeline.createNew(accessTokens, eventStreamSource, eventStreamSink, eventStreamSink).run()

    system.eventStream.subscribe(actor, classOf[VkApiRequest])

    users.foreach(vkSupervisor ! VkHttpSupervisor.Commands.AddNewUser(_))

    system.scheduler.scheduleOnce(5.seconds) {
        Seq(1, 2, 5, 6, 7)
            .map(id => VkApiRequest(method = Users.Get(userId = id, fields = Set("last_seen"))))
            .foreach(system.eventStream.publish)
    }

    system.scheduler.scheduleOnce(15.seconds) {
        Seq(8, 9, 10, 11, 12)
            .map(id => VkApiRequest(method = Users.Get(userId = id, fields = Set("last_seen"))))
            .foreach(system.eventStream.publish)
    }
}
