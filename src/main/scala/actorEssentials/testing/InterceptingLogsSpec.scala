package actorEssentials.testing

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.testkit.{EventFilter, ImplicitSender, TestKit}
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

class InterceptingLogsSpec extends
  // to intercept logger needs to be configured in application.conf
  TestKit(ActorSystem("InterceptingLogsSpec", ConfigFactory.load().getConfig("interceptingLogMessages")))
  with ImplicitSender
  with WordSpecLike
  with BeforeAndAfterAll {
  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  import InterceptingLogsSpec._

  "A checkout flow" must {
    "correctly log dispatch of an order" in {
      val item = "rockthejvmakkacourse"
      val creditCard = "1234-1234-1234-1234"

      // to intercept logger needs to be configured in application.conf
      // EventFilter intercept waits for logging of a message for a given time
      // leeway time can be increased in config
      EventFilter.info(pattern = s"Order: [0-9]+ for item: $item has been dispatched", occurrences = 1) intercept {
        val checkoutRef = system.actorOf(Props[CheckoutActor])
        checkoutRef ! Checkout(item, creditCard)
      }
    }

    "freak out if payment is denied" in {
      val item = "rockthejvmakkacourse"
      val creditCard = "01234-1234-1234-1234"

      EventFilter[RuntimeException](occurrences = 1) intercept  {
        val checkoutRef = system.actorOf(Props[CheckoutActor])
        checkoutRef ! Checkout(item, creditCard)
      }
    }
  }
}

object InterceptingLogsSpec {

  case class Checkout(item: String, creditCard: String)

  case class AuthorizeCard(creditCard: String)

  case object PaymentAccepted

  case object PaymentDenied

  case class DispatchOrder(item: String)

  case object OrderConfirmed

  class CheckoutActor extends Actor {
    private val paymentManager: ActorRef = context.actorOf(Props[PaymentManager])
    private val fulfillmentManager: ActorRef = context.actorOf(Props[FulfillmentManager])

    override def receive: Receive = awaitingCheckout

    def awaitingCheckout(): Receive = {
      case Checkout(item, card) =>
        paymentManager ! AuthorizeCard(card)
        context.become(pendingPayment(item))
    }

    def pendingPayment(item: String): Receive = {
      case PaymentAccepted =>
        fulfillmentManager ! DispatchOrder(item)
        context.become(pendingFulfillment(item))
      case PaymentDenied => throw new RuntimeException("I can't handle this anymore")
    }

    def pendingFulfillment(item: String): Receive = {
      case OrderConfirmed => context.become(awaitingCheckout)
    }
  }

  class PaymentManager extends Actor {
    override def receive: Receive = {
      case AuthorizeCard(card) =>
        Thread.sleep(4000)
        if (card.startsWith("0")) sender ! PaymentDenied else sender ! PaymentAccepted
    }
  }

  class FulfillmentManager extends Actor with ActorLogging {
    var orderId = 43

    override def receive: Receive = {
      case DispatchOrder(item) =>
        orderId += 1
        log.info(s"Order: $orderId for item: $item has been dispatched")
        sender ! OrderConfirmed
    }
  }

}