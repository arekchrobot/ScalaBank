package pl.ark.chr.scalabank.account

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.StatusCodes
import pl.ark.chr.scalabank.account.BankAccount.AccountBalance
import pl.ark.chr.scalabank.config.ScalaBankHttpTestBase

class AccountControllerSpec extends ScalaBankHttpTestBase with AccountJsonProtocol with SprayJsonSupport {

  val accountController = new AccountController

  val username = "arkchr1234"

  "An AccountController" should {
    "create account" in {
      Post("/api/account", username) ~> accountController.route() ~> check {
        status shouldBe StatusCodes.OK
      }
    }

    "get balances for account" in {
      Post("/api/account", username) ~> accountController.route() ~> check {
        status shouldBe StatusCodes.OK
      }
      Thread.sleep(2000)
      Get(s"/api/account/$username/balance") ~> accountController.route() ~> check {
        status shouldBe StatusCodes.OK
        entityAs[List[AccountBalance]].size shouldBe 1
      }
    }
  }
}
