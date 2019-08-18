package actorEssentials.FaultTolerance

import akka.actor.SupervisorStrategy.{Resume, Stop}
import akka.actor.{Actor, ActorLogging, ActorSystem, OneForOneStrategy, Props}
import akka.pattern.{Backoff, BackoffOpts, BackoffSupervisor}

import scala.io.Source

object BackoffSupervisorPattern extends App {

  case object ReadFile

  class FileBasedPersistentActor extends Actor with ActorLogging {
    var datasource: Source = null

    override def preStart(): Unit = log.info("Persistent actor starting")

    override def postStop(): Unit = log.warning("Persistent actor has stopped")

    override def preRestart(reason: Throwable, message: Option[Any]): Unit = log.warning("Persistent actor restarting")

    override def receive: Receive = {
      case ReadFile if (datasource == null) =>
        datasource = Source.fromFile("src/main/resources/testfiles/important-data.txt")
        log.info("I've just read some important data: " + datasource.getLines().toList)
    }
  }

  val system = ActorSystem("Backoff-Supervisor-Demo")
  //val simpleActor = system.actorOf(Props[FileBasedPersistentActor])
  //simpleActor ! ReadFile

  import scala.concurrent.duration._

  val simpleSupervisorProps = BackoffSupervisor.props(BackoffOpts.onFailure(
    Props[FileBasedPersistentActor],
    "simpleBackoffActor",
    3 seconds, // then 6s,12s,24s
    30 seconds, // cap is 30s
    0.2 //noise (randomness) so that we don't have huge number of actors starting at the same time in
    // scenarios like database down
  ))
  //    BackoffSupervisor.props(
  //    Backoff.onFailure(
  //      Props[FileBasedPersistentActor],
  //      "simpleBackoffActor",
  //      3 seconds,
  //      30 seconds,
  //      0.2
  //    )

  //val simpleBackoffSupervisor = system.actorOf(simpleSupervisorProps, "SimpleSupervisor")
  /* Simple Supervisor
      - child called simpleBackoffActor (props of type FileBasedPersistentActor)
      - supervision strategy is the default one (restarting on everything)
        - first attempt after 3 seconds
        - next attempt is 2x the previous attempt
   */

  //simpleBackoffSupervisor ! ReadFile


  val stopSupervisorProps = BackoffSupervisor.props(BackoffOpts.onStop(
    Props[FileBasedPersistentActor],
    "stopBackoffActor",
    4 seconds,
    30 seconds,
    0.2
  ).withSupervisorStrategy(OneForOneStrategy() {
    case _ => Stop
  }))

  //val stopBackoffSupervisor = system.actorOf(stopSupervisorProps, "StopSupervisor")
  //stopBackoffSupervisor ! ReadFile

  class EagerFileBasedPersistentActor extends FileBasedPersistentActor {
    override def preStart(): Unit = {
      log.info("Eager Actor Starting")
      datasource = Source.fromFile("src/main/resources/testfiles/important-data.txt")
    }
  }

  val eagerActor = system.actorOf(Props[EagerFileBasedPersistentActor], "EagerFileBasedPersistentActor")
  // for ActorInitializationException, default supervisor strategy is to Stop that's why actor in this case is not started again

  val repeatedSupervisorProps = BackoffSupervisor.props(BackoffOpts.onStop(
    Props[EagerFileBasedPersistentActor],
    "eagerBackoffActor",
    1 seconds,
    30 seconds,
    0.1
  ))

  val repeatedSupervisor = system.actorOf(repeatedSupervisorProps, "repeatedSupervisor")
  /* eager supervisor
    - child eagerActor
      - will die on start with ActorInitializationException
      - trigger the supervision strategy in eagerSupervisor => STOP eagerActor
    - backoff will kick in after 1 second, 2s, 4s, 8s, 16s
   */
}
