package actorEssentials.FaultTolerance

import akka.actor.SupervisorStrategy.{Escalate, Restart, Resume, Stop}
import akka.actor.{Actor, ActorRef, ActorSystem, AllForOneStrategy, OneForOneStrategy, Props, SupervisorStrategy, Terminated}
import akka.testkit.{EventFilter, ImplicitSender, TestKit}
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

class SupervisionSpec extends TestKit(ActorSystem("SupervisionSpec"))
  with ImplicitSender with WordSpecLike with BeforeAndAfterAll {
  override def afterAll(): Unit = TestKit.shutdownActorSystem(system)

  import SupervisionSpec._

  "a supervisor" should {
    "resume its child in case of a minor fault" in {
      val supervisor = system.actorOf(Props[Supervisor])
      supervisor ! Props[FussyWordCounter]
      val child = expectMsgType[ActorRef]

      child ! "I love Akka"
      child ! Report
      expectMsg(3)

      child ! "Akka is awesome because I am learning to think in a whole new way"
      child ! Report
      expectMsg(3)
    }

    "restart its child in case of empty string" in {
      val supervisor = system.actorOf(Props[Supervisor])
      supervisor ! Props[FussyWordCounter]
      val child = expectMsgType[ActorRef]

      child ! "I love Akka"
      child ! Report
      expectMsg(3)

      child ! ""
      child ! Report
      expectMsg(0) // Actor state is cleared because Actor instance is swapped (new instance is created on restart)
    }

    "terminate its child in case of a major error" in {
      val supervisor = system.actorOf(Props[Supervisor])
      supervisor ! Props[FussyWordCounter]
      val child = expectMsgType[ActorRef]

      watch(child)

      child ! "akka is nice"
      val terminatedMsg = expectMsgType[Terminated]
      assert(terminatedMsg.actor == child)
    }

    "escalate an error when it doesn't know what to do" in {
      val supervisor = system.actorOf(Props[Supervisor], "supervisor")
      supervisor ! Props[FussyWordCounter]
      val child = expectMsgType[ActorRef]

      watch(child)

      child ! 10
      val terminatedMsg = expectMsgType[Terminated]
      assert(terminatedMsg.actor == child) //Before escalation it stops all its children
    }
  }
  "A Kinder supervisor" should {
    "not kill children in case its restarted or escalates failures" in {
      val supervisor = system.actorOf(Props[NoDeathOnRestartSupervisor], "nodeath-supervisor")
      supervisor ! Props[FussyWordCounter]
      val child = expectMsgType[ActorRef]

      child ! "Akka is cool"
      child ! Report

      expectMsg(3)

      child ! 45
      child ! Report
      expectMsg(0)
    }
  }

  "An all-for-one supervisor" should {
    "apply the all-for-one strategy" in {
      val supervisor = system.actorOf(Props[AllForOneSupervisor], "allforone-supervisor")
      supervisor ! Props[FussyWordCounter]
      val child = expectMsgType[ActorRef]

      supervisor ! Props[FussyWordCounter]
      val secondChild = expectMsgType[ActorRef]

      secondChild ! "Testing Supervision"
      secondChild ! Report
      expectMsg(2)

      EventFilter[NullPointerException]() intercept {
        child ! ""
      }

      Thread.sleep(500)
      secondChild ! Report
      expectMsg(0)
    }
  }
}

object SupervisionSpec {

  case object Report

  class AllForOneSupervisor extends Actor {
    override def receive: Receive = {
      case props: Props =>
        val childRef = context.actorOf(props)
        sender ! childRef
    }

    // AllForOneStrategy applies same strategy to all children irrespective of which one caused the failure
    override val supervisorStrategy = AllForOneStrategy(){
      case _: NullPointerException => Restart
      case _: IllegalArgumentException => Stop
      case _: RuntimeException => Resume // (actor is resumed from suspension) Internal State of the actor is kept
      case _: Exception => Escalate //Stops all its children and then escalate error to its parent
    }
  }
  class NoDeathOnRestartSupervisor extends Supervisor {
    // Escalates to the user guardian in case of exception. User Guardian will restart this supervisor but it would
    // have killed its children on Restart, in preRestart, but since its empty the children still live. User guradian
    // restarts everything so the children restart and not killed and so their state is not retained.
    override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
      //empty
    }
  }

  class Supervisor extends Actor {
    override def receive: Receive = {
      case props: Props =>
        val childRef = context.actorOf(props)
        sender ! childRef
    }

    // OneForOneStrategy applies the strategy to children which caused the failure
    override val supervisorStrategy = OneForOneStrategy() {
      case _: NullPointerException => Restart
      case _: IllegalArgumentException => Stop
      case _: RuntimeException => Resume // (actor is resumed from suspension) Internal State of the actor is kept
      case _: Exception => Escalate //Stops all its children and then escalate error to its parent
    }
  }

  class FussyWordCounter extends Actor {
    var words = 0

    override def receive: Receive = {
      case "" => throw new NullPointerException("sentence is empty")
      case sentence: String if (sentence.length > 20) => throw new RuntimeException("sentence is too big")
      case sentence: String if (!Character.isUpperCase(sentence(0))) => throw new IllegalArgumentException("sentence must start with upper case")
      case sentence: String => words += sentence.split(" ").length
      case Report => sender ! words
      case _ => throw new Exception("can only receive strings")
    }
  }

}