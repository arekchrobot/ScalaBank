package pl.ark.chr.scalabank.account

import java.util.UUID

import akka.actor.{Actor, ActorLogging, ActorRef, PoisonPill, Props}
import akka.persistence.PersistentActor
import scala.concurrent.duration._

object UserAccount {
  //COMMANDS
  case object InitAccount
  case object CreateBankAccount
  case class DepositMoney(accountNumber: String, amount: BigDecimal)
  case class WithdrawMoney(accountNumber: String, amount: BigDecimal)
  case class CloseBankAccount(accountNumber: String)
  case object GetBalances

  //EVENTS
  case class CreateBankAccountEvent(accountNumber: String)
  case class CloseBankAccountEvent(accountNumber: String)

  //RECOVER COMMANDS
  private case class RestoreBankAccount(accountNumber: String)
  private case class ShutdownBankAccount(accountNumber: String)
  private case object RestoreAccount

  //RESPONSES
  case object BalanceRetrievalTimeout
  case object BankAccountCreated
  case object BankAccountClosed

  def props(username: String): Props = Props(new UserAccount(username))
}

class UserAccount(username: String) extends PersistentActor with ActorLogging {

  import UserAccount._
  import BankAccount._

  override def persistenceId: String = username

  override def receiveCommand: Receive = initialize()

  def initialize(): Receive = {
    case InitAccount =>
      val newBankAccount = createBankAccount
      context.become(accountManagement(Map(newBankAccount)))
    case RestoreAccount =>
      context.become(accountManagement(Map()))
  }

  def accountManagement(bankAccounts: Map[String, ActorRef]): Receive = {
    case CreateBankAccount =>
      val newBankAccount = createBankAccount
      persist(CreateBankAccountEvent(newBankAccount._1)) { _ =>
        sender() ! BankAccountCreated
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
      processCashOperation[Deposit](bankAccounts, accountNumber, Deposit(amount))
    case WithdrawMoney(accountNumber, amount) =>
      log.info(s"Withdrawing money: $amount from account: $accountNumber")
      processCashOperation[Withdraw](bankAccounts, accountNumber, Withdraw(amount))
    case GetBalances =>
      val originalSender = sender()
      context.actorOf(Props(extraActor(bankAccounts, originalSender)))
    case RestoreBankAccount(accountNumber) =>
      val restoredBankAccount = createBankAccount(accountNumber)
      context.become(accountManagement(bankAccounts + restoredBankAccount))
    case ShutdownBankAccount(accountNumber) =>
      bankAccounts.get(accountNumber) match {
        case Some(bankAccount) =>
          bankAccount ! PoisonPill
          context.become(accountManagement(bankAccounts - accountNumber))
        case None =>
          log.warning(s"User: $username cannot close bank account: $accountNumber since user is not owner of it.")
      }
  }

  private def extraActor(bankAccounts: Map[String, ActorRef], originalSender: ActorRef) =
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

  private def processCashOperation[T](bankAccounts: Map[String, ActorRef], accountNumber: String, message: T): Unit = {
    bankAccounts.get(accountNumber) match {
      case Some(bankAccount) =>
        bankAccount ! message
      case None => ??? //TODO: return failure
    }
  }

  override def receiveRecover: Receive = {
    case CreateBankAccountEvent(accountNumber) =>
      log.info(s"Restoring bank account: $accountNumber")
      self ! RestoreBankAccount(accountNumber)
    case CloseBankAccountEvent(accountNumber) =>
      log.info(s"Shutting down bank account: $accountNumber")
      self ! ShutdownBankAccount(accountNumber)
  }
}
