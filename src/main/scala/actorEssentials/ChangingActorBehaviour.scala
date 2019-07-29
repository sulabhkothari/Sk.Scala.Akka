package actorEssentials

import actorEssentials.ChangingActorBehaviour.Mom.MomStart
import akka.actor.{Actor, ActorRef, ActorSystem, Props}

object ChangingActorBehaviour {
  def main(args: Array[String]): Unit = {
    val actorSystem = ActorSystem("ChangingActorBehaviour")
    val kid = actorSystem.actorOf(Props[FussyKid], "kidActor")
    val statelessKid = actorSystem.actorOf(Props[StatelessFussyKid], "statelessKidActor")
    val mom = actorSystem.actorOf(Props[Mom], "momActor")
    //    mom ! MomStart(kid)
    //    Thread.sleep(1000)
    //    mom ! MomStart(statelessKid)
    //    println("=============================================================")
    //    mom ! MomStart(kid)
    //    mom ! MomStart(statelessKid)

    mom ! MomStart(statelessKid)
  }

  class StatelessFussyKid extends Actor {

    import FussyKid._
    import Mom._

    override def receive: Receive = happyReceive


    def happyReceive: Receive = {
      case Food(VEGETABLE) => context.become(sadReceive, false)
      case Food(CHOCOLATE) =>
      case Ask(_) => sender ! KidAccept
    }

    def sadReceive: Receive = {
      case Food(VEGETABLE) => context.become(sadReceive, false)
      case Food(CHOCOLATE) => context.unbecome()
      case Ask(_) => sender ! KidReject
    }
  }

  object FussyKid {

    case class KidAccept()

    case class KidReject()

    val HAPPY = "happy"
    val SAD = "sad"
  }

  class FussyKid extends Actor {

    import ChangingActorBehaviour.Mom._
    import FussyKid._

    var state = HAPPY

    override def receive: Receive = {
      case Food(VEGETABLE) => state = SAD
      case Food(CHOCOLATE) => state = HAPPY
      case Ask(_) =>
        if (state == HAPPY) sender ! KidAccept
        else sender ! KidReject
    }
  }

  object Mom {
    val VEGETABLE = "vegetable"
    val CHOCOLATE = "chocolate"

    case class MomStart(kidRef: ActorRef)

    case class Food(food: String)

    case class Ask(message: String)

  }

  class Mom extends Actor {

    import ChangingActorBehaviour.FussyKid.{KidAccept, KidReject}
    import Mom._

    override def receive: Receive = {
      case MomStart(kidRef) =>
        kidRef ! Food(VEGETABLE)
        kidRef ! Ask("Do you want to play?")
        kidRef ! Food(VEGETABLE)
        kidRef ! Ask("Do you want to play?")
        kidRef ! Food(CHOCOLATE)
        kidRef ! Ask("Do you want to play?")
        kidRef ! Food(CHOCOLATE)
        kidRef ! Ask("Do you want to play?")
      case KidAccept => println("Kid Accepted!!")
      case KidReject => println("Kid Rejected!!")
    }
  }

}
