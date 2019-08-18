package actorEssentials.AkkaPatterns

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Cancellable, FSM, Props}
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.{BeforeAndAfterAll, OneInstancePerTest, WordSpecLike}

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext

class FiniteStateMachineSpec extends TestKit(ActorSystem())
  with ImplicitSender with WordSpecLike with BeforeAndAfterAll with OneInstancePerTest {
  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  import FiniteStateMachineSpec._

  "A vending machine" should {
    runTestSuite(Props[VendingMachine])
  }

  "A vending machine FSM" should {
    runTestSuite(Props[VendingMachineFSM])
  }

  def runTestSuite(props: Props) = {
    "error when not initialized" in {
      val vendingMachine = system.actorOf(props)
      vendingMachine ! RequestProduct("coke")
      expectMsg(VendorError("MachineNotInitialized"))
    }
    "report a product not available" in {
      val vendingMachine = system.actorOf(props)
      vendingMachine ! Initialize(Map("coke" -> 12), Map("coke" -> 20))
      vendingMachine ! RequestProduct("pepsi")
      expectMsg(VendorError("ProductNotAvailable"))
    }
    "throw a timeout if I don't insert money" in {
      val vendingMachine = system.actorOf(props)
      vendingMachine ! Initialize(Map("coke" -> 12), Map("coke" -> 20))
      vendingMachine ! RequestProduct("coke")
      expectMsg(Instruction("Please insert 20 dollars"))
      within(1.5 seconds) {
        expectMsg(VendorError("RequestTimedOut"))
      }
    }

    "handle reception of partial money" in {
      val vendingMachine = system.actorOf(props)
      vendingMachine ! Initialize(Map("coke" -> 12), Map("coke" -> 3))
      vendingMachine ! RequestProduct("coke")
      expectMsg(Instruction("Please insert 3 dollars"))

      vendingMachine ! ReceiveMoney(1)
      expectMsg(Instruction("Please insert 2 dollars"))

      within(1.5 seconds) {
        expectMsg(VendorError("RequestTimedOut"))
        expectMsg(GiveBackChange(1))
      }
    }

    "deliver the product if I insert all the money" in {
      val vendingMachine = system.actorOf(props)
      vendingMachine ! Initialize(Map("coke" -> 12), Map("coke" -> 3))
      vendingMachine ! RequestProduct("coke")
      expectMsg(Instruction("Please insert 3 dollars"))

      vendingMachine ! ReceiveMoney(3)
      expectMsg(Deliver("coke"))
    }

    "should give back change and be able to request money for a new product" in {
      val vendingMachine = system.actorOf(props)
      vendingMachine ! Initialize(Map("coke" -> 12), Map("coke" -> 3))
      vendingMachine ! RequestProduct("coke")
      expectMsg(Instruction("Please insert 3 dollars"))

      vendingMachine ! ReceiveMoney(30)
      expectMsg(Deliver("coke"))
      expectMsg(GiveBackChange(27))

      vendingMachine ! RequestProduct("coke")
      expectMsg(Instruction("Please insert 3 dollars"))
    }
  }
}

object FiniteStateMachineSpec {

  case class Initialize(inventory: Map[String, Int], price: Map[String, Int]) extends VendingData

  case class RequestProduct(product: String)

  case class Instruction(instruction: String)

  case class ReceiveMoney(amount: Int)

  case class Deliver(product: String)

  case class GiveBackChange(amount: Int)

  case class VendorError(reason: String)

  case object ReceiveMoneyTimeout

  class VendingMachine extends Actor with ActorLogging {
    override def receive: Receive = idle

    def waitForMoney(inventory: Map[String, Int], prices: Map[String, Int],
                     product: String, money: Int,
                     moneyTimeoutSchedule: Cancellable,
                     requestor: ActorRef): Receive = {
      case ReceiveMoneyTimeout =>
        requestor ! VendorError("RequestTimedOut")
        if (money > 0) requestor ! GiveBackChange(money)
        context.become(operational(inventory, prices))
      case ReceiveMoney(amount) =>
        moneyTimeoutSchedule.cancel()
        val price = prices(product)
        if (price <= amount + money) {
          requestor ! Deliver(product)
          if (amount + money - price > 0) requestor ! GiveBackChange(amount + money - price)
          val newStock = inventory(product) - 1
          val newInventory = inventory + (product -> newStock)
          context.become(operational(newInventory, prices))
        }
        else {
          val remainingMoney = price - (amount + money)
          requestor ! Instruction(s"Please insert $remainingMoney dollars")
          context.become(waitForMoney(inventory, prices, product, amount + money, startReceiveMoneyTimeoutSchedule, requestor))
        }
    }

    import scala.concurrent.duration._

    implicit val executionContext: ExecutionContext = context.dispatcher

    def startReceiveMoneyTimeoutSchedule(): Cancellable = context.system.scheduler.scheduleOnce(1 second) {
      self ! ReceiveMoneyTimeout
    }

    def operational(inventory: Map[String, Int], prices: Map[String, Int]): Receive = {
      case RequestProduct(product) => inventory.get(product) match {
        case None | Some(0) =>
          sender() ! VendorError("ProductNotAvailable")
        case Some(_) =>
          sender ! Instruction(s"Please insert ${prices(product)} dollars")
          context.become(waitForMoney(inventory, prices, product, 0, startReceiveMoneyTimeoutSchedule(), sender))
      }
    }

    def idle: Receive = {
      case Initialize(inventory, prices) => context.become(operational(inventory, prices))
      case _ => sender ! VendorError("MachineNotInitialized")
    }
  }

  // Step 1 - define the states and the data
  trait VendingState
  case object Idle extends VendingState
  case object Operational extends VendingState
  case object WaitingForMoney extends VendingState

  trait VendingData
  case object Uninitialized extends VendingData
  case class WaitForMoneyData(inventory: Map[String, Int], prices: Map[String, Int],
                              product: String, money: Int,
                              requestor: ActorRef) extends VendingData

  class VendingMachineFSM extends FSM[VendingState, VendingData] {
    // we don't have a receive handler

    // an EVENT(message, data)

    startWith(Idle, Uninitialized)

    when(Idle) {
      case Event(Initialize(inventory, prices), Uninitialized) =>
        goto(Operational) using Initialize(inventory, prices)
      case _ =>
        sender ! VendorError("MachineNotInitialized")
        stay()
    }

    when(Operational) {
      case Event(RequestProduct(product), Initialize(inventory, prices)) => inventory.get(product) match {
        case None | Some(0) =>
          sender() ! VendorError("ProductNotAvailable")
          stay()
        case Some(_) =>
          sender ! Instruction(s"Please insert ${prices(product)} dollars")
          // startReceiveMoneyTimeoutSchedule is skipped because Akka FSM can handle it by default
          goto(WaitingForMoney) using WaitForMoneyData(inventory, prices, product, 0, sender)
          //context.become(waitForMoney(inventory, prices, product, 0, startReceiveMoneyTimeoutSchedule(), sender))
      }
    }

    when(WaitingForMoney, stateTimeout = 1 second) {
      case Event(StateTimeout, WaitForMoneyData(inventory, prices, product, money, requester))  =>
        requester ! VendorError("RequestTimedOut")
        if (money > 0) requester ! GiveBackChange(money)
        goto(Operational) using Initialize(inventory, prices)

      case Event(ReceiveMoney(amount), WaitForMoneyData(inventory, prices, product, money, requester)) =>
        val price = prices(product)
        if (price <= amount + money) {
          requester ! Deliver(product)
          if (amount + money - price > 0) requester ! GiveBackChange(amount + money - price)
          val newStock = inventory(product) - 1
          val newInventory = inventory + (product -> newStock)
          goto(Operational) using Initialize(inventory, prices)
        }
        else {
          val remainingMoney = price - (amount + money)
          requester ! Instruction(s"Please insert $remainingMoney dollars")
          stay() using WaitForMoneyData(inventory, prices, product, amount + money, requester)
        }
    }

    whenUnhandled {
      case Event(_, _) =>
        sender ! VendorError("CommandNotFound")
        stay()
    }

    onTransition {
      case stateA -> stateB => log.info(s"Trasitioning from $stateA to $stateB")
    }

    initialize()  //starts Actor
  }
}