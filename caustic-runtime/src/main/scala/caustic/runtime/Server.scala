package caustic.runtime

import caustic.runtime.jdbc.JdbcDatabase
import caustic.runtime.local.{LocalCache, LocalDatabase}
import caustic.runtime.redis.RedisCache
import com.typesafe.config.{Config, ConfigFactory}
import org.apache.thrift.server.TNonblockingServer
import org.apache.thrift.transport.TNonblockingServerSocket
import java.io.Closeable
import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

/**
 * A server instance.
 *
 * @param database Underlying database.
 * @param port Port number.
 */
case class Server(
  database: Database,
  port: Int
) extends Closeable {

  // Construct a Thrift server and serve it asynchronously.
  val transport = new TNonblockingServerSocket(this.port)
  val processor = new thrift.Database.AsyncProcessor(this.database)
  val arguments = new TNonblockingServer.Args(this.transport).processor(this.processor)
  val server = new TNonblockingServer(this.arguments)
  val thread = new Thread(() => this.server.serve())
  this.thread.start()

  override def close(): Unit = {
    // Close the Thrift server and underlying database.
    this.server.stop()
    this.database.close()
  }

}

object Server {

  // Configuration Root.
  val root: String = "caustic.runtime.server"

  /**
   *
   * @return
   */
  def apply(): Server =
    Server(ConfigFactory.load())

  /**
   *
   * @param config
   * @return
   */
  def apply(config: Config): Server = {
    // Setup the underlying database and iteratively construct caches.
    val database = config.getString(s"$root.database") match {
      case "local" => LocalDatabase(config)
      case "jdbc" => JdbcDatabase(config)
    }

    val underlying = config.getStringList(s"$root.caches").asScala.foldRight(database) {
      case ("local", db) => LocalCache(db, config)
      case ("redis", db) => RedisCache(db, config)
    }

    // Construct a database.
    Server(underlying, config.getInt(s"$root.port"))
  }

}