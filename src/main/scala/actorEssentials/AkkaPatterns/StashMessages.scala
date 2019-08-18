package actorEssentials.AkkaPatterns

import akka.actor.{Actor, ActorLogging, ActorSystem, Props, Stash}

object StashMessages extends App {
  /**
    * ResourceActor
    *  - open => it can receive read/write requests to the resource
    *  - otherwise it will postpone all read/write requests until the state is open
    *
    *  ResourceActor is closed
    *   - open => switch to the open state
    *   - Read, Write messages are postponed
    *
    *  ResourceActor is open
    *   - Read, Write are handled
    *   - Closed => switch to the closed state
    *
    *  [Open, Read, Read, Write]
    */

  case object Open
  case object Close
  case object Read
  case class Write(data: String)
  class ResourceActor extends Actor with ActorLogging with Stash {
    private var innerData: String = ""

    override def receive: Receive = closedReceive

    def closedReceive(): Receive = {
      case Open =>
        log.info("Opening resources")
        unstashAll()
        context.become(openReceive())
      case message =>
        log.info(s"Stashing $message because I can't handle it in the closed state")
        stash()
    }

    def openReceive(): Receive = {
      case Close =>
        log.info("Closing resource")
        unstashAll() //if stashed messages contain an Open closedReceive will again be replaced by openReceive
        context.become(closedReceive())
      case Read =>
        log.info(s"I am reading data $innerData")
      case Write(txt) =>
        log.info(s"I am writing data $txt")
        innerData = txt
      case message =>
        log.info(s"Stashing $message because I can't handle it in the open state")
        stash()
        //stash() //Causes error because of duplicate stash
    }
  }

  val system = ActorSystem("Stashing")
  val resourceActor = system.actorOf(Props[ResourceActor], "resourceActor")

  resourceActor ! Read
  resourceActor ! Open
  resourceActor ! Open  //This is stashed and will be unstashed as soon as Close message is encountered which will rollback to openReceive
  resourceActor ! Write("I love stash")
  resourceActor ! Close //switch to closed; switch to open again because Open action was stashed
  resourceActor ! Read
}
