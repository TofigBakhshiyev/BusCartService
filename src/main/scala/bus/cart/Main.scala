package bus.cart

import akka.actor.typed.scaladsl.AbstractBehavior
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.scaladsl.AkkaManagement
import akka.stream.alpakka.cassandra.scaladsl.CassandraSessionRegistry

object Main {

  def main(args: Array[String]): Unit = {
    ActorSystem[Nothing](Main(), "BusCartService")
  }

  def apply(): Behavior[Nothing] = {
    Behaviors.setup[Nothing](context => new Main(context))
  }
}

class Main(context: ActorContext[Nothing])
    extends AbstractBehavior[Nothing](context) {
  val system = context.system

  AkkaManagement(system).start()
  ClusterBootstrap(system).start()

  BusCart.init(system)

  val session = CassandraSessionRegistry(system).sessionFor(
    "akka.persistence.cassandra"
  )
  // use same keyspace for the item_popularity table as the offset store
  val userTransactionKeyspace =
    system.settings.config
      .getString("akka.projection.cassandra.offset-store.keyspace")
  val userTransactionRepository =
    new UserTransactionRepositoryImpl(session, userTransactionKeyspace)(
      system.executionContext
    )

  TransactionProjection.init(system, userTransactionRepository)

  val grpcInterface =
    system.settings.config.getString("bus-cart-service.grpc.interface")
  val grpcPort =
    system.settings.config.getInt("bus-cart-service.grpc.port")
  val grpcService = new BusCartServiceImpl(system, userTransactionRepository)
  BusCartServer.start(grpcInterface, grpcPort, system, grpcService)

  override def onMessage(msg: Nothing): Behavior[Nothing] =
    this
}
