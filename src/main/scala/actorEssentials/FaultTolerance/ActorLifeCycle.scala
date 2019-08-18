package actorEssentials.FaultTolerance

import akka.actor.{Actor, ActorLogging, ActorSystem, PoisonPill, Props}

object ActorLifeCycle extends App {

  case object StartChild

  class LifecycleActor extends Actor with ActorLogging {
    override def preStart(): Unit = log.info("I am starting")

    override def postStop(): Unit = log.info("I have stopped")

    override def receive: Receive = {
      case StartChild =>
        context.actorOf(Props[LifecycleActor], "child")
    }
  }

  val system = ActorSystem("LifecycleDemo")

  //  val parent = system.actorOf(Props[LifecycleActor], "parent")
  //  parent ! StartChild
  //  parent ! PoisonPill

  case object Fail

  case object FailChild

  case object Check

  case object CheckChild

  class Parent extends Actor {
    val child = context.actorOf(Props[Child], "SupervisedChild")

    override def receive: Receive = {
      case FailChild => child ! Fail
      case CheckChild => child ! Check
    }
  }

  //Child actor is restarted after exception
  class Child extends Actor with ActorLogging {
    override def preStart(): Unit = log.info("SupervisedChild started")

    override def postStop(): Unit = log.info("SupervisedChild stopped")

    override def preRestart(reason: Throwable, message: Option[Any]): Unit =
      log.info(s"SupervisedActor restarting because of ${reason.getMessage}")

    override def postRestart(reason: Throwable): Unit = log.info("Supervised actor restarted")

    override def receive: Receive = {
      case Fail =>
        log.warning("child will fail now")
        throw new RuntimeException("I failed")
      case Check => log.info("Alive & Kicking")
    }
  }

  val supervisor = system.actorOf(Props[Parent], "supervisor")
  supervisor ! FailChild

  //Default supervision strategy: If an actor threw an exception while processing a message, this message which caused
  // the exception to be thrown is removed from the queue and not put back to the mailbox once again, and the actor is
  // restarted which means the mailbox is untouched
  supervisor ! CheckChild
}
