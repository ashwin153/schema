package caustic.library.collection

import caustic.library.control._
import caustic.library.typing._
import caustic.runtime

import scala.language.reflectiveCalls

/**
 * A mutable collection of values.
 *
 * @param length Current size.
 */
class List[T <: Primitive](length: Variable[Int]) {

  /**
   * Returns the number of values in the list.
   *
   * @return Number of values.
   */
  def size: Value[Int] = this.length

  /**
   * Returns the value at the specified index.
   *
   * @param index Index.
   * @param context Parse context.
   * @return Value.
   */
  def apply(index: Value[Int])(implicit context: Context): Variable[T] = {
    this.length.scope[T](index)
  }

  /**
   * Appends the value to the end of the list.
   *
   * @param value Value.
   * @param context Parse context.
   */
  def append(value: Value[T])(implicit context: Context): Unit = {
    this.length.scope[T](this.length) := value
    this.length += 1
  }

  /**
   * Applies the function to each value in the list.
   *
   * @param f Function.
   * @param context Parse context.
   */
  def foreach[U](f: (Value[Int], Value[T]) => U)(implicit context: Context): Unit = {
    if (this.length.isInstanceOf[Remote[Int]]) context += runtime.prefetch(this.length.key, this.length)
    val index = Local[Int](context.label())
    index := 0

    While (index < this.length) {
      f(index, this(index))
    }
  }

  /**
   * Removes the value from the list.
   *
   * @param value Value.
   * @param context Parse context.
   */
  def remove(value: Value[T])(implicit context: Context): Unit = {
    val index = this.indexOf(value)

    If (index <> -1) {
      // Shift over all values after the index of the removed value.
      foreach { case (i, _) =>
        If (i >= index && i < this.length - 1) {
          this.length.scope[T](i) := this(i + 1)
        }
      }

      // Decrement the size of the list.
      this.length.scope[T](this.length - 1) := Null
      this.length -= 1
    }
  }

  /**
   * Returns whether or not the list contains the specified value.
   *
   * @param value Value.
   * @param context Parse context.
   * @return Whether or not the value is in the list.
   */
  def contains(value: Value[T])(implicit context: Context): Value[Boolean] = {
    indexOf(value) >= 0
  }

  /**
   * Returns the index of the value in the list of -1 if it is not present.
   *
   * @param value Value.
   * @param context Parse context.
   * @return Index of value in list or -1.
   */
  def indexOf(value: Value[T])(implicit context: Context): Value[Int] = {
    val index = Local[Int](context.label())
    index := -1
    foreach { case (i, v) => If (index === -1 && v === value)(index := i) }
    index
  }

  /**
   * Returns whether or not the lists contain the same values.
   *
   * @param that Another list.
   * @param context Parse context.
   * @return Whether or not the lists contain the same values.
   */
  def ===(that: List[T])(implicit context: Context): Value[Boolean] = {
    val equal = Local[Boolean](context.label())
    equal := true
    foreach { case (i, v) => equal := equal && this(i) === v }
    this.size === that.size && equal
  }

  /**
   * Removes all values from the list.
   *
   * @param context Parse context.
   */
  def clear()(implicit context: Context): Unit = {
    foreach { case (i, _) => this.length.scope[T](i) := Null }
    this.length := 0
  }

  /**
   * Returns the contents of the list as a JSON string.
   *
   * @param context Parse context.
   * @return JSON representation.
   */
  def toJson(implicit context: Context): Value[String] = {
    val json = Local[String](context.label())
    json := "["

    foreach { case (_, v) =>
      If (json === "[") {
        json := json ++ v.toJson
      } Else {
        json := json ++ ", " ++ v.toJson
      }
    }

    json ++ "]"
  }

  /**
   * Returns the list as a set.
   *
   * @return Set.
   */
  def toSet: Set[T] = new Set(this)

}

object List {

  /**
   * Constructs a list backed by the specified variable.
   *
   * @param length Variable.
   * @return List.
   */
  def apply[T <: Primitive](length: Variable[Int]): List[T] = new List(length)

}