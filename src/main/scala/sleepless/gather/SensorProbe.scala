package sleepless.gather

import java.time.ZonedDateTime

case class SensorProbe(sensorType:String, sensorId:String, timestamp: ZonedDateTime, data: SensorData)
