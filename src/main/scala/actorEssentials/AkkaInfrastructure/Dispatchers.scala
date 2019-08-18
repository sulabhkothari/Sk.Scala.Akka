package actorEssentials.AkkaInfrastructure

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import com.typesafe.config.ConfigFactory

import scala.concurrent.Future
import scala.util.Random

object Dispatchers extends App {

  class Counter extends Actor with ActorLogging {
    var count = 0

    override def receive: Receive = {
      case message =>
        count += 1
        log.info(s"[$count] $message")

    }
  }

  // method #1 - programmatic/in code
  val system = ActorSystem("DispatcherDemo")

  //  Dispatcher will only schedule an actor on a thread for 30 times in a row, before scheduling another actor. This is because
  //  the throughput is set to 30 in configuration

  //  val simpleCounterActor = system.actorOf(Props[Counter].withDispatcher("my-dispatcher"))
  //  val actors = for (i <- 1 to 10) yield system.actorOf(Props[Counter].withDispatcher("my-dispatcher"), s"counter_$i")
  //  val random = new Random()
  //  for (i <- 1 to 1000) {
  //    actors(random.nextInt(10)) ! i
  //  }

  //  Use dispatcher with just 1 thread
  //  val actors = for (i <- 1 to 10) yield system.actorOf(Props[Counter].withDispatcher("my-dispatcher-1thread"), s"counter_$i")
  //  for (i <- 1 to 999) {
  //    actors(i / 100) ! i
  //  }

  // method #2 - from config
  //val system2 = ActorSystem("DispatcherDemo", ConfigFactory.load().getConfig("dispatchersDemo"))
  //val simpleCounterActor = system2.actorOf(Props[Counter], "rtjvm")

  /**
    * Dispatchers implement the ExecutionContext trait
    */

  class DBActor extends Actor with ActorLogging {

    // With this all actors share the same execution context, so they share same thread pool and threads. If some operation
    // is long/blocking like a database call, which is simulated here using a Future with Thread.sleep(5000), then, threads of
    // the same execution context execute these blocking calls, because of which scheduling of actors will experience
    // latency. That is, these calls will starve the context dispatcher of running threads, which are otherwise used for
    // handling messages. The dispatcher is limited and with increased load might be occupied serving messages to actors,
    // instead of serving your futures, or the other way round, you may starve the delivery of messages to actors.
    //import system.dispatcher

    // Solution #1
    // To solve the above issue use a dedicated dispatcher (a separate execution context) for blocking calls as below
    implicit val executionContext = context.system.dispatchers.lookup("my-dispatcher")

    // Solution #2
    // Use Router

    /**
      *
      * Different types of Dispatchers
      * type = Dispatcher (in configuration) is most commonly used dispatcher, and its based on an executor service
      * which binds an actor to a thread pool
      *
      * PinnedDispatcher: which binds each actor to a thread pool with exactly one thread and those threads might circle
      * around
      *
      * CallingThreadDispatcher: ensures that all invocations in all communications with an actor happen on the calling
      * thread, whatever that thread is.
      */

    override def receive: Receive = {
      case msg =>
        // This is discouraged, we are using it to simulate blocking calls
        Future {
        Thread.sleep(5000)
        log.info(s"Success: $msg")
      }
    }
  }

  val dbActor = system.actorOf(Props[DBActor])
  dbActor ! "the meaning of life is 42"

  val nonBlockingActor = system.actorOf(Props[Counter])
  for (i <- 1 to 1000) {
    val message = s"important message: $i"
    dbActor ! message
    nonBlockingActor ! message
  }

  // Instead of this use a dedicated dispatcher for blocking I/O calls
}
