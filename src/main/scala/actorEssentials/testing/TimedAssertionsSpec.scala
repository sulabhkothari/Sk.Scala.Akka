package actorEssentials.testing

import akka.actor.{Actor, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

import scala.util.Random

class TimedAssertionsSpec extends
  TestKit(ActorSystem("TimedAssertionsSpec", ConfigFactory.load().getConfig("specialTimedAssertionsConfig")))
  with ImplicitSender with WordSpecLike with BeforeAndAfterAll {

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  import TimedAssertionsSpec._

  "A worker actor" should {
    val workerActor = system.actorOf(Props[WorkerActor])

    "reply with the meaning of life in a timely manner" in {
      // It will get "assertion failed: block took 6.104 milliseconds, should at least have been 500 milliseconds"
      //    if Thread.sleep is removed in case "work"
      import scala.concurrent.duration._
      within(500 millis, 1 second) {
        workerActor ! "work"
        expectMsg(WorkResult(42))
      }
    }

    "reply with valid work at a reasonable pace" in {
      import scala.concurrent.duration._
      within(1 second) {
        workerActor ! "workSequence"
        // Get 10 messages within 2 seconds at most 500 milliseconds apart
        val results: Seq[Int] = receiveWhile[Int](2 second, 500 millis, 10) {
          case WorkResult(result) => result
        }

        assert(results.sum > 5)
      }
    }

    // This test fails because of below mentioned reason
    "reply to a test probe in a timely manner" in {
      import scala.concurrent.duration._
      within(1 second) {
        val probe = TestProbe()
        probe.send(workerActor, "work")
        probe.expectMsg(WorkResult(42)) // timeout of 0.3s set from application.config because within block does not apply to TestProbe
      }
    }
  }
}

object TimedAssertionsSpec {

  class WorkerActor extends Actor {
    override def receive: Receive = {
      case "work" =>
        // long computation
        Thread.sleep(500)
        sender() ! WorkResult(42)
      case "workSequence" =>
        var r = new Random()
        for (_ <- 1 to 10) {
          Thread.sleep(r.nextInt(50))
          sender() ! WorkResult(1)
        }
    }
  }

  case class WorkResult(result: Int)

}