package amf.plugins.document.webapi.parser.spec.common

import amf.core.model.document.ExternalFragment
import amf.core.model.domain.{DataNode, LinkNode, ScalarNode, ArrayNode => DataArrayNode, ObjectNode => DataObjectNode}
import amf.core.parser.{Annotations, _}
import amf.core.vocabulary.Namespace
import org.yaml.model._
import org.yaml.parser.YamlParser
import amf.core.utils._

import scala.collection.mutable
import scala.collection.mutable.ListBuffer

/**
  * We need to generate unique IDs for all data nodes if the name is not set
  */
class IdCounter {
  private var c = 0

  def genId(id: String): String = {
    c += 1
    s"${id}_$c"
  }

  def reset(): Unit = c = 0
}

/**
  * Parse an object as a fully dynamic value.
  */
case class DataNodeParser(node: YNode,
                          parameters: AbstractVariables = AbstractVariables(),
                          parent: Option[String] = None,
                          idCounter: IdCounter = new IdCounter)(implicit ctx: ParserContext) {
  def parse(): DataNode = {
    node.tag.tagType match {
      case YType.Str =>
        if (node.as[YScalar].text.matches("^\\d{2}:\\d{2}(:\\d{2})?$")) {
          parseScalar(node.as[YScalar], "time")
        } else if (node.as[YScalar].text.matches("^\\d{4}-\\d{1,2}-\\d{1,2}?$")) {
          parseScalar(node.as[YScalar], "date")
        } else {
          parseScalar(node.as[YScalar], "string")
        }
      case YType.Int   => parseScalar(node.as[YScalar], "integer")
      case YType.Float => parseScalar(node.as[YScalar], "float")
      case YType.Bool  => parseScalar(node.as[YScalar], "boolean")
      case YType.Null  => parseScalar(node.as[YScalar], "nil")
      case YType.Seq   => parseArray(node.as[Seq[YNode]], node)
      case YType.Map   => parseObject(node.as[YMap])
      case YType.Timestamp =>
        if (node.as[YScalar].text.indexOf(":") > -1) {
          parseScalar(node.as[YScalar], "dateTime")
        } else {
          parseScalar(node.as[YScalar], "date")
        }

      // Included external fragment
      case _ if node.tagType == YType.Include => parseInclusion(node)

      case other =>
        throw new Exception(s"Cannot parse data node from AST structure $other")
    }
  }

  protected def parseInclusion(node: YNode): DataNode = {
    node.value match {
      case reference: YScalar =>
        ctx.refs.find(ref => ref.origin.url == reference.text) match {
          case Some(ref) if ref.unit.isInstanceOf[ExternalFragment] =>
            val includedText = ref.unit.asInstanceOf[ExternalFragment].encodes.raw
            parseIncludedAST(includedText)
          case _ =>
            parseLink(reference.text)
        }
      case _ =>
        parseLink(node.value.toString)
    }
  }

  def parseIncludedAST(raw: String): DataNode = {
    YamlParser(raw).withIncludeTag("!include").parse().find(_.isInstanceOf[YNode]) match {
      case Some(node: YNode) => DataNodeParser(node, parameters, parent, idCounter).parse()
      case _                 => ScalarNode(raw, Some((Namespace.Xsd + "string").iri())).withId(parent.getOrElse("") + "/included")
    }
  }

  /**
    * Generates a new LinkNode base on the text of a label and the fragments in the context
    * @param linkText local text pointing to fragment
    * @return the parsed LinkNode
    */
  protected def parseLink(linkText: String): LinkNode = {
    if (linkText.contains(":")) {
      LinkNode(linkText, linkText).withId(parent.getOrElse("") + "/included")
    } else {
      val localUrl = parent.getOrElse("#").split("#").head
      val leftLink = if(localUrl.endsWith("/")) localUrl else s"${baseUrl(localUrl)}/"
      val rightLink = if (linkText.startsWith("/")) linkText.drop(1) else linkText
      val finalLink = normalizeUrl(leftLink + rightLink)
      LinkNode(linkText, finalLink).withId(parent.getOrElse("") + "/included")
    }
  }

  protected def baseUrl(url: String): String = {
    if (url.contains("://")) {
      val protocol = url.split("://").head
      val path = url.split("://").last
      val remaining =  path.split("/").dropRight(1)
      s"$protocol://${remaining.mkString("/")}"
    } else {
      url.split("/").dropRight(1).mkString("/")
    }
  }

  protected def normalizeUrl(url: String): String = {
    if (url.contains("://")) {
      val protocol = url.split("://").head
      val path = url.split("://").last
      val remaining =  path.split("/")
      var stack: ListBuffer[String] = new ListBuffer[String]()
      remaining.foreach {
        case "."   => // ignore
        case ".."  => stack = stack.dropRight(1)
        case other => stack += other
      }
      s"$protocol://${stack.mkString("/")}"
    } else {
      url
    }
  }


  protected def parseScalar(ast: YScalar, dataType: String): DataNode = {
    val node = ScalarNode(ast.text, Some((Namespace.Xsd + dataType).iri()), Annotations(ast))
      .withName(idCounter.genId("scalar"))
    parent.foreach(node.adopted)
    parameters.parseVariables(ast)
    node
  }

  protected def parseArray(seq: Seq[YNode], ast: YPart): DataNode = {
    val node = DataArrayNode(Annotations(ast)).withName(idCounter.genId("array"))
    parent.foreach(node.adopted)
    seq.foreach { v =>
      val element = DataNodeParser(v, parameters, Some(node.id), idCounter).parse().forceAdopted(node.id)
      node.addMember(element)
    }
    node
  }

  protected def parseObject(value: YMap): DataNode = {
    val node = DataObjectNode(Annotations(value)).withName(idCounter.genId("object"))
    parent.foreach(node.adopted)
    value.entries.map { ast =>
      val key = ast.key.as[YScalar].text
      parameters.parseVariables(key)
      val value               = ast.value
      val propertyAnnotations = Annotations(ast)

      val propertyNode = DataNodeParser(value, parameters, Some(node.id), idCounter).parse().forceAdopted(node.id)
      node.addProperty(key.urlEncoded, propertyNode, propertyAnnotations)
    }
    node
  }
}
