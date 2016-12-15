package test

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.ws.ahc.{AhcWSClient, AhcWSClientConfig}
import scala.sys.addShutdownHook

/** Helper object to simplify testing in the Scala console by constructing
  * all the necessary implicits that the Play WS library requires.
  */
object ConsoleSupport {

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val ec = defaultContext
  implicit val ws = AhcWSClient(AhcWSClientConfig())

  /** Close and release resources associated with this WS client instance. This *must*
    * be called before exiting the SBT console or else SBT will hang.
    */
  def close() = {
    ws.close
    system.terminate
  }

  addShutdownHook(close())
}
