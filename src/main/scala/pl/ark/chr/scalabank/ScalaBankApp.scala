package pl.ark.chr.scalabank

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import pl.ark.chr.scalabank.account.AccountController
import pl.ark.chr.scalabank.core.http.RestController

import scala.util.{Failure, Success}

object ScalaBankApp extends App {

  implicit val system: ActorSystem = ActorSystem("ScalaBankApp")
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  val log = Logging(system, ScalaBankApp.getClass)

  log.info("Starting Scala Bank application")

  val appConfig = ConfigFactory.load()
  val host = appConfig.getString("server.host")
  val port = appConfig.getInt("server.port")

  val controllers: List[RestController] = List(
    new AccountController
  )

  var route: Route = _

  for(controller <- controllers) {
    if(route == null) {
      route = controller.route()
    } else {
      route = route ~ controller.route()
    }
  }

  Http().bindAndHandle(route, host, port).onComplete {
    case Success(_) => log.info(s"Scala Bank started on http://$host:$port")
    case Failure(ex) => log.error("Http server failed to start!", ex)
  }(system.dispatcher)
}
