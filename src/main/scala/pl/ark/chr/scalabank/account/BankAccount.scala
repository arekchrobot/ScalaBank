package pl.ark.chr.scalabank.account

import akka.actor.{ActorLogging, ActorRef, Props, Timers}
import akka.persistence.{PersistentActor, SaveSnapshotFailure, SaveSnapshotSuccess, SnapshotOffer}
import pl.ark.chr.scalabank.serialization.AvroSerializable

import scala.concurrent.duration._

object BankAccount {
  //COMMANDS
  private[account] case class Deposit(amount: BigDecimal)
  private[account] case class Withdraw(amount: BigDecimal)
  private[account] case object Balance

  //EVENTS
  case class DepositEvent(balance: BigDecimal, amount: BigDecimal) extends AvroSerializable
  case class WithdrawEvent(balance: BigDecimal, amount: BigDecimal) extends AvroSerializable

  //RESPONSES
  case object DepositSuccess
  case object WithdrawSuccess
  case class WithdrawFailure(reason: String)
  case class AccountBalance(accountNumber: String, balance: BigDecimal)

  //TIMERS
  private case object SnapshotKey
  private case object CreateSnapshot

  private[account] def props(accountNumber: String): Props = Props(new BankAccount(accountNumber))
}

private[account] class BankAccount(accountNumber: String) extends Timers with PersistentActor with ActorLogging {
  import BankAccount._

  timers.startPeriodicTimer(SnapshotKey, CreateSnapshot, 10.minutes)

  override def persistenceId: String = accountNumber

  override def receiveCommand: Receive = storage(BigDecimal("0"))

  def storage(balance: BigDecimal): Receive = {
    case Deposit(amount) =>
      val originalSender = sender()
      log.info(s"Depositing: $amount to account: $accountNumber")
      persist(DepositEvent(balance, amount)) { _ =>
        log.info(s"Successfully deposited $amount to: $accountNumber")
        originalSender ! DepositSuccess
        context.become(storage(balance + amount))
      }
    case Withdraw(amount) =>
      val originalSender = sender()
      log.info(s"Withdrawing $amount from account: $accountNumber")
      if(amount > balance) {
        failedWithdraw(amount, originalSender)
      } else {
        persist(WithdrawEvent(balance, amount)) { _ =>
          log.info(s"Successfully withdrawn $amount from account: $accountNumber")
          originalSender ! WithdrawSuccess
          context.become(storage(balance - amount))
        }
      }
    case Balance =>
      log.info(s"Returning balance for account: $accountNumber")
      sender() ! AccountBalance(accountNumber, balance)
    case CreateSnapshot =>
      log.info(s"Saving balance snapshot for account: $accountNumber")
      saveSnapshot(balance)
    case SaveSnapshotSuccess(metadata) =>
      log.info(s"Saving balance snapshot succeeded: $metadata for account: $accountNumber")
      deleteSnapshot(metadata.sequenceNr - 1)
    case SaveSnapshotFailure(metadata, reason) =>
      log.warning(s"saving snapshot $metadata failed bcs of $reason for account: $accountNumber")
  }

  private def failedWithdraw(amount: BigDecimal, sender: ActorRef): Unit = {
    val msg = s"Withdraw amount: $amount is bigger than available balance"
    log.warning(msg)
    sender ! WithdrawFailure(msg)
  }

  override def receiveRecover: Receive = {
    case DepositEvent(balance, amount) =>
      log.info(s"Recovered depositing $amount for account: $accountNumber")
      context.become(storage(balance + amount))
    case WithdrawEvent(balance, amount) =>
      log.info(s"Recovered withdrawing $amount from account: $accountNumber")
      context.become(storage(balance - amount))
    case SnapshotOffer(metadata, contents) =>
      log.info(s"Recovered snapshot: $metadata")
      context.become(storage(contents.asInstanceOf[BigDecimal]))
  }
}
