package actorEssentials.FaultTolerance

import actorEssentials.FaultTolerance.StartingStoppingActors.Parent.{StartChild, Stop, StopChild}
import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Kill, PoisonPill, Props, Terminated}

object StartingStoppingActors extends App {
  val system = ActorSystem("StoppingActorsDemo")

  object Parent {

    case class StartChild(name: String)

    case class StopChild(name: String)

    case object Stop

  }

  class Parent extends Actor with ActorLogging {
    override def receive: Receive = withChildren(Map())

    def withChildren(children: Map[String, ActorRef]): Receive = {
      case StartChild(name) =>
        log.info(s"Starting Child $name")
        context.become(withChildren(children + (name -> context.actorOf(Props[Child], name))))
      case StopChild(name) =>
        log.info(s"Stopping Child $name")
        for (child <- children.get(name))
          context.stop(child)
        context.become(withChildren(children - name))
      case Stop =>
        log.info("Stopping myself")
        context.stop(self)
      case message => log.info(message.toString)
    }
  }

  class Child extends Actor with ActorLogging {
    override def receive: Receive = {
      case message => log.info(message.toString)
    }
  }

  import Parent._

  val parent = system.actorOf(Props[Parent], "parent")
  parent ! StartChild("child1")
  val child = system.actorSelection("/user/parent/child1")
  child ! "Hi Kid!"
  parent ! StopChild("child1")
  //for (_ <- 1 to 50) child ! "R u still There?"

  parent ! StartChild("child2")
  //  Thread.sleep(100)
  val child2 = system.actorSelection("/user/parent/child2")
  child2 ! "Hi, second child!"

  parent ! Stop

  for (_ <- 1 to 10) {
    parent ! "parent, are you still there?" //should not be received
  }
  for (i <- 1 to 100)
    child2 ! s"[$i] Second Kid, R u still There?"

  val looseActor = system.actorOf(Props[Child], "looseActor")
  looseActor ! "Hello, loose actor!"
  looseActor ! PoisonPill
  looseActor ! "Loose Actor, R u still there?"

  val abruptlyTerminatedActor = system.actorOf(Props[Child], "abruptlyTerminatedActor")
  abruptlyTerminatedActor ! "You are about to be terminated"
  abruptlyTerminatedActor ! Kill  // Makes the actor throw an ActorKilledException
  abruptlyTerminatedActor ! "You have been terminated"

  class Watcher extends Actor with ActorLogging {
    import Parent._
    override def receive: Receive = {
      case StartChild(name) =>
        val child = context.actorOf(Props[Child], name)
        log.info(s"Started & Watching child $name")
        // Any actor can watch any other actor, not just the direct children
        context.watch(child)
        //context.unwatch(child)
      case Terminated(ref) =>
        // Terminated Message will be received even if the actor was already dead when you started watching it
        log.info(s"The reference I m watching $ref has been stopped")
    }
  }

  val watcher = system.actorOf(Props[Watcher], "watcher")
  watcher ! StartChild("watchedChild")
  val watchedChild = system.actorSelection("/user/watcher/watchedChild")
  watchedChild ! PoisonPill
}
