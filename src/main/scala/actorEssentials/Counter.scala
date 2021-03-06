package actorEssentials

import akka.actor.{Actor, ActorSystem, Props}

object Counter {
  def main(args: Array[String]): Unit = {
    val actorSystem = ActorSystem("Excercise")
    val actor = actorSystem.actorOf(Props[Counter], "counter")
    actor ! "Increment"
    actor ! "Increment"
    actor ! "Increment"
    actor ! "Increment"
    actor ! "Decrement"
    actor ! "Decrement"
    actor ! "Increment"
    actor ! "Decrement"
    actor ! "Decrement"
    actor ! "Print"
  }

  class Counter extends Actor {
    //private var state: Int = 0

    //    override def receive: Receive = {
    //      case "Increment" => state = state + 1
    //      case "Decrement" => state = state - 1
    //      case "Print" => println(s"Current balance = $state")
    //    }

    override def receive: Receive = countReceive(0)

    def countReceive(currentCount: Int): Receive = {
      case "Increment" => context.become(countReceive(currentCount + 1))
      case "Decrement" => context.become(countReceive(currentCount - 1))
      case "Print" => println(s"Current count: $currentCount")
    }
  }

}
