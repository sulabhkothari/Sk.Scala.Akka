package actorEssentials.AkkaInfrastructure

import akka.actor.{Actor, ActorLogging, ActorSystem, Kill, PoisonPill, Props, Terminated}
import akka.routing.{ActorRefRoutee, Broadcast, FromConfig, RoundRobinGroup, RoundRobinPool, RoundRobinRoutingLogic, Router}
import com.typesafe.config.ConfigFactory

object Routers extends App {

  /*
   * #1 - Manual router
   */
  class Master extends Actor {
    // Step 1 - create routees
    // 5 actor routee based off slave routees
    private val slaves = for (i <- 1 to 5) yield {
      val slave = context.actorOf(Props[Slave], s"slave_$i")
      context.watch(slave)

      ActorRefRoutee(slave) //TODO
    }

    // Step 2 Define router
    private var router = Router(RoundRobinRoutingLogic(), slaves)

    // Step 3 - route the message
    // Step 4 - Handle the termination/lifecycle of the routees
    override def receive: Receive = {
      case Terminated(ref) =>
        router = router.removeRoutee(ref)
        val newSlave = context.actorOf(Props[Slave])
        context.watch(newSlave)
        router = router.addRoutee(newSlave)
      case msg => router.route(msg, sender())
    }
  }

  class Slave extends Actor with ActorLogging {
    override def receive: Receive = {
      case msg => log.info(msg.toString)
    }

  }

  val system = ActorSystem("Routers", ConfigFactory.load().getConfig("routersDemo"))

  val master = system.actorOf(Props[Master], "master")
  //  for (i <- 1 to 10) {
  //    master ! s"[$i] Hello from the world"
  //  }

  /**
    * Method #2 - a router actor with its own children
    * POOL router
    */

  // 2.1 Programmatically (in code)
  // RoundRobinPool(5).props(Props[Slave] is an actor that creates 5 slaves
  //  val poolMaster = system.actorOf(RoundRobinPool(5).props(Props[Slave]), "simplePoolMaster")
  //
  //  for (i <- 1 to 10) {
  //    poolMaster ! s"[$i] Hello from the world"
  //  }

  // 2.2 from configuration
  //  val poolMaster2 = system.actorOf(FromConfig.props(Props[Slave]), "poolMaster2")
  //  for (i <- 1 to 10) {
  //    poolMaster2 ! s"[$i] Hello from the world"
  //  }

  /**
    * Method #3 - router with actors created elsewhere
    * GROUP router
    */
  // .. in another part of my application
  val slaveList = (1 to 5).map(i => system.actorOf(Props[Slave], s"slave_$i")).toList

  // need their paths
  val slavePaths = slaveList.map(_.path.toString)

  // 3.1 in the code
  //  val groupMaster = system.actorOf(RoundRobinGroup(slavePaths).props)
  //  for (i <- 1 to 10) {
  //    groupMaster ! s"[$i] Hello from the world"
  //  }

  // 3.2 from configuration
  val groupMaster2 = system.actorOf(FromConfig.props(), "groupMaster2")
  for (i <- 1 to 10) {
    groupMaster2 ! s"[$i] Hello from the world"
  }

  /**
    * Special Messages
    */
  groupMaster2 ! Broadcast("hello, everyone!")

  // PoisonKill and Kill are NOT routed
  // AddRoutees, RemoveRoutees, Get handled only by the routing actor

  //groupMaster2 ! Kill

  //slaveList(2) ! PoisonPill
  //Thread.sleep(1000)
  //groupMaster2 ! Broadcast("hello, everyone!")

}
