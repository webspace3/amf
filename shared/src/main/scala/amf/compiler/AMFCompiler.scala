package amf.compiler

import amf.common.{AMFAST, AMFToken}
import amf.json.JsonLexer
import amf.lexer.AbstractLexer
import amf.oas.OASParser
import amf.parser.{BaseAMFParser, YeastASTBuilder}
import amf.raml.RamlParser
import amf.remote.Mimes._
import amf.remote._
import amf.yaml.YamlLexer

import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AMFCompiler private (val url: String, val remote: Platform, val base: Option[Context], hint: Option[Hint]) {

  private lazy val context: Context                  = base.map(_.update(url)).getOrElse(Context(url))
  private var root: AMFAST                           = _
  private val references: ListBuffer[Future[AMFAST]] = ListBuffer()

  def build(): Future[AMFAST] = {
    remote.resolve(url, base).flatMap(parse)
  }

  def resolveLexer(content: Content): AbstractLexer[AMFToken] = {
    content.mime match {
      case Some(`APPLICATION/JSON`) | Some(`APPLICATION/RAML+JSON`) | Some(`APPLICATION/OPENAPI+JSON`) | Some(
            `APPLICATION/SWAGGER+JSON`) =>
        JsonLexer(content.stream)
      case Some(`APPLICATION/YAML`) | Some(`APPLICATION/RAML+YAML`) | Some(`APPLICATION/OPENAPI+YAML`) | Some(
            `APPLICATION/SWAGGER+YAML`) =>
        YamlLexer(content.stream)
      case _ =>
        hint.getOrElse("") match {
          case RamlYamlHint | OasYamlHint => YamlLexer(content.stream)
          case RamlJsonHint | OasJsonHint => JsonLexer(content.stream)
          case _                          => ??? //TODO handler unkown
        }
    }
  }

  def resolveParser(builder: YeastASTBuilder, content: Content): BaseAMFParser = {
    //TODO
    builder.currentText match {
      case s if s.startsWith("#%RAML") => new RamlParser(builder)
      case _ =>
        content.mime match {
          case Some(`APPLICATION/RAML`) | Some(`APPLICATION/RAML+JSON`) | Some(`APPLICATION/RAML+YAML`) =>
            new RamlParser(builder)
          case Some(`APPLICATION/OPENAPI+JSON`) | Some(`APPLICATION/SWAGGER+JSON`) | Some(`APPLICATION/OPENAPI+YAML`) |
              Some(`APPLICATION/SWAGGER+YAML`) | Some(`APPLICATION/OPENAPI`) | Some(`APPLICATION/SWAGGER`) =>
            new OASParser(builder)
          case _ =>
            hint.getOrElse("") match {
              case RamlYamlHint | RamlJsonHint => new OASParser(builder)
              case OasYamlHint | OasJsonHint   => new RamlParser(builder)
              case _                           => new RamlParser(builder)
            }
        }
    }
  }

  private def parse(content: Content): Future[AMFAST] = {
    val builder = YeastASTBuilder(resolveLexer(content))
    val parser  = resolveParser(builder, content)

    if (root == null) {
      root = builder.root() {
        parser.parse
      }
    }

    builder.references.foreach(link => {
      references += link.resolve(remote, context, hint)
    })

    Future.sequence(references).map(_ => root)
  }
}

object AMFCompiler {
  def apply(url: String, remote: Platform, hint: Option[Hint], context: Option[Context] = None) =
    new AMFCompiler(url, remote, context, hint)
}
