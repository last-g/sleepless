package sleepless.gather

import java.io.File

import akka.actor._
import com.github.tototoshi.csv._

class CsvWriter extends Actor {

  context.system.eventStream.subscribe(self, classOf[SensorProbe])

  val f = new File("out.csv")


  override def receive: Receive = {
    case sp: SensorProbe =>
      val writer = CSVWriter.open(f, append = true)
      writer.writeRow(Seq(sp.sensorType, sp.sensorId, sp.timestamp, sp.data))
      writer.close()
  }

}

object CsvWriter {
  def props = Props[CsvWriter]
}
