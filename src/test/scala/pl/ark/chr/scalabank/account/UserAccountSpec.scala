package pl.ark.chr.scalabank.account

import akka.actor.{ActorSystem, PoisonPill}
import pl.ark.chr.scalabank.account.BankAccount.AccountBalance
import pl.ark.chr.scalabank.config.ScalaBankPersistenceTestBase
import scala.concurrent.duration._

class UserAccountSpec extends ScalaBankPersistenceTestBase(ActorSystem("UserAccountSpec")) {

  import UserAccount._

  val username = "arkchr1234"
  val depositAmount = BigDecimal("10")
  val withdrawAmount = BigDecimal("8")

  "A UserAccount actor" should {
    "create new bank account" in {
      val userAccount = system.actorOf(props(username))

      userAccount ! OpenBankAccount
      expectMsgType[BankAccountOpened]

      within(300.millis) {
        userAccount ! GetBalances
        val result = expectMsgType[List[AccountBalance]]

        assert(result.size == 1)
      }
    }

    "deposit and withdraw money" in {
      val userAccount = system.actorOf(props(username))

      userAccount ! OpenBankAccount
      val bankAccount = expectMsgType[BankAccountOpened]

      userAccount ! DepositMoney(bankAccount.accountNumber, depositAmount)
      expectNoMsg(250.millis)

      userAccount ! WithdrawMoney(bankAccount.accountNumber, withdrawAmount)
      expectNoMsg(250.millis)

      within(300.millis) {
        userAccount ! GetBalances
        val result = expectMsgType[List[AccountBalance]]

        assert(result.size == 1)
        assert(result(0).balance == (depositAmount - withdrawAmount))
      }
    }

    "correctly return balances" in {
      val userAccount = system.actorOf(props(username))

      userAccount ! OpenBankAccount
      val bankAccount1 = expectMsgType[BankAccountOpened]

      userAccount ! OpenBankAccount
      val bankAccount2 = expectMsgType[BankAccountOpened]

      userAccount ! DepositMoney(bankAccount1.accountNumber, depositAmount)
      expectNoMsg(250.millis)

      userAccount ! WithdrawMoney(bankAccount1.accountNumber, withdrawAmount)
      expectNoMsg(250.millis)

      userAccount ! DepositMoney(bankAccount2.accountNumber, depositAmount)
      expectNoMsg(250.millis)

      within(300.millis) {
        userAccount ! GetBalances
        val result = expectMsgType[List[AccountBalance]]

        assert(result.size == 2)
        assert(result.map(_.balance).sum == (depositAmount - withdrawAmount + depositAmount))
      }
    }

    "close bank account" in {
      val userAccount = system.actorOf(props(username))

      userAccount ! OpenBankAccount
      val bankAccount1 = expectMsgType[BankAccountOpened]

      userAccount ! OpenBankAccount
      val bankAccount2 = expectMsgType[BankAccountOpened]

      userAccount ! CloseBankAccount(bankAccount1.accountNumber)
      expectMsg(BankAccountClosed)

      within(300.millis) {
        userAccount ! GetBalances
        val result = expectMsgType[List[AccountBalance]]

        assert(result.size == 1)
        assert(result(0).accountNumber == bankAccount2.accountNumber)
      }
    }

    "recover it's state" in {
      val userAccount = system.actorOf(props(username))

      userAccount ! OpenBankAccount
      expectMsgType[BankAccountOpened]

      userAccount ! OpenBankAccount
      expectMsgType[BankAccountOpened]

      userAccount ! PoisonPill

      val userAccount2 = system.actorOf(props(username))

      within(300.millis) {
        userAccount2 ! GetBalances
        val result = expectMsgType[List[AccountBalance]]

        assert(result.size == 2)
      }
    }
  }

}
