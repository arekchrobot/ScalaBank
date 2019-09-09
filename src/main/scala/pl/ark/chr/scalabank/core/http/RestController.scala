package pl.ark.chr.scalabank.core.http

import akka.http.scaladsl.server.Route

trait RestController {

  def route(): Route
}
