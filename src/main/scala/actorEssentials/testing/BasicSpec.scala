package actorEssentials.testing

import actorEssentials.testing.BasicSpec.{BlackHoleActor, EchoActor, LabTestActor}
import akka.actor.{Actor, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

import scala.util.Random

class BasicSpec extends TestKit(ActorSystem("BasicSpec"))
  with ImplicitSender
  with WordSpecLike
  with BeforeAndAfterAll {

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system) //system is a member of TestKit
  }

  // testActor is the actor on which we expect messages. ImplicitSender passes testActor as the implicit sender of
  // every single message
  "EchoActor" should {
    "send back same message" in {
      val actor = system.actorOf(Props[EchoActor], "echo")
      val message = "hello test"
      actor ! message
      expectMsg(message)  //akka.test.single-expect-default
    }
  }

  "BlackHoleActor" should {
    "send back some message" in {
      val actor = system.actorOf(Props[BlackHoleActor], "blackhole")
      val message = "hello test"
      actor ! message
      import scala.concurrent.duration._
      expectNoMessage(1 second)
    }
  }

  "A LabTestActor" should {
    val actor = system.actorOf(Props[LabTestActor], "labtestactor")
    "turn the string into upper case" in {
      val message = "hello test"
      actor ! message
      //expectMsg(message.toUpperCase)

      val reply = expectMsgType[String]
      reply equals message.toUpperCase
    }

    "reply to a greeting" in {
      actor ! "greetings"
      expectMsgAnyOf("hi", "hello")
    }

    "reply to favourite tech" in {
      actor ! "favouriteTech"
      expectMsgAllOf("scala", "akka")
    }

    "reply to cool tech in a different way" in {
      actor ! "favouriteTech"
      val messages = receiveN(2) //Seq[Any]
      //free to do more complicated assertions
    }

    "reply to cool tech in a fancy way" in {
      actor ! "favouriteTech"
      import scala.concurrent.duration._
      expectMsgPF(){
        case "scala" => true //only care that PF is defined
        case "akka" => true
      }
    }
    }
}

object BasicSpec {

  class EchoActor extends Actor {
    override def receive: Receive = {
      case message => sender ! message
    }
  }

  class BlackHoleActor extends Actor {
    override def receive: Receive = Actor.emptyBehavior
  }

  class LabTestActor extends Actor {
    val random = new Random()
    override def receive: Receive = {
      case "greetings" =>
        if(random.nextBoolean()) sender ! "hi" else sender ! "hello"
      case "favouriteTech" =>
        sender ! "scala"
        sender ! "akka"
      case message: String => sender ! message.toUpperCase
    }
  }
}