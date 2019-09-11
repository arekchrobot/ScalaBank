package pl.ark.chr.scalabank.account

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.StatusCodes
import pl.ark.chr.scalabank.account.BankAccount.AccountBalance
import pl.ark.chr.scalabank.account.UserAccount._
import pl.ark.chr.scalabank.config.ScalaBankHttpTestBase

class AccountControllerSpec extends ScalaBankHttpTestBase with AccountJsonProtocol with SprayJsonSupport {

  val accountController = new AccountController

  val username = "arkchr1234"
  val depositAmount = BigDecimal("13.0")
  val withdrawAmount = BigDecimal("10.0")

  "An AccountController" should {
    "create account" in {
      Post("/api/account", username) ~> accountController.route() ~> check {
        status shouldBe StatusCodes.OK
      }
    }

    "get balances for account" in {
      Post("/api/account", username) ~> accountController.route()
      Thread.sleep(2000)
      Get(s"/api/account/$username/balance") ~> accountController.route() ~> check {
        status shouldBe StatusCodes.OK
        entityAs[List[AccountBalance]].size shouldBe 1
      }
    }

    "correctly deposit and withdraw money from account" in {
      Post("/api/account", username) ~> accountController.route()
      Thread.sleep(2000)
      Get(s"/api/account/$username/balance") ~> accountController.route() ~> check {
        val accountNumber = entityAs[List[AccountBalance]].head.accountNumber
        Post(s"/api/account/$username/deposit", DepositMoney(accountNumber, depositAmount)) ~> accountController.route() ~> check {
          status shouldBe StatusCodes.OK
        }
        Post(s"/api/account/$username/withdraw", WithdrawMoney(accountNumber, withdrawAmount)) ~> accountController.route() ~> check {
          status shouldBe StatusCodes.OK
        }
      }
    }

    "not be able to withdraw amount bigger than balance" in {
      Post("/api/account", username) ~> accountController.route()
      Thread.sleep(2000)
      Get(s"/api/account/$username/balance") ~> accountController.route() ~> check {
        val accountNumber = entityAs[List[AccountBalance]].head.accountNumber
        Post(s"/api/account/$username/withdraw", WithdrawMoney(accountNumber, withdrawAmount)) ~> accountController.route() ~> check {
          status shouldBe StatusCodes.BadRequest
        }
      }
    }
  }
}
