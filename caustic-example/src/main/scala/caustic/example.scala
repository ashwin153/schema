// Autogenerated by Caustic Compiler
package caustic.example

import caustic.runtime.service._
import spray.json._
import scala.util.Try

// TODO: Copy block comment from *.acid file.
case class Total(
  value: Int
)

/**
 * A Spray Json serialization protocol for instances of [[Total]].
 */
object Total$JsonProtocol extends DefaultJsonProtocol {
  implicit val TotalFormat = jsonFormat1(Total)
}

import Total$JsonProtocol._
         
// TODO: Copy block comment from *.acid file.
case class Counter(client: Client) {
  
  // Pre-compute function body to reduce allocations and improve performance.
  private val inc$Body = cons(store(text("root/caustic/example/inc@x"), text(x)), load(branch(notEqual(read(add(load("root/caustic/example/inc@x"), text("@value"))), None), write(add(load("root/caustic/example/inc@x"), text("@value")), add(read(add(load("root/caustic/example/inc@x"), text("@value"))), real(1.0))), write(add(load("root/caustic/example/inc@x"), text("@value")), real(1.0)))))
  
  // TODO: Copy block comment from *.acid file.
  def inc(
    x: String
  ): Try[Int] = {
    this.client.execute(inc$Body) map { result =>
      // Extract a Json string from the result.
      if (result.isSetText)
        result.getText
      else if (result.isSetReal)
        result.getReal.toString
      else if (result.isSetFlag)
        result.getFlag.toString
      else
        ""
    } map {
      // Deserialize the result using Spray Json.
      _.parseJson.convertTo[Int]
    }
  }
       
}
         
     