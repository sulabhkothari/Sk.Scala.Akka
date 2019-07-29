package actorEssentials

import akka.actor.{Actor, ActorRef, ActorSystem, Props}

object SimpleVotingSystem {
  def main(args: Array[String]): Unit = {
    val system = ActorSystem("SimpleVotingSystem")
    val alice = system.actorOf(Props[Citizen], "alice")
    val bob = system.actorOf(Props[Citizen], "bob")
    val charlie = system.actorOf(Props[Citizen], "charlie")
    val daniel = system.actorOf(Props[Citizen], "daniel")

    alice ! Vote("Martin")
    bob ! Vote("Jonas")
    charlie ! Vote("Roland")

    val votes = AggregateVotes(Set(alice, bob, charlie, daniel))
    val voteAggregator = system.actorOf(Props[VoteAggregator])
    voteAggregator ! votes

    daniel ! Vote("Roland")

    // Below will add to the votes until a condition is added to receive replies only from registered voters
    // Sequence-wise below tells will be fired before citizen replies that send back voting status
    // because actors are asynchronous and these calls are made just after citizen foreach is executed
    //voteAggregator ! VotingStatusReply(Some("Roland"))
    //voteAggregator ! VotingStatusReply(Some("Martin"))

  }

  case class Vote(candidate: String)

  case object VotingStatusRequest

  case class VotingStatusReply(candidate: Option[String])

  class Citizen extends Actor {
    override def receive: Receive = votingHandler(None)

    def votingHandler(candidate: Option[String]): Receive = {
      case Vote(candidate) => context.become(votingHandler(Some(candidate)))
      case VotingStatusRequest => context.sender ! VotingStatusReply(candidate)
    }
  }

  case class AggregateVotes(citizens: Set[ActorRef])

  class VoteAggregator extends Actor {
    override def receive: Receive = awaitingCommand

    def awaitingCommand: Receive = {
      case AggregateVotes(citizens) =>
        citizens.foreach(_ ! VotingStatusRequest)
        context.become(awaitingStatuses(Map(), citizens))
    }

    def awaitingStatuses(voteMap: Map[String, Int], citizens: Set[ActorRef]): Receive = {
      case VotingStatusReply(Some(candidate)) =>
        if (citizens.contains(sender)) {
          val newVoteMap = voteMap + voteMap.get(candidate).map(_ + 1).map(candidate -> _).getOrElse(candidate -> 1)
          val remainingCitizens = citizens - context.sender
          if (remainingCitizens.isEmpty) {
            println(s"Vote Count: $newVoteMap")
            context.become(awaitingCommand)
          }
          else context.become(awaitingStatuses(newVoteMap, remainingCitizens))
        }
      case VotingStatusReply(None) => context.sender ! VotingStatusRequest
    }
  }

}
