package actorEssentials.AkkaInfrastructure

import akka.actor.{Actor, ActorLogging, ActorSystem, Cancellable, PoisonPill, Props, Timers}

object TimersSchedulers extends App {

  class SimpleActor extends Actor with ActorLogging {
    override def receive: Receive = {
      case message => log.info(message.toString)
    }
  }

  val system = ActorSystem("TimersSchedulersDemo")

  //  val simpleActor = system.actorOf(Props[SimpleActor])
  //
  //  import scala.concurrent.duration._
  //
  //  system.log.info("Scheduling Reminder for SimpleActor")
  //
  //  //implicit val executionContext = system.dispatcher
  //  import system.dispatcher
  //
  //  system.scheduler.scheduleOnce(1 second) {
  //    simpleActor ! "reminder"
  //  }
  //
  //  val routine: Cancellable = system.scheduler.schedule(1 second, 2 second) {
  //    simpleActor ! "heartbeat"
  //  }
  //
  //  system.scheduler.scheduleOnce(5 second) {
  //    routine.cancel()
  //  }

  /* Excercise: implement a self-closing actor
   *
   * - if the actor receives a message (anything), you have 1 second to send it another message
   * - if the time window expires, the actor will stop itself
   * - if you send another message, the time window is reset
   */

  import scala.concurrent.duration._
  import system.dispatcher

  class SelfClosingActor extends Actor with ActorLogging {
    override def postStop(): Unit = log.info("Stopping the actor now")

    override def receive: Receive = {
      case msg =>
        log.info(msg.toString)
        val routine = context.system.scheduler.scheduleOnce(2 second) {
          context.self ! PoisonPill
        }
        context.become(setWindow(routine))
    }

    def setWindow(routine: Cancellable): Receive = {
      case "timeout" =>
        log.warning("Stopping self now")
        context.stop(self)
      case msg =>
        log.info(msg.toString)
        routine.cancel()
        context.become(
          setWindow(context.system.scheduler.scheduleOnce(2 second) {
            self ! "timeout"
          }))
    }
  }

  //  val selfClosingActor = system.actorOf(Props[SelfClosingActor], "selfclosingactor")
  //  selfClosingActor ! "start off timer"
  //
  //  var heatbeattime: Int = 0
  //  for (i <- 1 to 100) {
  //    //Thread.sleep(i*200)
  //    heatbeattime += i * 200
  //    system.scheduler.scheduleOnce(heatbeattime millisecond) {
  //      selfClosingActor ! s"Message: $i"
  //    }
  //  }

  case object TimerKey

  case object Start

  case object Reminder

  case object Stop

  class TimerBasedHeartbeatActor extends Actor with ActorLogging with Timers {
    timers.startSingleTimer(TimerKey, Start, 500 millis)

    override def receive: Receive = {
      case Start =>
        log.info("Bootstrapping")

        // Starting timer cancels the previous timer with the same key
        timers.startPeriodicTimer(TimerKey, Reminder, 1 second)
      case Reminder =>
        log.info("I m alive")
      case Stop =>
        log.warning("Stopping")
        timers.cancel(TimerKey)
        context.stop(self)
    }
  }

  val timerHeartbeatActor = system.actorOf(Props[TimerBasedHeartbeatActor], "timerActor")
  system.scheduler.scheduleOnce(5 second) {
    timerHeartbeatActor ! Stop
  }

}
