package amf.plugins.document.apicontract.parser.spec.oas

import amf.apicontract.client.scala.model.domain.EndPoint
import amf.apicontract.client.scala.model.domain.api.WebApi
import amf.core.client.scala.model.document.{BaseUnit, DeclaresModel, Document}
import amf.core.client.scala.model.domain.DomainElement
import amf.core.client.scala.model.domain.extensions.CustomDomainProperty
import amf.core.internal.plugins.syntax.{SourceCodeBlock, StringDocBuilder}
import amf.core.internal.render.BaseEmitters.pos
import amf.shapes.client.scala.model.domain.{NodeShape, ScalarShape}

class GrpcEmitterContext(document: BaseUnit) {
  def topLevelMessages: Seq[NodeShape] = messages.filter { s =>
    val declarations = s.name.value().split("\\.").filter(w => w != "" && !w.matches("[a-z].+"))
    declarations.length == 1
  }

  def extensions: Seq[CustomDomainProperty] = document match {
    case dec: DeclaresModel => dec.declares.collect { case cdp: CustomDomainProperty => cdp }
    case _                  => Nil
  }

  def topLevelEnums: Seq[ScalarShape] = enums.filter { s =>
    val declarations = s.name.value().split("\\.").filter(w => w != "" && !w.matches("[a-z].+"))
    declarations.length == 1
  }

  def nestedMessages(shape: NodeShape): Seq[NodeShape] = {
    val currentPath = shape.name.value()
    val currentLevel = currentPath.split("\\.").length
    messages.filter { s =>
      val level = s.name.value().split("\\.").length
      s.name.value().startsWith(currentPath) && (level == currentLevel + 1)
    }
  }

  def nestedEnums(shape: NodeShape): Seq[ScalarShape] = {
    val currentPath = shape.name.value()
    val currentLevel = currentPath.split("\\.").length
    enums.filter { s =>
      val level = s.name.value().split("\\.").length
      s.name.value().startsWith(currentPath) && (level == currentLevel + 1)
    }
  }

  def messages: Seq[NodeShape] = {
    document match {
      case dec: DeclaresModel => dec.declares.map(isMessage).collect { case Some(s) => s}
      case _                  => Nil
    }
  }

  def enums: Seq[ScalarShape] = {
    document match {
      case dec: DeclaresModel => dec.declares.map(isEnum).collect { case Some(s) => s}
      case _                  => Nil
    }
  }

  private def isMessage(s: DomainElement): Option[NodeShape] = {
    s match {
      case n: NodeShape => Some(n)
      case _            => None
    }
  }

  private def isEnum(s: DomainElement): Option[ScalarShape] = {
    s match {
      case s: ScalarShape if s.values.nonEmpty => Some(s)
      case _                                   => None
    }
  }

}

class GrpcDocumentEmitter(document: BaseUnit, builder: StringDocBuilder) extends GrpcEmitter {

  val ctx = new GrpcEmitterContext(document)

  def emit(): Unit = {
    builder.doc { doc =>
      doc += ("syntax = \"proto3\";\n")
      emitReferences(doc)
      doc.list { l =>
        emitPackage(l)
        emitMessages(l)
        emitEnums(l)
        emitServices(l)
        emitExtensions(l)
        emitOptions(webApi, l, ctx)
      }
    }
  }

  def webApi: WebApi = document.asInstanceOf[Document].encodes.asInstanceOf[WebApi]
  def endpoints: Seq[EndPoint] = webApi.endPoints

  def emitReferences(b: StringDocBuilder): Unit = {
    var checkDefaultGoogleDescriptor = false
    // we make the location relative to the location of the unit if we can
    val rootLocation = document.location().getOrElse("").replace("file://", "").split("/").dropRight(1).mkString("/") + "/"
    document.references.collect{ case r if r.location().isDefined => r }.foreach { ref =>
      val refLocation = ref.location().get.replace("file://", "").replace(rootLocation, "")
      if (refLocation.contains("google/protobuf/descriptor.proto\"")) {
        checkDefaultGoogleDescriptor = true
      }
      b += ("import \"" + refLocation + "\";")
    }
    if (!checkDefaultGoogleDescriptor && declaresOptions) {
      b += ("import \"google/protobuf/descriptor.proto\";")
    }
    if (document.references.nonEmpty || declaresOptions) {
      b += "\n"
    }

  }

  def declaresOptions: Boolean = {
    document match {
      case lib: DeclaresModel =>
        lib.declares.exists(_.isInstanceOf[CustomDomainProperty])
      case _                    => false
    }
  }

  def emitPackage(l: StringDocBuilder): SourceCodeBlock = {
    val nameField = if (document.pkg.option().isDefined) {
      document.pkg
    } else {
      webApi.name
    }
    val position = pos(nameField.annotations())
    val name = nameField.option().getOrElse("anonymous")
    val normalizedName = name.toLowerCase.replaceAll("-", "_").replaceAll( " ", "")
    l += (s"package $normalizedName;", position)
  }

  private def emitMessages(l: StringDocBuilder) = {
    ctx.topLevelMessages.map { s => GrpcMessageEmitter(s, l, ctx).emit() }
  }

  private def emitEnums(l: StringDocBuilder) = {
    ctx.topLevelEnums.map { s => GrpcEnumEmitter(s, l, ctx).emit() }
  }

  private def emitServices(l: StringDocBuilder): Unit = {
    endpoints.foreach { ep =>
      GrpcServiceEmitter(ep, l, ctx).emit()
    }
  }

  private def emitExtensions(l: StringDocBuilder): Unit = {
    ctx.extensions.groupBy(c => c.domain.head.value()).foreach { case (domain, extensions) =>
      GrpcExtensionEmitter(extensions, l, domain, ctx).emit()
    }
  }
}
