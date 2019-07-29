package actorEssentials

import akka.actor.{Actor, ActorRef, ActorSystem, Props}

object ActorsWithSenders {

  case class MessageWithSender(num: Int, sender: ActorRef)

  def main(args: Array[String]): Unit = {
    val actorSystem = ActorSystem("SulabhActors")
    val actor1: ActorRef = actorSystem.actorOf(Props[SenderOfActor], "SenderOfActor")
    val actor2: ActorRef = actorSystem.actorOf(Props[ReceiverOfActor], "ReceiverOfActor")
    //println(s"ACTOR in main: ${implicitly[ActorRef]}")
    actor1 ! "Hey Actor1!!"
    actor1 ! MessageWithSender(123, actor2)
  }

  class SenderOfActor extends Actor {
    override def receive: Receive = {
      case str: String => println(s"Just a String: $str from ${context.sender} " +
        s"=======> ACTOR(self/this) in implicit scope: ${implicitly[ActorRef]}")
      case mws: MessageWithSender =>
        println(s"Just an Integer: ${mws.num} with ${mws.sender}")
        mws.sender ! "Hey Actor2!!"
    }
  }

  class ReceiverOfActor extends Actor {
    override def receive: Receive = {
      case str: String => println(s"Message $str from ${context.sender}")
    }
  }

}
