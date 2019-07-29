package actorEssentials

import akka.actor.{Actor, ActorRef, ActorSystem, Props}

import scala.language.postfixOps
import scala.util.Random
import scala.concurrent.duration._

object FirstActor {
  def main(args: Array[String]): Unit = {
    val actorSystem = ActorSystem("SulabhActors")
    val shoppingCart: ActorRef = actorSystem.actorOf(Props[ShoppingCart], "shoppingCart")
    // If invoked from an instance that is not an Actor the sender will be deadLetters actor reference by default.
    shoppingCart ! "msg"
    //shoppingCart ! "msg23"

    val scMngr = actorSystem.actorOf(Props[ShoppingCartManager])
    scMngr ! StartTask(10)        // sends message to shoppingcart
    scMngr ! ContinueTask(10)     // sends continuation message to shoppingcart
    scMngr ! BeAnother
    scMngr ! 12
    scMngr ! RollbackAnother
    scMngr ! BeAnother

    Thread.sleep(2000)

    scMngr ! "directly from Manager"

    //shoppingCart ! "msg230"

    Thread.sleep(10000)
    actorSystem stop scMngr
    actorSystem stop shoppingCart
  }

  case class StartTask(id: Int)
  case class ContinueTask(id: Int)


  case class BeAnother()
  case class RollbackAnother()

  class ShoppingCartManager extends Actor {
    var sc: ActorRef = _

    override def preStart(): Unit = {
      println("ShoppingCartManager PreStart")
      context.become(shoppingCartAdd)
    }

    def shoppingCartAdd: Receive = {
      case StartTask(id) =>
        println("Received StartTask Message")
        sc = context.actorOf(Props[ShoppingCart], id.toString)
        sc ! "*********Starting Task"
      case BeAnother =>
        println("Received BeAnother Message")
        context.become(another)
      case ContinueTask(id) => sc ! "*********continuation task"
    }

    def another: Receive = {
      case str:String =>
        println("Another here.....now offloading shoppingcart instance -> " + str)
        context.stop(sc)
      case i:Int =>
        println("Another here.......with integer -> " + i)
      case RollbackAnother =>
        println("Unbecoming...")
        context.unbecome()
        context.become(shoppingCartAdd)
    }

    override def receive: Receive = {
      case _ => println("ShoppingCartManager receive")
    }

    override def postStop(): Unit = {
      println("ShoppingCartManager PostStop")
    }
  }

  class ShoppingCart extends Actor {
    override def preStart(): Unit = {
      println("ShoppingCart PreStart")
      super.preStart()
    }

    override def postStop(): Unit = {
      println("ShoppingCart PostStop")
      super.postStop()
    }

    override def receive: Receive = {
      case str: String => println(s"ShoppingCart received --> $str , received from: ******${context.sender()}")
        val n = Random.nextInt(10)
        import context._
        val sender = context.sender()
        context.system.scheduler.scheduleOnce(1 second){
          println(s"$n*********** Sender : "+sender)
        }
        context.system.scheduler.scheduleOnce(1 second){
          println(s"$n*********** Sender'' : "+Option(context).map(_.sender()).getOrElse("NA"))
        }
    }
  }

}
