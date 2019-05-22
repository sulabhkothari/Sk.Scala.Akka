import Supervision.RequestLoanQuotation
import akka.actor.SupervisorStrategy.{Escalate, Restart, Resume, Stop}
import akka.actor.{Actor, ActorRef, ActorSystem, OneForOneStrategy, Props}

import scala.concurrent.duration._
object Supervision {
  case class RequestLoanQuotation()

  def main(args: Array[String]): Unit = {
    val actorSystem = ActorSystem("SulabhActors")
    val supervisor: ActorRef = actorSystem.actorOf(Props[Supervision], "supervisor")
    supervisor ! RequestLoanQuotation
  }
}

class Supervision extends Actor
{
  override def receive: Receive = {
    case RequestLoanQuotation => println("Req loan quotation")
      val sc = context.actorOf(Props[FailingActor])
      sc ! 12
  }

  override val supervisorStrategy = OneForOneStrategy(maxNrOfRetries = 5, withinTimeRange = 1 minute) {
    case _: NullPointerException => Restart
    case _: ArithmeticException => Resume
    case _: IllegalArgumentException =>
      println("****************ILLEGALEXCEPTION")
      Stop
    case _: UnsupportedOperationException => Stop
    case x: Exception =>
      println("EXCEPTION **************" + x)
      Escalate
  }
}

class FailingActor extends Actor{
  override def preStart(): Unit = {
    throw new IllegalArgumentException
  }

  override def receive: Receive = {
    case str:String =>
    case i:Int =>
  }
}