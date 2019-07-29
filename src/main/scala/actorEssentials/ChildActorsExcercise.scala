package actorEssentials

import actorEssentials.ChildActorsExcercise.WordCounterMaster.{Initialize, WordCountReply, WordCountTask}
import akka.actor.{Actor, ActorRef, ActorSystem, Props}

object ChildActorsExcercise extends App {
  val system = ActorSystem("Parent-Child")
  val master = system.actorOf(Props[WordCounterMaster], "master")
  master ! Initialize(10)
  master ! WordCountTask(text = "A quick brown fox jumped over the fence")
  master ! WordCountTask(text = "Mahatama Gandhi")
  master ! WordCountTask(text = "Some men are not leaders")
  master ! WordCountTask(text = "Rocket science")
  master ! WordCountTask(text = "Falcon Heavy successfully tested for recovery")
  master ! WordCountTask(text = "Modi on Man vs Wild")
  master ! WordCountTask(text = "Done for the day")
  master ! WordCountTask(text = "Diplomacy")
  master ! WordCountTask(text = "International Law")
  master ! WordCountTask(text = "Kashmir a bilateral issue")
  master ! WordCountTask(text = "Aksai-Chin")


  object WordCounterMaster {

    case class Initialize(nChildren: Int)

    case class WordCountTask(id: Int = 0, text: String)

    case class WordCountReply(id: Int, count: Int)

  }

  class WordCounterMaster extends Actor {

    import WordCounterMaster._

    override def receive: Receive = initialize

    def initialize: Receive = {
      case Initialize(n) =>
        println(s"Creating $n workers")
        context.become(executeTask((1 to n).map(x => context.actorOf(Props[WordCounterWorker], x.toString)),
          Map(), 0, 1))
    }

    def executeTask(workers: Seq[ActorRef], tasks: Map[Int, String], currentActorRef: Int, currentTaskId: Int): Receive = {
      case WordCountTask(id, text) =>
        println(s"Count words for ${text}")
        workers(currentActorRef % workers.length) ! WordCountTask(currentTaskId, text)
        context.become(executeTask(workers, tasks + (currentTaskId -> text), currentActorRef + 1,
          currentTaskId + 1))
      case WordCountReply(id, count) =>
        println(s"Word count for ${tasks.get(id)} : $count")
        context.become(executeTask(sender +: workers, tasks, currentActorRef, currentTaskId))
      //Method name ending with : expects calling object on the right
    }
  }

  class WordCounterWorker extends Actor {
    override def receive: Receive = {
      case WordCountTask(id, text) =>
        println(s"Task picked up by ${self.path}")
        sender ! WordCountReply(id, text.split(" ").length)
    }
  }

}
