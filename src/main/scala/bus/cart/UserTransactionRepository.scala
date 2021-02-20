package bus.cart

import scala.concurrent.Future
import akka.Done

case class UserLastTransactionRow(userid: String, bus_number: Int,
                                  cartid: String, time: Int, zone: String)

trait UserTransactionRepository {
  def update(cartId: String, userId: String, zone: String,
             bus_number: Int, time: Int): Future[Done]
  def getUser(userId: String): Future[Option[UserLastTransactionRow]]
}

import scala.concurrent.Future
import akka.Done
import scala.concurrent.ExecutionContext
import akka.stream.alpakka.cassandra.scaladsl.CassandraSession

object UserTransactionRepositoryImpl {
  val popularityTable = "user_projection"
}

class UserTransactionRepositoryImpl(session: CassandraSession, keyspace: String)(
  implicit val ec: ExecutionContext)
  extends UserTransactionRepository {
  import UserTransactionRepositoryImpl.popularityTable

  override def update(cartId: String, userId: String, zone: String,
                      bus_number: Int, time: Int): Future[Done] = {
    session.executeWrite(
      s"INSERT INTO $keyspace.$popularityTable(cartId, userId, zone, bus_number, time) VALUES (?, ?, ?, ?, ?)",
      cartId,
      userId,
      zone,
      java.lang.Integer.valueOf(bus_number),
      java.lang.Integer.valueOf(time)
    )
  }

  override def getUser(userId: String): Future[Option[UserLastTransactionRow]] = {
    session
      .selectOne(
        s"SELECT * FROM $keyspace.$popularityTable WHERE userId = ?",
        userId)
      .map(opt => opt.map(row => UserLastTransactionRow(
        userid = row.getString("userid").toString,
        bus_number = row.getInt("bus_number").intValue(),
        cartid = row.getString("cartid").toString,
        time = row.getInt("time").toInt,
        zone = row.getString("zone").toString)))
      //.map(opt => opt.map(row => row.getInt("bus_number").intValue()))
  }
}
