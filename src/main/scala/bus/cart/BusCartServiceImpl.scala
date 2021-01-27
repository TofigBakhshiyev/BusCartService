package bus.cart

import java.util.concurrent.TimeoutException

import scala.concurrent.Future

import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.grpc.GrpcServiceException
import akka.util.Timeout
import io.grpc.Status
import org.slf4j.LoggerFactory

class ShoppingCartServiceImpl(system: ActorSystem[_]) extends proto.BusCartService {

  import system.executionContext

  private val logger = LoggerFactory.getLogger(getClass)

  implicit private val timeout: Timeout =
    Timeout.create(
      system.settings.config.getDuration("bus-cart-service.ask-timeout"))

  private val sharding = ClusterSharding(system)

  override def addAmount(in: proto.AddAmountRequest): Future[proto.Cart] = {
    logger.info("addAmount {} to cart {}", in.userId, in.cartId)
    val entityRef = sharding.entityRefFor(BusCart.EntityKey, in.cartId)
    val reply: Future[BusCart.Summary] =
      entityRef.askWithStatus(BusCart.AddAmount(in.userId, in.amount, _))
    val response = reply.map(cart => toProtoCart(cart))
    convertError(response)
  }

  private def toProtoCart(cart: BusCart.Summary): proto.Cart = {
    proto.Cart(user = Option(proto.User(cart.userId, cart.amount)))
  }

  private def convertError[T](response: Future[T]): Future[T] = {
    response.recoverWith {
      case _: TimeoutException =>
        Future.failed(
          new GrpcServiceException(
            Status.UNAVAILABLE.withDescription("Operation timed out")))
      case exc =>
        Future.failed(
          new GrpcServiceException(
            Status.INVALID_ARGUMENT.withDescription(exc.getMessage)))
    }
  }
}
