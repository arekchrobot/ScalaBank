package pl.ark.chr.scalabank.account

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.util.Timeout
import pl.ark.chr.scalabank.account.BankAccount._
import pl.ark.chr.scalabank.account.UserAccount._
import pl.ark.chr.scalabank.core.http.RestController

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.{Failure, Success}

class AccountController(implicit val system: ActorSystem) extends RestController
  with AccountJsonProtocol with SprayJsonSupport {

  //TODO: store in DB and restore in case of app shutdown
  private var accounts: Map[String, ActorRef] = Map()

  implicit val timeout = Timeout(300.millis)

  implicit val askDispatcher: ExecutionContext = system.dispatchers.lookup("ask-dispatcher")

  import pl.ark.chr.scalabank.common.MapExtensions._

  override def route(): Route =
    pathPrefix("api" / "account") {
      (path(Segment / "deposit") & post & extractLog ) { (username, log) =>
        entity(as[DepositMoney]) { depositMoney =>
          accounts.get(username) match {
            case None =>
              log.info(s"No user found for username: $username")
              complete(StatusCodes.NotFound)
            case Some(userAccount) =>
              onComplete(userAccount ? depositMoney) {
                case Success(_) => complete(StatusCodes.OK)
                case Failure(ex) =>
                  log.error(s"Failure during depositing money to account: $userAccount with error: $ex")
                  complete(StatusCodes.InternalServerError)
              }
          }
        }
      } ~
      (path(Segment / "withdraw") & post & extractLog ) { (username, log) =>
        entity(as[WithdrawMoney]) { withdrawMoney =>
          accounts.get(username) match {
            case None =>
              log.info(s"No user found for username: $username")
              complete(StatusCodes.NotFound)
            case Some(userAccount) =>
              onComplete(userAccount ? withdrawMoney) {
                case Success(WithdrawFailure(reason)) =>
                  log.error(s"Could not withdraw money from account: $userAccount with reason: $reason")
                  complete(StatusCodes.BadRequest)
                case Success(_) => complete(StatusCodes.OK)
                case Failure(ex) =>
                  log.error(s"Failure during withdrawing money from account: $userAccount with error: $ex")
                  complete(StatusCodes.InternalServerError)
              }
          }
        }
      } ~
      post {
        entity(as[String]) { username =>
          accounts = accounts.getOrElseUpdated(username, {
            val newUser = system.actorOf(UserAccount.props(username), username)
            newUser ! OpenBankAccount
            //TODO: persist account in db
            newUser
          })
          complete(StatusCodes.OK)
        }
      } ~
      (path(Segment / "balance") & get & extractLog) { (username, log) =>
        accounts.get(username) match {
          case None =>
            log.info(s"No user found for username: $username")
            complete(StatusCodes.NotFound)
          case Some(userAccount) =>
            val balances = (userAccount ? GetBalances).mapTo[List[AccountBalance]]
            complete(balances)
        }
      }
    }
}
