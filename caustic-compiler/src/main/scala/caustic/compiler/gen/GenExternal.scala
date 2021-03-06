package caustic.compiler.gen

import caustic.compiler.error._
import caustic.compiler.reflect._
import caustic.compiler.util._
import caustic.grammar._

import scala.collection.JavaConverters._

/**
 * Generates external Scala definitions from declaration. Preserves indentation and documentation.
 *
 * @param universe Type universe.
 */
case class GenExternal(universe: Universe) extends CausticBaseVisitor[String] {

  override def visitService(ctx: CausticParser.ServiceContext): String = {
    val name = ctx.Identifier().getText

    i"""${ if (ctx.comment() != null) GenComment.visitComment(ctx.comment()) else "" }
       |case class $name(runtime: Runtime) {
       |
       |  ${ ctx.function().asScala.map(GenExternal(this.universe).visitFunction).mkString("\n") }
       |}
     """
  }

  override def visitStruct(ctx: CausticParser.StructContext): String = {
    val name = ctx.Identifier().getText
    val fields = ctx.parameters().parameter().asScala.map(_.Identifier().getText)
    val types = ctx.parameters().parameter().asScala.map(_.`type`()).map(visitType)

    i"""object $name {
       |
       |  // Implicit Conversions.
       |  implicit def reference(x: $name)(implicit context: Context): Reference[$name$$Internal] = {
       |    val ref = Reference.Local[$name$$Internal](context.label())
       |    ${ fields.map(f => s"ref.get('$f) := x.$f") mkString "\n    " }
       |    ref
       |  }
       |
       |  implicit object $name$$Format extends RootJsonFormat[$name] {
       |
       |    def write(x: $name): JsValue = JsObject(
       |      ${ fields.map(f => s""""$f" -> x.$f.toJson""") mkString ", " }
       |    )
       |
       |    def read(x: JsValue): $name = {
       |      x.asJsObject.getFields(${ fields.map(f => s""""$f"""") mkString ", " }) match {
       |        case Seq(${ fields mkString "," }) =>
       |          $name(${ fields.zip(types) map { case (n, t) => s"$n.convertTo[$t]" } mkString ", " })
       |        case _ => throw DeserializationException("$name expected, but not found.")
       |      }
       |    }
       |
       |  }
       |
       |}
       |
       |import $name._
       |
       |${ if (ctx.comment() != null) GenComment.visitComment(ctx.comment()) else "" }
       |case class $name(
       |  ${ visitParameters(ctx.parameters()) }
       |)
     """
  }

  override def visitFunction(ctx: CausticParser.FunctionContext): String = {
    val name = ctx.Identifier().getText
    val args = ctx.parameters().parameter().asScala.map(_.Identifier().getText)
    val call = s"$name$$Internal(${ args.mkString(", ") })"
    val body = GenType(this.universe).visitType(ctx.`type`()) match {
      case CPointer(_) => s"Return($call.key.asJson)"
      case CUnit => call
      case _ => s"Return($call.asJson)"
    }

    i"""${ if (ctx.comment() != null) GenComment.visitComment(ctx.comment()) else "" }
       |def $name(
       |  ${ visitParameters(ctx.parameters()) }
       |): Try[${ visitType(ctx.`type`()) }] = {
       |  this.runtime evaluate { implicit context =>
       |   $body
       |  } map {
       |    case Text(x) => x
       |    case Real(x) => x.toString
       |    case Flag(x) => x.toString
       |    case Null => "null"
       |  } map {
       |    _.parseJson.convertTo[${ visitType(ctx.`type`()) }]
       |  }
       |}
     """
  }

  override def visitParameters(ctx: CausticParser.ParametersContext): String =
    ctx.parameter().asScala.map(visitParameter).mkString(",\n")

  override def visitParameter(ctx: CausticParser.ParameterContext): String =
    s"${ ctx.Identifier().getText }: ${ visitType(ctx.`type`()) }"

  override def visitType(ctx: CausticParser.TypeContext): String =
    this.universe.find(ctx.getText.replaceAll("\\s", "")) match {
      case Some(_: CPointer) => s"Pointer[${ ctx.getText.dropRight(1)}]"
      case Some(_: CStruct) => s"${ ctx.getText }"
      case Some(CList(x)) => s"scala.List[$x]"
      case Some(CSet(x)) => s"scala.collection.Set[$x]"
      case Some(CMap(k, v)) => s"scala.collection.Map[$k, $v]"
      case Some(CString) => "java.lang.String"
      case Some(CDouble) => "scala.Double"
      case Some(CInt) => "scala.Int"
      case Some(CBoolean) => "scala.Boolean"
      case Some(CUnit) => "scala.Unit"
      case Some(any) => throw Error.Type(s"Field cannot be of type $any.", Trace(ctx))
      case None => throw Error.Type(s"Unknown type ${ ctx.getText }.", Trace(ctx))
    }

}
