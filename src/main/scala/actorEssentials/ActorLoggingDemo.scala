package actorEssentials

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.event.Logging

object ActorLoggingDemo extends App {

  val system = ActorSystem("Actor-Logging")
  val actor = system.actorOf(Props[SimpleActorWithExplicitLogger])
  actor ! "Logging a simple message"
  val actorWithLogging = system.actorOf(Props[ActorWithLogging])
  actorWithLogging ! "Logging via ActorLogging trait"
  actorWithLogging ! (10, 90)

  class SimpleActorWithExplicitLogger extends Actor {
    val logger = Logging(context.system, this)
    override def receive: Receive = {
      case message => logger.info(message.toString)
    }
  }

  class ActorWithLogging extends Actor with ActorLogging {
    override def receive: Receive = {
      case (a,b) => log.info("Two Things: {} and {}", a, b) //String interpolation
      case message => log.info(message.toString)
    }
  }
}
