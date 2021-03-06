package caustic.runtime

import beaker.client._
import beaker.server.protobuf._

import scala.collection.concurrent
import scala.util.Try

/**
 * A transactional key-value store. Thread-safe.
 */
trait Volume extends Serializable {

  /**
   * Returns the revisions of the specified keys.
   *
   * @param keys Keys to retrieve.
   * @return Revisions of keys.
   */
  def get(keys: Set[Key]): Try[Map[Key, Revision]]

  /**
   * Conditionally applies the changes if the dependencies remain unchanged.
   *
   * @param depends Dependencies.
   * @param changes Conditional updates.
   * @return Whether or not the changes were applied.
   */
  def cas(depends: Map[Key, Version], changes: Map[Key, Value]): Try[Unit]

}

object Volume {

  /**
   * An in-memory, thread-safe database. Useful for testing.
   *
   * @param map Underlying map.
   */
  class Memory(map: concurrent.Map[Key, Revision]) extends Volume {

    override def get(keys: Set[Key]): Try[Map[Key, Revision]] = {
      Try(keys.map(k => k -> this.map.getOrElse(k, Revision.defaultInstance)).toMap)
    }

    override def cas(depends: Map[Key, Version], changes: Map[Key, Value]): Try[Unit] = synchronized {
      Try(depends.keySet.map(k => k -> this.map.getOrElse(k, Revision.defaultInstance)).toMap)
        .filter(_ forall { case (k, v) => depends(k) >= v.version })
        .map(_ => changes map { case (k, v) => k -> Revision(depends(k) + 1, v) })
        .map(this.map ++= _)
    }

  }

  object Memory {

    /**
     * Constructs an empty in-memory database.
     *
     * @return Empty local database.
     */
    def empty: Volume.Memory =
      Volume.Memory(Map.empty[Key, Revision])

    /**
     * Constructs an in-memory database initialized with the specified values.
     *
     * @param initial Initial values.
     * @return Initialized local database.
     */
    def apply(initial: (Key, Revision)*): Volume.Memory =
      Volume.Memory(initial.toMap)

    /**
     * Constructs an in-memory database initialized with the specified values.
     *
     * @param initial Initialized values.
     * @return Initialized local database.
     */
    def apply(initial: Map[Key, Revision]): Volume.Memory =
      new Volume.Memory(concurrent.TrieMap(initial.toSeq: _*))

  }

  /**
   * A Beaker database.
   *
   * @param client Beaker client.
   */
  class Beaker(client: Client) extends Volume {

    override def get(keys: Set[Key]): Try[Map[Key, Revision]] =
      this.client.get(keys)

    override def cas(depends: Map[Key, Version], changes: Map[Key, Value]): Try[Unit] =
      this.client.cas(depends, changes).map(_ => ())
    
  }

  object Beaker {

    /**
     * Constructs a Beaker database connected to the specified address.
     *
     * @param name Hostname.
     * @param port Port number.
     * @return Connected beaker database.
     */
    def apply(name: String, port: Int): Volume.Beaker =
      Volume.Beaker(Address(name, port))

    /**
     * Constructs a Beaker database connected to the specified address.
     *
     * @param address Network location.
     * @return Connected beaker database.
     */
    def apply(address: Address): Volume.Beaker =
      Volume.Beaker(Client(address))

    /**
     * Constructs a Beaker database connected to the specified client.
     *
     * @param client Beaker client.
     * @return Connected beaker database.
     */
    def apply(client: Client): Volume.Beaker =
      new Volume.Beaker(client)

  }

}
