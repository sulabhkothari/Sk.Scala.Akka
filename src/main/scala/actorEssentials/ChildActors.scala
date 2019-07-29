package actorEssentials

import actorEssentials.ChildActors.CreditCard.{AttachToAccount, CheckStatus}
import actorEssentials.ChildActors.NaiveBankAccount.{Deposit, InitializeAccount}
import actorEssentials.ChildActors.Parent.{CreateChild, TellChild}
import akka.actor.{Actor, ActorRef, ActorSystem, Props}

object ChildActors extends App {
  println("Hello child actors !!")
  val system = ActorSystem("Parent-Child")
  val parent = system.actorOf(Props[Parent], "parent")
  parent ! CreateChild("child")
  parent ! TellChild("Hey the child actor!")

  val childSelection = system.actorSelection("/user/parent/child")
  childSelection ! "I found the actor!"

  val bankAccountRef = system.actorOf(Props[NaiveBankAccount], "naiveaccount")
  bankAccountRef ! InitializeAccount
  bankAccountRef ! Deposit(100)

  Thread.sleep(500)
  val ccSelection = system.actorSelection("/user/naiveaccount/credit-card")
  ccSelection ! CheckStatus

  object Parent {

    case class CreateChild(name: String)

    case class TellChild(message: String)

  }

  class Parent extends Actor {

    import Parent._

    override def receive: Receive = createChild

    def createChild: Receive = {
      case CreateChild(name) =>
        println(s"${self.path} creating child")
        context.become(tellChild(context.actorOf(Props[Child], name)))
    }

    def tellChild(child: ActorRef): Receive = {
      case TellChild(message) => child forward message
      case CreateChild(name) => context.become(tellChild(context.actorOf(Props[Child], name)))
    }
  }

  class Child extends Actor {
    override def receive: Receive = {
      case message => println(s"${self.path} I got message: $message")
    }
  }

  object NaiveBankAccount {

    case class Deposit(amount: Int)

    case class Withdraw(amount: Int)

    case object InitializeAccount

  }

  class NaiveBankAccount extends Actor {
    import NaiveBankAccount._
    import CreditCard._
    var amount = 0
    override def receive: Receive = {
      case InitializeAccount =>
        val creditCardRef = context.actorOf(Props[CreditCard], "credit-card")
        creditCardRef ! AttachToAccount(this)
      case Deposit(amt) => deposit(amt)
      case Withdraw(amt) => withdraw(amt)
    }

    def deposit(funds: Int) = {
      println(s"${self.path} depositing $funds on top of $amount.")
      amount += funds
    }

    def  withdraw(funds:Int) = {
      println(s"${self.path} withdrawing $funds from $amount.")
      amount -= funds
    }
  }

  object CreditCard {

    case class AttachToAccount(bankAccount: NaiveBankAccount) // This is a problem because it contains an instance of
                                                              // actual actor JVM object
                                                              // It should instead be an ActorRef
    case object CheckStatus

  }

  class CreditCard extends Actor {
    override def receive: Receive = {
      case AttachToAccount(account) => context.become(attachedTo(account))
    }

    def attachedTo(account: NaiveBankAccount): Receive = {
      case CheckStatus =>
        println(s"${self.path} your message has been processed.")
        account.withdraw(1)
    }
  }



}
