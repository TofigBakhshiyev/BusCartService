package bus.cart

import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.ShardedDaemonProcessSettings
import akka.cluster.sharding.typed.scaladsl.ShardedDaemonProcess
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import akka.persistence.query.Offset
import akka.projection.ProjectionBehavior
import akka.projection.ProjectionId
import akka.projection.cassandra.scaladsl.CassandraProjection
import akka.projection.eventsourced.EventEnvelope
import akka.projection.eventsourced.scaladsl.EventSourcedProvider
import akka.projection.scaladsl.AtLeastOnceProjection
import akka.projection.scaladsl.SourceProvider

object TransactionProjection {
  def init(
            system: ActorSystem[_],
            repository: UserTransactionRepository): Unit = {
    ShardedDaemonProcess(system).init(
      name = "TransactionProjection",
      BusCart.tags.size,
      index =>
        ProjectionBehavior(createProjectionFor(system, repository, index)),
      ShardedDaemonProcessSettings(system),
      Some(ProjectionBehavior.Stop))
  }

  private def createProjectionFor(
                                   system: ActorSystem[_],
                                   repository: UserTransactionRepository,
                                   index: Int)
  : AtLeastOnceProjection[Offset, EventEnvelope[BusCart.Event]] = {
    val tag = BusCart.tags(index)

    val sourceProvider
    : SourceProvider[Offset, EventEnvelope[BusCart.Event]] =
      EventSourcedProvider.eventsByTag[BusCart.Event](
        system = system,
        readJournalPluginId = CassandraReadJournal.Identifier,
        tag = tag)

    CassandraProjection.atLeastOnce(
      projectionId = ProjectionId("TransactionProjection", tag),
      sourceProvider,
      handler = () =>
        new ProjectionHandler(tag, system, repository)
    )
  }

}