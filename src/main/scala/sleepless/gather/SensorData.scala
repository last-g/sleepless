package sleepless.gather

import java.time.ZonedDateTime

/**
 * Created by last-g on 28.08.15.
 */
sealed trait SensorData
case class StringData(data:String) extends SensorData
case class DateTimeData(data:ZonedDateTime) extends SensorData
case class IntegerData(data:Long) extends SensorData
case class BooleanData(data:Boolean) extends SensorData