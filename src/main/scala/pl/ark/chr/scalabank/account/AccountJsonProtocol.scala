package pl.ark.chr.scalabank.account

import pl.ark.chr.scalabank.account.BankAccount.AccountBalance
import spray.json.DefaultJsonProtocol

trait AccountJsonProtocol extends DefaultJsonProtocol {

  implicit val accountBalanceFormat = jsonFormat2(AccountBalance)
}
