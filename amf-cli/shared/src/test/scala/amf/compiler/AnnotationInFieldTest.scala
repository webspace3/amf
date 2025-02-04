package amf.compiler

import amf.apicontract.client.scala.model.domain.api.WebApi
import amf.apicontract.client.scala.model.domain.templates.ResourceType
import amf.apicontract.client.scala.model.domain.{Parameter, Response}
import amf.apicontract.client.scala.transform.AbstractElementTransformer
import amf.apicontract.client.scala.{AMFConfiguration, OASConfiguration}
import amf.core.client.scala.errorhandling.IgnoringErrorHandler
import amf.core.client.scala.model.document.Document
import amf.core.client.scala.model.domain.NamedDomainElement
import amf.core.client.scala.model.domain.extensions.PropertyShape
import amf.core.internal.annotations.{LexicalInformation, ReferenceTargets, SourceAST}
import amf.core.internal.parser.domain.Annotations
import amf.core.internal.remote.{Oas20YamlHint, Oas30YamlHint, Raml10YamlHint}
import amf.shapes.client.scala.config.JsonSchemaConfiguration
import amf.shapes.client.scala.model.domain.NodeShape
import amf.shapes.internal.annotations.ExternalJsonSchemaShape
import org.mulesoft.common.client.lexical.PositionRange
import org.scalatest.funsuite.AsyncFunSuite

import scala.concurrent.ExecutionContext

class AnnotationInFieldTest extends AsyncFunSuite with CompilerTestBuilder {
  override implicit val executionContext: ExecutionContext = ExecutionContext.Implicits.global

  override def defaultConfig: AMFConfiguration =
    super.defaultConfig.withErrorHandlerProvider(() => IgnoringErrorHandler)
  test("test source and lexical info in response name oas") {
    for {
      unit <- build(
        "file://amf-cli/shared/src/test/resources/nodes-annotations-examples/oas-responses.yaml",
        Oas20YamlHint
      )
    } yield {
      val document = unit.asInstanceOf[Document]
      document.declares.collectFirst({ case r: Response => r }) match {
        case Some(res) => assertAnnotationsInName(res.id, res)
        case None      => fail("Any response declared found")
      }

      val responses = document.encodes.asInstanceOf[WebApi].endPoints.head.operations.head.responses
      responses.foreach { r =>
        assertAnnotationsInName(r.id, r)
      }
      succeed
    }
  }

  test("test source and lexical info in parameter name oas") {
    for {
      unit <- build(
        "file://amf-cli/shared/src/test/resources/nodes-annotations-examples/oas-parameters.yaml",
        Oas20YamlHint
      )
    } yield {
      val document = unit.asInstanceOf[Document]
      document.declares.collectFirst({ case r: Parameter => r }) match {
        case Some(p) => assertAnnotationsInName(p.id, p)
        case None    => fail("Any parameter declared found")
      }
      succeed
    }
  }

  test("test empty oas parameter") {
    for {
      unit <- build(
        "file://amf-cli/shared/src/test/resources/nodes-annotations-examples/empty-parameter.json",
        Oas20YamlHint
      )
    } yield {
      val document = unit.asInstanceOf[Document]
      document.encodes.asInstanceOf[WebApi].endPoints.head.parameters.headOption match {
        case Some(p) => findLexical(p.id, p.annotations)
        case None    => fail("Any parameter declared found")
      }
      succeed
    }
  }

  test("test annotation at property path") {
    for {
      unit <- build(
        "file://amf-cli/shared/src/test/resources/nodes-annotations-examples/property-path.yaml",
        Oas20YamlHint
      )
    } yield {
      val document                       = unit.asInstanceOf[Document]
      val properties: Seq[PropertyShape] = document.declares.head.asInstanceOf[NodeShape].properties
      assertRange(
        properties.head.range.name.annotations().find(classOf[LexicalInformation]).get.range,
        PositionRange((7, 8), (7, 9))
      )
      assertRange(
        properties
          .find(_.name.value() == "n")
          .get
          .range
          .name
          .annotations()
          .find(classOf[LexicalInformation])
          .get
          .range,
        PositionRange((9, 8), (9, 9))
      )
      assertRange(
        properties
          .find(_.name.value() == "a")
          .get
          .range
          .name
          .annotations()
          .find(classOf[LexicalInformation])
          .get
          .range,
        PositionRange((11, 8), (11, 9))
      )
      assertRange(
        properties
          .find(_.name.value() == "k")
          .get
          .range
          .name
          .annotations()
          .find(classOf[LexicalInformation])
          .get
          .range,
        PositionRange((14, 8), (14, 9))
      )
      succeed
    }
  }

  test("test annotation at raml property path") {
    for {
      unit <- build(
        "file://amf-cli/shared/src/test/resources/nodes-annotations-examples/property-path.raml",
        Raml10YamlHint
      )
    } yield {
      val document                       = unit.asInstanceOf[Document]
      val properties: Seq[PropertyShape] = document.declares.head.asInstanceOf[NodeShape].properties

      assertRange(
        properties.head.range.name.annotations().find(classOf[LexicalInformation]).get.range,
        PositionRange((8, 6), (8, 7))
      )
      assertRange(
        properties
          .find(_.name.value() == "n")
          .get
          .range
          .name
          .annotations()
          .find(classOf[LexicalInformation])
          .get
          .range,
        PositionRange((9, 6), (9, 7))
      )
      assertRange(
        properties
          .find(_.name.value() == "a")
          .get
          .range
          .name
          .annotations()
          .find(classOf[LexicalInformation])
          .get
          .range,
        PositionRange((11, 6), (11, 7))
      )
      assertRange(
        properties
          .find(_.name.value() == "k")
          .get
          .range
          .name
          .annotations()
          .find(classOf[LexicalInformation])
          .get
          .range,
        PositionRange((12, 6), (12, 7))
      )

      succeed
    }
  }

  test("test raml resource type position") {
    for {
      unit <- build(
        "file://amf-cli/shared/src/test/resources/nodes-annotations-examples/resource-type.raml",
        Raml10YamlHint
      )
    } yield {
      val document = unit.asInstanceOf[Document]
      val point = AbstractElementTransformer.asEndpoint(
        document,
        document.declares.head.asInstanceOf[ResourceType],
        defaultConfig
      )
      assertRange(point.annotations.find(classOf[LexicalInformation]).get.range, PositionRange((6, 2), (9, 12)))
      assertRange(point.path.annotations().find(classOf[LexicalInformation]).get.range, PositionRange((6, 2), (6, 5)))
      succeed
    }
  }

  test("test raml ReferenceTarget annotations - ExternalFragment") {
    val uri = "file://amf-cli/shared/src/test/resources/nodes-annotations-examples/reference-targets/"
    for {
      unit <- build(s"${uri}root.raml", Raml10YamlHint)
    } yield {
      val targets = unit.annotations.find(classOf[ReferenceTargets]).map(_.targets).getOrElse(Map.empty)
      assert(targets.size == 1)
      assert(targets.head._1 == s"${uri}reference.json")
      assert(targets.head._2 == List(PositionRange((6, 14), (6, 28))))
      succeed
    }
  }

  test("test raml ReferenceTarget annotations - DataType") {
    val uri = "file://amf-cli/shared/src/test/resources/nodes-annotations-examples/reference-targets/"
    for {
      unit <- build(s"${uri}root-2.raml", Raml10YamlHint)
    } yield {
      val targets = unit.annotations.find(classOf[ReferenceTargets]).map(_.targets).getOrElse(Map.empty)
      assert(targets.size == 1)
      assert(targets.head._1 == s"${uri}reference.raml")
      assert(targets.head._2 == List(PositionRange((6, 14), (6, 28))))
      succeed
    }
  }

  test("test raml ReferenceTarget annotations - double External") {
    val uri = "file://amf-cli/shared/src/test/resources/nodes-annotations-examples/reference-targets/"
    for {
      unit <- build(s"${uri}root-3.raml", Raml10YamlHint)
    } yield {
      val targets = unit.annotations.find(classOf[ReferenceTargets]).map(_.targets).getOrElse(Map.empty)
      val reftargets =
        unit.references.head.annotations.find(classOf[ReferenceTargets]).map(_.targets).getOrElse(Map.empty)

      assert(targets.size == 1)
      assert(targets.head._1 == s"${uri}reference-1.yaml")
      assert(targets.head._2 == List(PositionRange((6, 14), (6, 30))))

      assert(reftargets.size == 1)
      assert(reftargets.head._1 == s"${uri}reference.json")
      assert(reftargets.head._2 == List(PositionRange((1, 15), (1, 29))))

      succeed
    }
  }

  test("test raml ReferenceTarget annotations - double inclusion of same file") {
    val uri = "file://amf-cli/shared/src/test/resources/nodes-annotations-examples/reference-targets/"
    for {
      unit <- build(s"${uri}root-4.raml", Raml10YamlHint)
    } yield {
      val targets = unit.annotations.find(classOf[ReferenceTargets]).map(_.targets).getOrElse(Map.empty)

      assert(targets.size == 1)
      assert(targets.head._1 == s"${uri}example.json")
      assert(targets.head._2 == List(PositionRange((9, 22), (9, 34)), PositionRange((21, 30), (21, 42))))

      succeed
    }
  }

  test("test raml ReferenceTarget annotations coincides with references (invalid inclusion)") {
    val uri =
      "file://amf-cli/shared/src/test/resources/nodes-annotations-examples/reference-targets/invalid-include/"
    for {
      unit <- build(s"${uri}api.raml", Raml10YamlHint)
    } yield {
      val targets = unit.annotations.find(classOf[ReferenceTargets]).map(_.targets).getOrElse(Map.empty)

      assert(targets.size == 1)
      assert(unit.references.size == 1)
      assert(unit.references.head.location().contains(targets.head._1))

      succeed
    }
  }

  test("Test ExternalJsonSchemaShape in JSON ref to shape in external fragment") {
    val uri = "file://amf-cli/shared/src/test/resources/nodes-annotations-examples/ref-to-shape/api.yaml"
    for {
      unit <- build(uri, Oas20YamlHint)
    } yield {
      val shape = unit
        .asInstanceOf[Document]
        .encodes
        .asInstanceOf[WebApi]
        .endPoints
        .head
        .operations
        .head
        .responses
        .head
        .payloads
        .head
        .schema

      val annotation = shape.annotations.find(classOf[ExternalJsonSchemaShape])
      assert(annotation.nonEmpty)
      assert(annotation.get.original != null)

      succeed
    }
  }

  test("test JSON Schema Document ReferenceTarget annotations - range should be substring") {
    val uri    = "file://amf-cli/shared/src/test/resources/nodes-annotations-examples/reference-targets/json-schema"
    val config = JsonSchemaConfiguration.JsonSchema()
    for {
      unit <- config.baseUnitClient().parse(s"$uri/schema.json").map(_.baseUnit)
    } yield {
      val targets = unit.annotations.find(classOf[ReferenceTargets]).map(_.targets).getOrElse(Map.empty)

      assert(targets.size == 1)
      assert(targets.head._1 == s"$uri/schemas/subschema.json")
      assert(targets.head._2 == List(PositionRange((7, 15), (7, 37))))

      succeed
    }
  }

  test("test OAS API ReferenceTarget annotations - range should be substring") {
    val uri    = "file://amf-cli/shared/src/test/resources/nodes-annotations-examples/reference-targets/oas3"
    val config = OASConfiguration.OAS30()
    for {
      unit <- build(s"$uri/api.yaml", Oas30YamlHint)
    } yield {
      val targets = unit.annotations.find(classOf[ReferenceTargets]).map(_.targets).getOrElse(Map.empty)

      assert(targets.size == 1)
      assert(targets.head._1 == s"$uri/schemas/subschema.json")
      assert(targets.head._2 == List(PositionRange((16, 22), (16, 44))))

      succeed
    }
  }

  private def assertRange(actual: PositionRange, expected: PositionRange) = {
    assert(actual.start.line == expected.start.line)
    assert(actual.start.column == expected.start.column)
    assert(actual.end.line == expected.end.line)
    assert(actual.end.column == expected.end.column)
  }

  private def assertAnnotationsInName(id: String, element: NamedDomainElement): Unit = {
    val annotations = element.name.annotations()
    findLexical(id, annotations)
    findSourceAST(id, annotations)
  }
  private def findLexical(id: String, annotations: Annotations): Unit =
    if (!annotations.contains(classOf[LexicalInformation]))
      fail(s"LexicalInformation annotation not found for name in response $id")

  private def findSourceAST(id: String, annotations: Annotations): Unit =
    if (!annotations.contains(classOf[SourceAST])) fail(s"SourceAST annotation not found for name in response $id")

}
