package actorEssentials

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import com.typesafe.config.ConfigFactory

object IntroToAkkaConfiguration extends App {

  // Inline Configuration
  val configString =
    """
      | akka {
      |   loglevel = "DEBUG"
      | }
    """.stripMargin

  val config = ConfigFactory.parseString(configString)
  val system = ActorSystem("ConfigurationDemo", ConfigFactory.load(config))
  val actor = system.actorOf(Props[ActorWithLogging], "config-logging")
  actor ! "A message to remember"

  // Default configuration from file system
  // Actor system looks for application.conf in resources directory
  val defaultConfigFileSystem = ActorSystem("DefaultConfigFileDemo")
  val defaultConfigActor = defaultConfigFileSystem.actorOf(Props[ActorWithLogging], "defaultConfigActor")
  defaultConfigActor ! "Remember me"

  // Separate Configuration in the same file
  val specialConfig = ConfigFactory.load().getConfig("mySpecialConfig")
  val specialConfigSystem = ActorSystem("specialConfigSystem", specialConfig)
  val specialConfigActor = specialConfigSystem.actorOf(Props[ActorWithLogging], "specialConfigActor")
  specialConfigActor ! "Remember me, I am special"

  // Separate Configuration in another file
  val separateConfig = ConfigFactory.load("secretFolder/secretConfiguration.conf")
  val separateConfigSystem = ActorSystem("separateConfigSystem", separateConfig)
  val separateConfigActor = separateConfigSystem.actorOf(Props[ActorWithLogging], "separateConfigActor")
  separateConfigActor ! "Separate Config"

  println(s"separate config loglevel: ${separateConfig.getString("akka.loglevel")}")

  // JSON format for config file
  val jsonConfig = ConfigFactory.load("json/jsonConfig.json")
  val jsonConfigSystem = ActorSystem("jsonConfigSystem", jsonConfig)
  val jsonConfigActor = jsonConfigSystem.actorOf(Props[ActorWithLogging], "jsonConfigActor")
  jsonConfigActor ! "Json Config"
  println(s"json config loglevel: ${jsonConfig.getString("akka.loglevel")}")

  // Properties format for config file
  val propsConfig = ConfigFactory.load("props/propsConfiguration.properties")
  val propsConfigSystem = ActorSystem("propsConfigSystem", propsConfig)
  val propsConfigActor = propsConfigSystem.actorOf(Props[ActorWithLogging], "propsConfigActor")
  propsConfigActor ! "Properties Config"
  println(s"properties config loglevel: ${propsConfig.getString("akka.loglevel")}")
  println(s"properties config simpleProperty: ${propsConfig.getString("my.simpleProperty")}")

  class ActorWithLogging extends Actor with ActorLogging {
    override def receive: Receive = {
      case message => log.info(message.toString)
    }
  }

}
