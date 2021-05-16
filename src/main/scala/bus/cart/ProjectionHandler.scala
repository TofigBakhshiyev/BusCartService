package bus.cart

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import akka.Done
import akka.actor.typed.ActorSystem
import akka.projection.eventsourced.EventEnvelope
import akka.projection.scaladsl.Handler
import org.slf4j.LoggerFactory

class ProjectionHandler(
    tag: String,
    system: ActorSystem[_],
    repo: UserTransactionRepository)
    extends Handler[EventEnvelope[BusCart.Event]]() {

  private val log = LoggerFactory.getLogger(getClass)
  private implicit val ec: ExecutionContext =
    system.executionContext

  override def process(envelope: EventEnvelope[BusCart.Event]): Future[Done] = {
    envelope.event match {
      case BusCart.AmountAdded(cartId, userId, _, zone, bus_number, time) =>
        val result = repo.update(cartId, userId, zone, bus_number, time)
        result
    }
  }
}
