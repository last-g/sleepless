package sleepless.gather.sources.vk_http

import akka.actor.Actor
import net.ruippeixotog.scalascraper.browser.Browser
import spray.client.pipelining._
import spray.http.{HttpResponse, HttpRequest}

import scala.concurrent.Future


trait HtmlScrapping {
  self: Actor =>

  import context.dispatcher
  private val pipeline: HttpRequest => Future[HttpResponse] = sendReceive
  val browser = new Browser()

  def getPage(url: String) = pipeline(Get(url))
}
