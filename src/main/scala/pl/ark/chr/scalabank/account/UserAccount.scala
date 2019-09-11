package pl.ark.chr.scalabank.account

import java.util.UUID

import akka.actor.{Actor, ActorLogging, ActorRef, PoisonPill, Props}
import akka.persistence.{PersistentActor, RecoveryCompleted}
import pl.ark.chr.scalabank.core.serialization.AvroSerializable
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

object UserAccount {
  //COMMANDS
  case object OpenBankAccount
  case class DepositMoney(accountNumber: String, amount: BigDecimal)
  case class WithdrawMoney(accountNumber: String, amount: BigDecimal)
  case class CloseBankAccount(accountNumber: String)
  case object GetBalances

  //EVENTS
  case class OpenBankAccountEvent(accountNumber: String) extends AvroSerializable
  case class CloseBankAccountEvent(accountNumber: String) extends AvroSerializable

  //RECOVER COMMANDS
  private case class RestoreBankAccount(accountNumber: String)
  private case class ShutdownBankAccount(accountNumber: String)
  private case object BankAccountRecoveryCompleted

  //RESPONSES
  case object BalanceRetrievalTimeout
  case class BankAccountOpened(accountNumber: String)
  case object BankAccountClosed
  case object BankAccountNotFound

  def props(username: String): Props = Props(new UserAccount(username))
}

class UserAccount(username: String) extends PersistentActor with ActorLogging {

  import BankAccount._
  import UserAccount._

  override def persistenceId: String = username

  override def receiveCommand: Receive = accountManagement(Map())

  def accountManagement(bankAccounts: Map[String, ActorRef]): Receive = {
    case OpenBankAccount =>
      val newBankAccount = createBankAccount
      persist(OpenBankAccountEvent(newBankAccount._1)) { _ =>
        sender() ! BankAccountOpened(newBankAccount._1)
        //TODO: persist bank account in DB
        context.become(accountManagement(bankAccounts + newBankAccount))
      }
    case CloseBankAccount(accountNumber) =>
      log.info(s"Closing bank account: $accountNumber for user: $username")
      bankAccounts.get(accountNumber) match {
        case Some(bankAccount) =>
          bankAccount ! PoisonPill
          persist(CloseBankAccountEvent(accountNumber)) { _ =>
            sender() ! BankAccountClosed
            context.become(accountManagement(bankAccounts - accountNumber))
          }
        case None =>
          log.warning(s"User: $username cannot close bank account: $accountNumber since user is not owner of it.")
      }
    case DepositMoney(accountNumber, amount) =>
      log.info(s"Depositing money: $amount to account: $accountNumber")
      processCashOperation[Deposit](bankAccounts, accountNumber, Deposit(amount), sender())
    case WithdrawMoney(accountNumber, amount) =>
      log.info(s"Withdrawing money: $amount from account: $accountNumber")
      processCashOperation[Withdraw](bankAccounts, accountNumber, Withdraw(amount), sender())
    case GetBalances =>
      log.info(s"Getting balance for user: $username")
      val originalSender = sender()
      context.actorOf(Props(balanceCollectingActor(bankAccounts, originalSender)))
  }

  def restoring(bankAccounts: Map[String, ActorRef]): Receive = {
    case RestoreBankAccount(accountNumber) =>
      val restoredBankAccount = createBankAccount(accountNumber)
      context.become(restoring(bankAccounts + restoredBankAccount))
    case ShutdownBankAccount(accountNumber) =>
      bankAccounts.get(accountNumber) match {
        case Some(bankAccount) =>
          bankAccount ! PoisonPill
          context.become(restoring(bankAccounts - accountNumber))
        case None =>
          log.warning(s"User: $username cannot close bank account: $accountNumber since user is not owner of it.")
      }
    case BankAccountRecoveryCompleted =>
      unstashAll()
      context.become(accountManagement(bankAccounts))
    case _ => stash()
  }

  private def balanceCollectingActor(bankAccounts: Map[String, ActorRef], originalSender: ActorRef) =
    new Actor() {
      var balances: List[AccountBalance] = List()
      val accountSize = bankAccounts.size

      override def receive: Receive = {
        case accBalance: AccountBalance =>
          balances = accBalance :: balances
          collectBalances()
        case BalanceRetrievalTimeout =>
          sendResponseAndShutdown(BalanceRetrievalTimeout)
      }

      def collectBalances(): Unit =
        if (balances.size == accountSize) {
          timeoutMessager.cancel()
          sendResponseAndShutdown(balances)
        }

      def sendResponseAndShutdown(response: Any) = {
        originalSender ! response
        context.stop(self)
      }

      bankAccounts.foreach(_._2 ! Balance)

      import context.dispatcher

      val timeoutMessager = context.system.scheduler.scheduleOnce(250.milliseconds) {
        self ! BalanceRetrievalTimeout
      }
    }

  private def createBankAccount: (String, ActorRef) = {
    val accountNumber = UUID.randomUUID().toString
    createBankAccount(accountNumber)
  }

  private def createBankAccount(accountNumber: String): (String, ActorRef) = {
    log.info(s"Creating bank account: $accountNumber for user: $username")
    val bankAccount = context.actorOf(BankAccount.props(accountNumber), accountNumber)
    (accountNumber, bankAccount)
  }

  implicit val timeout = Timeout(250.millis)
  implicit val askDispatcher: ExecutionContext = context.system.dispatchers.lookup("ask-dispatcher")

  private def processCashOperation[T](bankAccounts: Map[String, ActorRef], accountNumber: String, message: T, sender: ActorRef): Unit = {
    bankAccounts.get(accountNumber) match {
      case Some(bankAccount) =>
        (bankAccount ? message).map(sender ! _)
      case None =>
        sender ! BankAccountNotFound
    }
  }

  override def receiveRecover: Receive = {
    case OpenBankAccountEvent(accountNumber) =>
      log.info(s"Restoring bank account: $accountNumber for user: $username")
      self ! RestoreBankAccount(accountNumber)
    case CloseBankAccountEvent(accountNumber) =>
      log.info(s"Shutting down bank account: $accountNumber for user: $username")
      self ! ShutdownBankAccount(accountNumber)
    case RecoveryCompleted =>
      log.info(s"Recovery completed for user: $username")
      self ! BankAccountRecoveryCompleted
      context.become(restoring(Map()))
  }
}
