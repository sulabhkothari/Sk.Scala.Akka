package actorEssentials.AkkaPatterns

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit}
import akka.util.Timeout
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

class AskSpec extends TestKit(ActorSystem("AskSpec"))
  with ImplicitSender with WordSpecLike with BeforeAndAfterAll {

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  import AskSpec._

  def authenticatorTestSuite(props: Props) = {
    "fail to authenticate a non-registered user" in {
      val authManager = system.actorOf(props)

      authManager ! Authenticate("daniel", "rtjvm")
      expectMsg(AuthFailure(AuthManager1.AUTH_FAILURE_NOT_FOUND))
    }

    "fail to authenticate if invalid password" in {
      val authManager = system.actorOf(props)

      authManager ! RegisterUser("daniel", "rtjvm")
      authManager ! Authenticate("daniel", "rtjvm1")

      expectMsg(AuthFailure(AuthManager1.AUTH_FAILURE_PASSWORD_INCORRECT))
    }

    "successfully authenticate a registered user" in {
      val authManager = system.actorOf(props)

      authManager ! RegisterUser("daniel", "rtjvm")
      authManager ! Authenticate("daniel", "rtjvm")

      expectMsg(AuthSuccess)
    }
  }

  "An authenticator" should {
    authenticatorTestSuite(Props[AuthManager])
  }

  "An piped authenticator" should {
    authenticatorTestSuite(Props[PipedAuthManager])
  }
}

object AskSpec {

  case class Read(key: String)

  case class Write(key: String, value: String)

  // this code is somewhere else in your application
  class KVActor extends Actor with ActorLogging {
    override def receive: Receive = online(Map())

    def online(kv: Map[String, String]): Receive = {
      case Read(key) =>
        log.info(s"Trying to read the value at key: $key")
        sender ! kv.get(key)
      case Write(key, value) =>
        log.info(s"Writing the value: $value for key: $key")
        context.become(online(kv + (key -> value)))
    }
  }

  // user authentication actor
  case class RegisterUser(userName: String, password: String)

  case class Authenticate(userName: String, password: String)

  case class AuthFailure(message: String)

  case object AuthSuccess

  object AuthManager1 {
    val AUTH_FAILURE_NOT_FOUND = "username not found"
    val AUTH_FAILURE_PASSWORD_INCORRECT = "password not correct"
    val AUTH_FAILURE_SYSTEM = "system error"
  }

  import akka.pattern.ask
  import akka.pattern.pipe

  import scala.concurrent.duration._

  class AuthManager extends Actor with ActorLogging {
    protected val authDb = context.actorOf(Props[KVActor])

    // Step 2: Logistics
    implicit val timeout: Timeout = Timeout(1 second)
    implicit val executionContext: ExecutionContext = context.dispatcher

    override def receive: Receive = {
      case RegisterUser(userName, password) => authDb ! Write(userName, password)
      case Authenticate(userName, password) => handleAuthentication(userName, password)

      //        authDb ! Read(userName)
      //        context.become(waitingForPassword(userName, sender))
    }

    def handleAuthentication(userName: String, password: String) = {
      val originalSender = sender()

      // Step 3: Ask the actor
      val future = authDb ? Read(userName)

      // Step 4: handle the future for e.g., with onComplete
      future.onComplete {
        // Step 5: Most important
        // NEVER CALL METHODS ON THE ACTOR INSTANCE OR ACCESS MUTABLE STATE IN ONCOMPLETE.
        // avoid closing over the actor instance or mutable state
        case Success(None) => originalSender ! AuthFailure(AuthManager1.AUTH_FAILURE_NOT_FOUND)
        case Success(Some(dbPassword)) =>
          if (dbPassword == password) originalSender ! AuthSuccess
          else originalSender ! AuthFailure(AuthManager1.AUTH_FAILURE_PASSWORD_INCORRECT)
        case Failure(_) => originalSender ! AuthFailure(AuthManager1.AUTH_FAILURE_SYSTEM)
      }
    }

    //    def waitingForPassword(str: String, ref: ActorRef): Receive = {
    //      case password: Option[String] => //do password checks here
    //    }
  }

  class PipedAuthManager extends AuthManager {
    override def handleAuthentication(userName: String, password: String): Unit = {
      val future = authDb ? Read(userName) //Future[Any]
      val passwordFuture = future.mapTo[Option[String]]
      val responseFuture = passwordFuture.map {
        case None => AuthFailure(AuthManager1.AUTH_FAILURE_NOT_FOUND)
        case Some(dbpassword) =>
          if (dbpassword == password) AuthSuccess
          else AuthFailure(AuthManager1.AUTH_FAILURE_PASSWORD_INCORRECT)
      }

      /**
        * When the future completes, send the response to the actor ref in the arg list.
        * Always prefer pipeTo because it does not expose you to onComplete callbacks
        * where you can break the actor encapsulation
        */
      responseFuture.pipeTo(sender)
    }
  }

}
