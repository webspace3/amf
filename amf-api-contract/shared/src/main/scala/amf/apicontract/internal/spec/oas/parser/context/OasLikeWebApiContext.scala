package amf.apicontract.internal.spec.oas.parser.context

import amf.aml.internal.semantic.SemanticExtensionsFacadeBuilder
import amf.apicontract.client.scala.model.domain.security.SecurityScheme
import amf.apicontract.client.scala.model.domain.{EndPoint, Operation}
import amf.apicontract.internal.spec.common.OasLikeWebApiDeclarations
import amf.apicontract.internal.spec.common.emitter.SpecVersionFactory
import amf.apicontract.internal.spec.common.parser.{ParsingHelpers, WebApiContext}
import amf.apicontract.internal.spec.oas.parser.domain.{
  OasLikeEndpointParser,
  OasLikeOperationParser,
  OasLikeSecuritySettingsParser,
  OasLikeServerVariableParser
}
import amf.core.client.scala.config.ParsingOptions
import amf.core.client.scala.model.document.ExternalFragment
import amf.core.client.scala.model.domain.Shape
import amf.core.client.scala.parse.document.{ParsedReference, ParserContext}
import amf.shapes.internal.spec.common.parser.{IgnoreAnnotationSchemaValidatorBuilder, IgnoreCriteria, SpecSettings}
import org.yaml.model.{YMap, YMapEntry, YNode}

import scala.collection.mutable
import scala.language.postfixOps

trait OasLikeSpecVersionFactory extends SpecVersionFactory {
  def serverVariableParser(entry: YMapEntry, parent: String): OasLikeServerVariableParser
  // TODO ASYNC complete this
  def operationParser(entry: YMapEntry, adopt: Operation => Operation): OasLikeOperationParser
  def endPointParser(entry: YMapEntry, parentId: String, collector: List[EndPoint]): OasLikeEndpointParser
  def securitySettingsParser(map: YMap, scheme: SecurityScheme): OasLikeSecuritySettingsParser
}

abstract class OasLikeWebApiContext(
    loc: String,
    refs: Seq[ParsedReference],
    options: ParsingOptions,
    private val wrapped: ParserContext,
    private val ds: Option[OasLikeWebApiDeclarations] = None,
    private val operationIds: mutable.Set[String] = mutable.HashSet(),
    val specSettings: SpecSettings
) extends WebApiContext(loc, refs, options, wrapped, ds, specSettings = specSettings) {

  val factory: OasLikeSpecVersionFactory

  def makeCopy(): OasLikeWebApiContext
  private def makeCopyWithJsonPointerContext() = {
    val copy = makeCopy()
    copy.jsonSchemaRefGuide = this.jsonSchemaRefGuide
    copy.indexCache = this.indexCache
    copy
  }

  override val declarations: OasLikeWebApiDeclarations =
    ds.getOrElse(
      new OasLikeWebApiDeclarations(
        refs
          .flatMap(r =>
            if (r.isExternalFragment)
              r.unit.asInstanceOf[ExternalFragment].encodes.parsed.map(node => r.origin.url -> node)
            else None
          )
          .toMap,
        None,
        errorHandler = eh,
        futureDeclarations = futureDeclarations
      )
    )

  override def ignoreCriteria: IgnoreCriteria = OasLikeIgnoreCriteria

  /** Used for accumulating operation ids. returns true if id was not present, and false if operation being added is
    * already present.
    */
  def registerOperationId(id: String): Boolean = operationIds.add(id)

  def navigateToRemoteYNode[T <: OasLikeWebApiContext](
      ref: String
  )(implicit ctx: T): Option[RemoteNodeNavigation[T]] = {
    val nodeOption = jsonSchemaRefGuide.obtainRemoteYNode(ref)
    val rootNode   = jsonSchemaRefGuide.getRootYNode(ref)
    nodeOption.map { node =>
      val newCtx = ctx.makeCopyWithJsonPointerContext().moveToReference(node.location.sourceName).asInstanceOf[T]
      rootNode.foreach(newCtx.setJsonSchemaAST)
      RemoteNodeNavigation(node, newCtx)
    }
  }

  def moveToReference(loc: String): this.type = {
    jsonSchemaRefGuide = jsonSchemaRefGuide.changeJsonSchemaSearchDestination(loc)
    this
  }

  override def autoGeneratedAnnotation(s: Shape): Unit = ParsingHelpers.oasAutoGeneratedAnnotation(s)
}

case class RemoteNodeNavigation[T <: OasLikeWebApiContext](remoteNode: YNode, context: T)

object RemoteNodeNavigation {

  def unapply[T <: OasLikeWebApiContext](arg: RemoteNodeNavigation[T]): Option[(YNode, T)] =
    Some((arg.remoteNode, arg.context))
}
