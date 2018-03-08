package caustic.beaker

package object ordering {

  // Implicit relation operations.
  implicit class RelationOps[T](x: T)(implicit relation: Relation[T]) {
    def ~(y: T): Boolean = relation.equiv(x, y)
  }

  // Implicit order operations.
  implicit class OrderOps[T](x: T)(implicit order: Order[T]) {
    def <|(y: T): Boolean = order.before(x, y).exists(identity)
    def |>(y: T): Boolean = order.before(y, x).exists(identity)
  }

}
