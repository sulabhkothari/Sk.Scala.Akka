package actorEssentials.AkkaInfrastructure

import akka.actor.{Actor, ActorLogging, ActorSystem, PoisonPill, Props}
import akka.dispatch.{ControlMessage, PriorityGenerator, UnboundedPriorityMailbox}
import com.typesafe.config.{Config, ConfigFactory}

object Mailboxes extends App {
  val system = ActorSystem("MailboxDemo", ConfigFactory.load().getConfig("mailboxesDemo"))

  class SimpleActor extends Actor with ActorLogging {
    override def receive: Receive = {
      case message =>
        log.info(message.toString)
    }
  }

  /**
    * Interesting case #1 - custom priority mailbox
    * P0 - most important
    * P1
    * P2
    * P3
    */

  // Step 1: Mailbox Definition
  // Step 2: Make it known in the config
  // Step 3: Attach the dispatcher to the actor
  class SupportTicketPriorityMailbox(settings: ActorSystem.Settings, config: Config)
    extends UnboundedPriorityMailbox(PriorityGenerator {
      case message: String if (message.startsWith("[P0]")) => 0 //Lower means higher priority
      case message: String if (message.startsWith("[P1]")) => 1
      case message: String if (message.startsWith("[P2]")) => 2
      case message: String if (message.startsWith("[P3]")) => 3
      case _ => 4
    })

  //  println(classOf[SupportTicketPriorityMailbox])
  //  val supportTicketLogger = system.actorOf(Props[SimpleActor].withDispatcher("support-ticket-dispatcher"))
  //
  //  // supportTicketLogger ! PoisonPill
  //  // Thread.sleep(1000)
  //  supportTicketLogger ! "[P3] - this thing would be nice to have"
  //  supportTicketLogger ! "[P0] - this thing needs to be solved now"
  //  Thread.sleep(10)
  //  supportTicketLogger ! "[P1] - do this when you have the time"
  // after which time can I send another message and be prioritized accordingly?
  // Answer is neither do you know, nor can you configure the wait

  /**
    * Interesting case #2 - control-aware mailbox
    * we'll use UnboundedControlAwareMailbox
    */

  // Step 1 - mark important messages as control messages
  case object ManagementTicket extends ControlMessage

  // Step 2 - configure who gets the mailbox
  // make the actor attach to mailbox
  // Method #1
  //  val controlAwareActor = system.actorOf(Props[SimpleActor].withMailbox("control-mailbox"))
  //  controlAwareActor ! "[P3] - this thing would be nice to have"
  //  controlAwareActor ! "[P0] - this thing needs to be solved now"
  //  controlAwareActor ! "[P1] - do this when you have the time"
  //  controlAwareActor ! ManagementTicket

  // Method #2 using deployment config
  val altControlAwareActor = system.actorOf(Props[SimpleActor], "altControlAwareActor")
    //, "altControlAwareActor")
  altControlAwareActor ! "[P3] - this thing would be nice to have"
  altControlAwareActor ! "[P0] - this thing needs to be solved now"
  altControlAwareActor ! "[P1] - do this when you have the time"
  altControlAwareActor ! ManagementTicket
}
