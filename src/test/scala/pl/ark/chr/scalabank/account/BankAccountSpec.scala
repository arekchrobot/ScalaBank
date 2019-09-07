package pl.ark.chr.scalabank.account

import akka.actor.{ActorSystem, PoisonPill}
import pl.ark.chr.scalabank.config.ScalaBankPersistenceTestBase

class BankAccountSpec extends ScalaBankPersistenceTestBase(ActorSystem("BankAccountSpec")) {

  import BankAccount._

  val accountNumber = "12345"

  "A BankAccount actor" should {
    "deposit money" in {
      val bankAccountManager = system.actorOf(BankAccount.props(accountNumber))

      val deposit = BigDecimal("10")

      bankAccountManager ! Deposit(deposit)
      expectMsg(DepositSuccess)

      bankAccountManager ! Balance
      expectMsg(AccountBalance(accountNumber, deposit))
    }

    "correctly add up money" in {
      val bankAccountManager = system.actorOf(BankAccount.props(accountNumber))

      val deposit1 = BigDecimal("10.15")
      val deposit2 = BigDecimal("10.21")

      bankAccountManager ! Deposit(deposit1)
      expectMsg(DepositSuccess)

      bankAccountManager ! Deposit(deposit2)
      expectMsg(DepositSuccess)

      bankAccountManager ! Balance
      expectMsg(AccountBalance(accountNumber, deposit1 + deposit2))
    }

    "withdraw money from account" in {
      val bankAccountManager = system.actorOf(BankAccount.props(accountNumber))

      val deposit = BigDecimal("10.15")
      val withdraw = BigDecimal("10")

      bankAccountManager ! Deposit(deposit)
      expectMsg(DepositSuccess)

      bankAccountManager ! Withdraw(withdraw)
      expectMsg(WithdrawSuccess)

      bankAccountManager ! Balance
      expectMsg(AccountBalance(accountNumber, deposit - withdraw))
    }

    "fail when withdraw amount is bigger than available" in {
      val bankAccountManager = system.actorOf(BankAccount.props(accountNumber))

      val deposit = BigDecimal("10")
      val withdraw = BigDecimal("10.11")

      bankAccountManager ! Deposit(deposit)
      expectMsg(DepositSuccess)

      bankAccountManager ! Withdraw(withdraw)
      expectMsg(WithdrawFailure("Withdraw amount: 10.11 is bigger than available balance"))

      bankAccountManager ! Balance
      expectMsg(AccountBalance(accountNumber, deposit))
    }

    "recover it's state in case of failure" in {
      val bankAccountManager = system.actorOf(BankAccount.props(accountNumber))

      val deposit1 = BigDecimal("10.15")
      val deposit2 = BigDecimal("10.21")
      val withdraw = BigDecimal("10")

      bankAccountManager ! Deposit(deposit1)
      expectMsg(DepositSuccess)

      bankAccountManager ! Deposit(deposit2)
      expectMsg(DepositSuccess)

      bankAccountManager ! Withdraw(withdraw)
      expectMsg(WithdrawSuccess)

      bankAccountManager ! PoisonPill

      val bankAccountManager2 = system.actorOf(BankAccount.props(accountNumber))

      bankAccountManager2 ! Balance
      expectMsg(AccountBalance(accountNumber, deposit1 + deposit2 - withdraw))
    }
  }

}
