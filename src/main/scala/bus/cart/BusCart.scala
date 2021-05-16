package bus.cart

import akka.actor.typed.ActorRef
import akka.pattern.StatusReply
import akka.persistence.typed.scaladsl.Effect
import akka.persistence.typed.scaladsl.ReplyEffect
import scala.concurrent.duration._
import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.SupervisorStrategy
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.cluster.sharding.typed.scaladsl.Entity
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.EventSourcedBehavior
import akka.persistence.typed.scaladsl.RetentionCriteria
import akka.cluster.sharding.typed.scaladsl.EntityContext

object BusCart {

  sealed trait Command extends CborSerializable

  /**
   * A command to add an amount to the cart.
   *
   * It replies with `StatusReply[Summary]`, which is sent back to the caller when
   * all the events emitted by this command are successfully persisted.
   */

  final case class AddAmount(
      userId: String,
      amount: Int,
      replyTo: ActorRef[StatusReply[Summary]])
      extends Command

  final case class ExtractAmount(
      userId: String,
      fee: Int,
      zone: String,
      bus_number: Int,
      time: Int,
      replyTo: ActorRef[StatusReply[Summary]])
      extends Command

  final case class Get(userId: String, replyTo: ActorRef[Summary])
      extends Command

  /**
   * Summary of the bus cart state, used in reply messages.
   */
  final case class Summary(userId: String, amount: Int) extends CborSerializable

  /**
   * This interface defines all the events that the BusCart supports.
   */

  sealed trait Event extends CborSerializable {
    def cartId: String
  }

  final case class AmountAdded(
      cartId: String,
      userId: String,
      amount: Int,
      zone: String,
      bus_number: Int,
      time: Int)
      extends Event

  /*final case class AmountExtracted(cartId: String, userId: String, fee: Int,
                                   zone: String, bus_number: Int, time: Int)
    extends Event*/

  final case class State(user: Map[String, Int]) extends CborSerializable {

    def hasItem(userId: String): Boolean =
      user.contains(userId)

    def getAmount(userId: String): Int =
      user(userId)

    def toSummary(userId: String): Summary = {
      Summary(userId, user(userId))
    }

    def updateItem(userId: String, amount: Int): State = {
      amount match {
        case _ => copy(user = Map(userId -> amount))
      }
    }
  }
  object State {
    val empty = State(user = Map.empty)
  }

  private def handleCommand(
      cartId: String,
      state: State,
      command: Command): ReplyEffect[Event, State] = {
    command match {
      case AddAmount(userId, amount, replyTo) =>
        if (state.hasItem(userId)) {
          if (amount <= 0)
            Effect.reply(replyTo)(
              StatusReply.Error("Quantity must be greater than zero"))
          else {
            val new_amount = amount + state.getAmount(userId)
            Effect
              .persist(AmountAdded(cartId, userId, new_amount, "", 0, 0))
              .thenReply(replyTo) { updatedCart =>
                StatusReply.Success(Summary(userId, new_amount))
              }
          }
        } else {
          Effect.reply(replyTo)(
            StatusReply.Error("There is not any card with this card Id"))
        }
      case ExtractAmount(userId, fee, zone, bus_number, time, replyTo) =>
        if (state.hasItem(userId)) {
          val amount = state.getAmount(userId)
          if (amount < fee)
            Effect.reply(replyTo)(
              StatusReply.Error("You have not enough money, " +
              "please increase card balance"))
          else {
            val new_amount = amount - fee
            Effect
              .persist(
                AmountAdded(cartId, userId, new_amount, zone, bus_number, time))
              .thenReply(replyTo) { updatedCart =>
                StatusReply.Success(Summary(userId, new_amount))
              }
          }
        } else {
          Effect.reply(replyTo)(
            StatusReply.Error("There is not any card with this card Id"))
        }
      case Get(userId, replyTo) =>
          Effect.reply(replyTo)(state.toSummary(userId))
    }
  }

  private def handleEvent(state: State, event: Event) = {
    event match {
      case AmountAdded(_, userId, amount, _, _, _) =>
        state.updateItem(userId, amount)
    }
  }

  val EntityKey: EntityTypeKey[Command] =
    EntityTypeKey[Command]("BusCart")

  val tags = Vector.tabulate(5)(i => s"carts-$i")

  def init(system: ActorSystem[_]): Unit = {
    /*ClusterSharding(system).init(Entity(EntityKey) { entityContext =>
      BusCart(entityContext.entityId)
    })*/
    val behaviorFactory: EntityContext[Command] => Behavior[Command] = {
      entityContext =>
        val i = math.abs(entityContext.entityId.hashCode % tags.size)
        val selectedTag = tags(i)
        BusCart(entityContext.entityId, selectedTag)
    }
    ClusterSharding(system).init(Entity(EntityKey)(behaviorFactory))
  }

  def apply(cartId: String, projectionTag: String): Behavior[Command] = {
    EventSourcedBehavior
      .withEnforcedReplies[Command, Event, State](
        persistenceId = PersistenceId(EntityKey.name, cartId),
        emptyState = State.empty,
        commandHandler =
          (state, command) => handleCommand(cartId, state, command),
        eventHandler = (state, event) => handleEvent(state, event))
      .withTagger(_ => Set(projectionTag))
      .withRetention(RetentionCriteria
        .snapshotEvery(numberOfEvents = 100, keepNSnapshots = 3))
      .onPersistFailure(
        SupervisorStrategy.restartWithBackoff(200.millis, 5.seconds, 0.1))
  }
}
