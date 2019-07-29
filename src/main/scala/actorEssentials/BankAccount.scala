package actorEssentials

import akka.actor.{Actor, ActorRef, ActorSystem, Props}

import scala.util.{Failure, Success}

object BankAccount {
  def main(args: Array[String]): Unit = {
    val actorSystem = ActorSystem("banking")
    val actor = actorSystem.actorOf(Props(new BankAccount("123123123")), "bankaccount")
    val atm = actorSystem.actorOf(Props(new ATM(actor)), "atm")

    atm ! Deposit(900)
    atm ! Withdraw(90.99)

    atm ! Deposit(900)
    atm ! Statement

    atm ! Withdraw(10000)

  }

  class ATM(actor: ActorRef) extends Actor {
    override def receive: Receive = {
      case obj
        if(obj.isInstanceOf[Deposit] || obj.isInstanceOf[Withdraw]) => actor ! obj
      case Statement => actor ! Statement
      case Success(msg) => println(msg)
      case Failure(ex) => println("Operation failed due to " + ex.getMessage)
      case _ => println("Invalid Message")
    }
  }

  case class Deposit(amt: Double)

  case class Withdraw(amt: Double)

  case object Statement

  class BankAccount(accNum: String) extends Actor {
    private var balance: Double = 0.0

    override def receive: Receive = {
      case Deposit(amt) => balance += amt
        context.sender ! Success("Deposited")
      case Withdraw(amt) =>
        if (balance - amt > 0) {
          balance -= amt
          context.sender ! Success("Withdrawn")
        }
        else {
          context.sender ! Failure(new Exception("Low Balance"))
        }
      case Statement => sender ! Success(s"Your balance is $balance")
    }
  }

}
