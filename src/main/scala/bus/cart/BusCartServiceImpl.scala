package bus.cart

import java.util.concurrent.TimeoutException

import scala.concurrent.Future

import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.grpc.GrpcServiceException
import akka.util.Timeout
import io.grpc.Status
import org.slf4j.LoggerFactory

class BusCartServiceImpl(system: ActorSystem[_], userTransactionRepository: UserTransactionRepository) extends proto.BusCartService {

  import system.executionContext

  private val logger = LoggerFactory.getLogger(getClass)

  implicit private val timeout: Timeout =
    Timeout.create(
      system.settings.config.getDuration("bus-cart-service.ask-timeout"))

  private val sharding = ClusterSharding(system)

  override def addAmount(in: proto.AddAmountRequest): Future[proto.Cart] = {
    logger.info("addAmount {} to cart {}", in.amount, in.cartId)
    val entityRef = sharding.entityRefFor(BusCart.EntityKey, in.cartId)
    val reply: Future[BusCart.Summary] =
      entityRef.askWithStatus(BusCart.AddAmount(in.userId, in.amount, _))
    val response = reply.map(cart => toProtoCart(cart))
    convertError(response)
  }

  override def extractAmount(in: proto.ExtractAmountRequest): Future[proto.Cart] = {
    logger.info("fee {} extracted from cart {} in {} in the {} number bus - time: {}",
      in.fee, in.cartId, in.zone, in.busNumber, in.time)
    val entityRef = sharding.entityRefFor(BusCart.EntityKey, in.cartId)
    val reply: Future[BusCart.Summary] =
      entityRef.askWithStatus(BusCart.ExtractAmount(in.userId, in.fee,
        in.zone, in.busNumber, in.time, _))
    val response = reply.map(cart => toProtoCart(cart))
    convertError(response)
  }

  override def getCart(in: proto.GetCartRequest): Future[proto.Cart] = {
    logger.info("getCart {}", in.cartId)
    val entityRef = sharding.entityRefFor(BusCart.EntityKey, in.cartId)
    val response =
      entityRef.ask(BusCart.Get(in.userId, _)).map { cart =>
          toProtoCart(cart)
      }
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

  override def getUserLastTransaction(in: proto.GetUserLastTransactionRequest)
  : Future[proto.GetUserLastTransactionResponse] = {
    userTransactionRepository.getUser(in.userId).map {
      case Some(bus_number) =>
        proto.GetUserLastTransactionResponse(in.userId, bus_number)
      case None =>
        proto.GetUserLastTransactionResponse(in.userId, 0L)
    }
  }
}
