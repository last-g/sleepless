package sleepless.gather

import java.util.concurrent.TimeUnit
import concurrent.duration._
import com.typesafe.config.ConfigFactory


object Constants {

  private val config  = ConfigFactory.load()

  val WatcherRequestTime = config.getDuration("gather.probeinterval", TimeUnit.SECONDS).seconds

}
