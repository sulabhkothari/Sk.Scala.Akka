package actorEssentials.testing

import akka.actor.{Actor, ActorSystem, Props}
import akka.testkit.{CallingThreadDispatcher, TestActorRef, TestProbe}
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

import scala.concurrent.duration.Duration

class SynchronousTestingSpec extends WordSpecLike with BeforeAndAfterAll {
  implicit val system = ActorSystem("SynchronousTestingSpec")

  override def afterAll(): Unit = {
    system.terminate()
  }

  import SynchronousTestingSpec._

  "A counter" should {
    "synchronously increase its counter" in {
      val counter = TestActorRef[Counter](Props[Counter])

      // This is a blocking call
      counter ! Increment // at this point counter has ALREADY received the message
      // because sending a message to a TestActorRef happens in the calling thread
      assert(counter.underlyingActor.count == 1)
    }

    "synchronously increase its counter at the call of the receive function" in {
      val counter = TestActorRef[Counter](Props[Counter])
      counter.receive(Increment)
      assert(counter.underlyingActor.count == 1)
    }

    "work on the calling thread dispatcher" in {
      val counter = system.actorOf(Props[Counter].withDispatcher(CallingThreadDispatcher.Id))
      val probe = TestProbe()
      probe.send(counter, Read)
      probe.expectMsg(Duration.Zero, 0) //probe has already received the message zero
    }
  }

}

object SynchronousTestingSpec {

  case object Increment

  case object Read

  class Counter extends Actor {
    var count = 0

    override def receive: Receive = {
      case Increment => count += 1
      case Read => sender() ! count
    }
  }

}