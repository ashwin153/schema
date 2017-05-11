package com.schema.runtime

import akka.actor.ActorSystem
import akka.pattern.after
import com.schema.runtime
import com.schema.runtime.syntax.Context.Variable
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.FiniteDuration

/**
 *
 */
package object syntax {

  // Implicit conversions.
  implicit def pxy2txn(proxy: Proxy): Transaction = read(proxy.key)
  implicit def pxy2fix(proxy: Proxy): InfixTransaction = pxy2txn(proxy)
  implicit def fld2obj(field: Field): Object = Object(read(field.key))
  implicit def var2fix(variable: Variable): InfixTransaction = var2txn(variable)
  implicit def var2txn(variable: Variable): Transaction = load(variable.name)

  // Additional Math Operations.
  lazy val E : Transaction = Literal(math.E.toString)
  lazy val Pi: Transaction = Literal(math.Pi.toString)

  def abs(x: Transaction): Transaction = branch(less(x, Literal.Zero), sub(Literal.Zero, x), x)
  def exp(x: Transaction): Transaction = pow(E, x)
  def tan(x: Transaction): Transaction = div(sin(x), cos(x))
  def cot(x: Transaction): Transaction = div(cos(x), sin(x))
  def sec(x: Transaction): Transaction = div(Literal.One, cos(x))
  def csc(x: Transaction): Transaction = div(Literal.One, sin(x))

  def sinh(x: Transaction): Transaction = div(sub(exp(x), exp(sub(Literal.Zero, x))), Literal.Two)
  def cosh(x: Transaction): Transaction = div(add(exp(x), exp(sub(Literal.Zero, x))), Literal.Two)
  def tanh(x: Transaction): Transaction = div(sinh(x), cosh(x))
  def coth(x: Transaction): Transaction = div(cosh(x), sinh(x))
  def sech(x: Transaction): Transaction = div(Literal.One, cosh(x))
  def csch(x: Transaction): Transaction = div(Literal.One, sinh(x))
  def sqrt(x: Transaction): Transaction = pow(x, Literal.Half)
  def ceil(x: Transaction): Transaction = branch(equal(x, floor(x)), x, floor(x) + Literal.One)

  def round(x: Transaction): Transaction = branch(less(sub(x, floor(x)), Literal.Half), floor(x), ceil(x))

  // Infix Operator Extensions.
  implicit class InfixTransaction(x: Transaction) {

    def unary_- : Transaction = sub(Literal.Zero, x)
    def unary_! : Transaction = not(x)
    def unary_~ : Transaction = not(x)

    def +(y: Transaction): Transaction = add(x, y)
    def -(y: Transaction): Transaction = sub(x, y)
    def *(y: Transaction): Transaction = mul(x, y)
    def /(y: Transaction): Transaction = div(x, y)
    def %(y: Transaction): Transaction = mod(x, y)

    def <(y: Transaction): Transaction = less(x, y)
    def >(y: Transaction): Transaction = not(or(equal(x, y), less(x, y)))
    def ===(y: Transaction): Transaction = equal(x, y)
    def <>(y: Transaction): Transaction = not(equal(x, y))
    def <=(y: Transaction): Transaction = or(equal(x, y), less(x, y))
    def >=(y: Transaction): Transaction = not(less(x, y))
    def &&(y: Transaction): Transaction = and(x, y)
    def ||(y: Transaction): Transaction = or(x, y)
    def max(y: Transaction): Transaction = branch(less(x, y), y, x)
    def min(y: Transaction): Transaction = branch(less(x, y), x, y)

    def ++(y: Transaction): Transaction = concat(x, y)
    def charAt(i: Transaction): Transaction = slice(x, i, add(i, Literal.One))
    def contains(y: Transaction): Transaction = runtime.contains(x, y)
    def endsWith(y: Transaction): Transaction = equal(x.substring(length(x) - length(y)), y)
    def startsWith(y: Transaction): Transaction = equal(x.substring(0, length(y)), y)
    def matches(y: Transaction): Transaction = runtime.matches(x, y)
    def substring(l: Transaction): Transaction = x.substring(l, length(x))
    def substring(l: Transaction, h: Transaction): Transaction = slice(x, l, h)

  }

  // Objects are denormalized, so that different fields of the same object may be simultaneously
  // modified by separate transactions. Together, these delimiters enable the library to generate
  // lists of globally unique field identifiers which it uses to keep track the object's fields.
  val FieldDelimiter: Literal = literal("@")
  val ListDelimiter: Literal = literal(",")

  /**
   *
   * @param f
   * @param ec
   * @param db
   * @return
   */
  def Schema(backoffs: Stream[FiniteDuration])(f: Context => Unit)(
    implicit ec: ExecutionContext,
    akka: ActorSystem,
    db: Database
  ): Future[String] =
    Schema(f).recoverWith { case _ if backoffs.nonEmpty =>
      after(backoffs.head, akka.scheduler)(Schema(backoffs.drop(1))(f))
    }

  /**
   *
   * @param f
   * @param ec
   * @param db
   * @return
   */
  def Schema(f: Context => Unit)(
    implicit ec: ExecutionContext,
    db: Database
  ): Future[String] = {
    val ctx = Context.empty
    f(ctx)
    db.execute(ctx.txn)
  }

  /**
   *
   * @param key
   * @param ctx
   * @return
   */
  def Select(key: Key)(implicit ctx: Context): Object = {
    require(key.nonEmpty, "Key must be non-empty.")
    require(!key.contains(FieldDelimiter.value), s"Key may not contain ${FieldDelimiter.value}")
    require(!key.contains(ListDelimiter.value), s"Key may not contain ${ListDelimiter.value}")
    Object(key)
  }

  /**
   *
   * @param obj
   * @param ctx
   */
  def Delete(obj: Object)(implicit ctx: Context): Unit = {
    // When working with loops, it is important to prefetch keys whenever possible.
    prefetch(obj.$fields)
    prefetch(obj.$indices)

    // Serialize the various fields of the object.
    If(length(obj.$fields) > 0) {
      ctx.$i = 0

      While(ctx.$i < length(obj.$fields)) {
        ctx.$j = ctx.$i
        While(!equal(obj.$fields.charAt(ctx.$j), ListDelimiter)) {
          ctx.$j = ctx.$j + 1
        }

        val name = obj.$fields.substring(ctx.$i, ctx.$j)
        ctx += write(obj.key ++ FieldDelimiter ++ name, Literal.Empty)
        ctx.$i = ctx.$j + 1
      }
    }

    // Serialize the various indices of the object.
    If (length(obj.$indices) > 0) {
      ctx.$i = 0

      While (ctx.$i < length(obj.$indices)) {
        ctx.$j = ctx.$i + 1
        While (!equal(obj.$indices.charAt(ctx.$j), ListDelimiter)) {
          ctx.$j = ctx.$j + 1
        }

        val name = obj.$indices.substring(ctx.$i, ctx.$j)
        val field = obj.key ++ FieldDelimiter ++ name
        val index = read(field ++ FieldDelimiter ++ literal("$values"))
        prefetch(index)
        ctx.$k = 0

        While(ctx.$k < length(index)) {
          ctx.$l = ctx.$k + 1
          While(!equal(index.charAt(ctx.$l), ListDelimiter)) {
            ctx.$l = ctx.$l + 1
          }

          val at = index.substring(ctx.$k, ctx.$l)
          ctx += write(field ++ FieldDelimiter ++ at, Literal.Empty)
          ctx.$k = ctx.$l + 1
        }

        ctx += write(field ++ FieldDelimiter ++ literal("$values"), Literal.Empty)
        ctx.$i = ctx.$j + 1
      }
    }

    // Clean up all our hidden variables and remove the existence marker on the object.
    obj.$fields = ""
    obj.$indices = ""
    ctx += write(obj.key, Literal.Empty)
  }

  /**
   *
   * @param cmp
   * @param success
   * @param ctx
   * @return
   */
  def If(cmp: Transaction)(success: => Unit)(
    implicit ctx: Context
  ) = new {
    private val before = ctx.txn
    ctx.txn = Literal.Empty
    success
    private val pass = ctx.txn
    ctx.txn = before
    ctx += branch(cmp, pass, Literal.Empty)

    def Else(failure: => Unit): Unit = {
      ctx.txn = Literal.Empty
      failure
      val fail = ctx.txn
      ctx.txn = before
      ctx += branch(cmp, pass, fail)
    }
  }

  /**
   *
   * @param cmp
   * @param block
   * @param ctx
   */
  def While(cmp: Transaction)(block: => Unit)(
    implicit ctx: Context
  ): Unit = {
    val before = ctx.txn
    ctx.txn = Literal.Empty
    block
    val body = ctx.txn
    ctx.txn = before
    ctx += repeat(cmp, body)
  }

  /**
   *
   * @param index
   * @param from
   * @param until
   * @param step
   * @param block
   * @param ctx
   */
  def For(
    index: Variable,
    from: Transaction,
    until: Transaction,
    step: Transaction = Literal.One
  )(
    block: => Unit
  )(
    implicit ctx: Context
  ): Unit = {
    ctx += store(index.name, from)
    While (load(index.name) < until) {
      block
      ctx += store(index.name, load(index.name) + step)
    }
  }

  /**
   *
   * @param obj
   * @param ctx
   */
  def Return(obj: Object)(implicit ctx: Context): Unit = {
    // When working with loops, it is important to prefetch keys whenever possible.
    prefetch(obj.$fields)
    prefetch(obj.$indices)
    ctx.$json = literal("{\"key\":\"") ++ obj.key ++ "\""

    // Serialize the various fields of the object.
    If(length(obj.$fields) > 0) {
      ctx.$i = 0

      While(ctx.$i < length(obj.$fields)) {
        ctx.$j = ctx.$i
        While(!equal(obj.$fields.charAt(ctx.$j), ListDelimiter)) {
          ctx.$j = ctx.$j + 1
        }

        val name = obj.$fields.substring(ctx.$i, ctx.$j)
        val value = read(obj.key ++ FieldDelimiter ++ name)
        ctx.$json = ctx.$json ++ ",\"" ++ name ++ "\":\"" ++ value ++ "\""
        ctx.$i = ctx.$j + 1
      }
    }

    // Serialize the various indices of the object.
    If (length(obj.$indices) > 0) {
      ctx.$i = 0

      While (ctx.$i < length(obj.$indices)) {
        ctx.$j = ctx.$i + 1
        While (!equal(obj.$indices.charAt(ctx.$j), ListDelimiter)) {
          ctx.$j = ctx.$j + 1
        }

        val name = obj.$indices.substring(ctx.$i, ctx.$j)
        val index = read(obj.key ++ FieldDelimiter ++ name ++ FieldDelimiter ++ "$values")
        prefetch(index)
        ctx.$json = ctx.$json ++ ",\"" ++ name ++ "\":["
        ctx.$k = 0

        While(ctx.$k < length(index)) {
          ctx.$l = ctx.$k + 1
          While(!equal(index.charAt(ctx.$l), ListDelimiter)) {
            ctx.$l = ctx.$l + 1
          }

          val at = index.substring(ctx.$k, ctx.$l)
          val value = read(obj.key ++ FieldDelimiter ++ name ++ FieldDelimiter ++ at)
          ctx.$json = ctx.$json ++ "\"" ++ at ++ "\":\"" ++ value ++ "\","
          ctx.$k = ctx.$l + 1
        }

        ctx.$json = ctx.$json.substring(0, length(ctx.$json) - 1) ++ "]"
        ctx.$i = ctx.$j + 1
      }
    }

    // Place the serialized value into the context.
    ctx.$json = ctx.$json ++ "}"
    ctx += ctx.$json
  }

}
