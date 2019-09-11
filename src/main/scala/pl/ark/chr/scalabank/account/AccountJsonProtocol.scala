package pl.ark.chr.scalabank.account

import pl.ark.chr.scalabank.account.BankAccount.AccountBalance
import pl.ark.chr.scalabank.account.UserAccount._
import spray.json.DefaultJsonProtocol

trait AccountJsonProtocol extends DefaultJsonProtocol {

  implicit val accountBalanceFormat = jsonFormat2(AccountBalance)
  implicit val depositMoneyFormat = jsonFormat2(DepositMoney)
  implicit val withdrawMoneyFormat = jsonFormat2(WithdrawMoney)
}
