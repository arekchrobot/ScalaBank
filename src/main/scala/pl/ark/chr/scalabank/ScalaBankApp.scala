package pl.ark.chr.scalabank

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory

import scala.util.{Failure, Success}

object ScalaBankApp extends App {

  implicit val system: ActorSystem = ActorSystem("ScalaBankApp")
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  val log = Logging(system, ScalaBankApp.getClass)

  log.info("Starting Scala Bank application")

  val appConfig = ConfigFactory.load()
  val host = appConfig.getString("server.host")
  val port = appConfig.getInt("server.port")

  val testRoute =
    path("hello") {
      get {
        complete(StatusCodes.OK)
      }
    }

  Http().bindAndHandle(testRoute, host, port).onComplete {
    case Success(_) => log.info(s"Scala Bank started on http://$host:$port")
    case Failure(ex) => log.error("Http server failed to start!", ex)
  }(system.dispatcher)
}
