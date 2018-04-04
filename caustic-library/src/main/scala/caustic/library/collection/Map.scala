package caustic.library.collection

import caustic.library.control._
import caustic.library.typing._

import scala.language.reflectiveCalls

/**
 * A mutable collection of key-value pairs.
 *
 * @param keys Keys.
 */
case class Map[A <: String, B <: Primitive](keys: Set[A]) {

  /**
   * Returns the number of entries.
   *
   * @return Number of entries.
   */
  def size: Value[Int] = this.keys.size

  /**
   * Returns the value at the specified key.
   *
   * @param key Key.
   * @param context Parse context.
   * @return Value.
   */
  def apply(key: Value[A])(implicit context: Context): Value[B] = {
    this.keys.toList(this.keys.toList.indexOf(key)).scope(key)
  }

  /**
   * Returns whether or not the key is present in the map.
   *
   * @param key Key.
   * @param context Parse context.
   * @return Whether or not key is in map.
   */
  def contains(key: Value[A])(implicit context: Context): Value[Boolean] = this.keys.contains(key)

  /**
   * Adds the key-value pair to the map.
   *
   * @param key Key.
   * @param value Value.
   * @param context Parse context.
   */
  def put(key: Value[A], value: Value[B])(implicit context: Context): Unit = {
    If (value === Null) {
      this.keys.remove(key)
    } Else {
      this.keys.add(key)
      this.keys.toList(this.keys.toList.indexOf(key)).scope[B](key) := value
    }
  }

  /**
   * Adds the key-value pair to the map if the key is not already present in the map.
   *
   * @param key Key.
   * @param value Value.
   * @param context Parse context.
   */
  def putIfAbsent(key: Value[A], value: Value[B])(implicit context: Context): Unit = {
    If (!contains(key)) {
      put(key, value)
    }
  }

  /**
   * Removes the key from the map.
   *
   * @param key Key.
   * @param context Parse context.
   */
  def remove(key: Value[A])(implicit context: Context): Unit = put(key, Null)

  /**
   * Applies the function to each key-value pair in the map.
   *
   * @param f Function.
   * @param context Parse context.
   */
  def foreach[U](f: (Value[A], Value[B]) => U)(implicit context: Context): Unit = {
    this.keys.foreach(k => f(k, this(k)))
  }

  /**
   * Removes all key-value pairs from the map.
   *
   * @param context Parse context.
   */
  def clear()(implicit context: Context): Unit = {
    foreach { case (k, _) => this.keys.toList(this.keys.toList.indexOf(k)).scope[B](k) := Null }
    this.keys.clear()
  }

  /**
   * Returns whether or not the maps contain the same key-value pairs.
   *
   * @param that Another map.
   * @param context Parse context.
   * @return Whether or not the maps are equal.
   */
  def ===(that: Map[A, B])(implicit context: Context): Value[Boolean] = {
    val equals = Local[Boolean](context.label())
    foreach { case (k, v) => equals := equals && v === that(k) }
    equals && this.keys === that.keys
  }

  /**
   * Returns the contents of the map as a JSON string.
   *
   * @param context Parse context.
   * @return JSON representation.
   */
  def toJson(implicit context: Context): Value[String] = {
    val json = Local[String](context.label())
    json := "{"

    foreach { case (k, v) =>
      If (json === "{") {
        json := json ++ k.quoted ++ ": " ++ v.toJson
      } Else {
        json := json ++ ", " ++ k.quoted ++ ": " ++ v.toJson
      }
    }

    json ++ "}"
  }

}

object Map {

  /**
   * Constructs a map backed by the specified variable.
   *
   * @param length Variable.
   * @return Map.
   */
  def apply[A <: String, B <: Primitive](length: Variable[Int]): Map[A, B] = new Map(Set(length))

}