package amf.graphql.internal.spec.context

import amf.apicontract.client.scala.model.domain.security.SecurityScheme
import amf.apicontract.internal.spec.common.WebApiDeclarations
import amf.apicontract.internal.spec.common.emitter.SpecVersionFactory
import amf.apicontract.internal.spec.common.parser.{SecuritySchemeParser, WebApiContext}
import amf.core.client.scala.config.ParsingOptions
import amf.core.client.scala.model.domain.Shape
import amf.core.client.scala.parse.document.{ParsedReference, ParserContext}
import amf.core.internal.remote.{GraphQL, Spec}
import amf.shapes.internal.spec.common.parser.SpecSyntax
import org.yaml.model.{YNode, YPart}

object GraphQLVersionFactory extends SpecVersionFactory {
  override def securitySchemeParser: (YPart, SecurityScheme => SecurityScheme) => SecuritySchemeParser =
    throw new Exception("GraphQL specs don't support security schemes")
}

object GraphQLWebApiContext {
  object RootTypes extends Enumeration {
    val Query, Subscription, Mutation = Value
  }
}
class GraphQLWebApiContext(override val loc: String,
                           override val refs: Seq[ParsedReference],
                           override val options: ParsingOptions,
                           private val wrapped: ParserContext,
                           private val ds: Option[WebApiDeclarations] = None)
    extends WebApiContext(loc, refs, options, wrapped, ds) {
  override val syntax: SpecSyntax = new SpecSyntax {
    override val nodes: Map[String, Set[String]] = Map()
  }
  override val spec: Spec = GraphQL

  override def link(node: YNode): Either[String, YNode] =
    throw new Exception("GraphQL cannot be used with a SYaml parser")

  override protected def ignore(shape: String, property: String): Boolean = false

  override def autoGeneratedAnnotation(s: Shape): Unit = {}

  override val factory: SpecVersionFactory = GraphQLVersionFactory

}
