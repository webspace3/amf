package amf.validation

import amf.apicontract.client.scala._
import amf.apicontract.client.scala.model.domain.api.WebApi
import amf.apicontract.internal.metamodel.domain.{EndPointModel, OperationModel}
import amf.apicontract.internal.spec.async.NotFinishedAsync20ParsePlugin
import amf.core.client.common.transform.PipelineId
import amf.core.client.scala.config.RenderOptions
import amf.core.client.scala.model.document.{BaseUnit, Document}
import amf.core.client.scala.model.domain.extensions.PropertyShape
import amf.core.client.scala.model.domain.{AmfArray, ExternalSourceElement, ScalarNode, Shape}
import amf.core.internal.annotations.{DeclaredElement, Inferred, VirtualElement, VirtualNode}
import amf.core.internal.parser.domain.Annotations
import amf.core.internal.remote.Mimes
import amf.graphql.client.scala.GraphQLConfiguration
import amf.shapes.client.scala.model.domain._
import amf.shapes.internal.annotations.{BaseVirtualNode, TargetName}
import amf.shapes.internal.domain.metamodel.AnyShapeModel
import amf.testing.BaseUnitUtils._
import amf.testing.ConfigProvider.configFor
import org.mulesoft.common.client.lexical.{Position, PositionRange}
import org.scalatest
import org.scalatest.Assertion
import org.scalatest.funsuite.AsyncFunSuite
import org.scalatest.matchers.should.Matchers
import org.yaml.model.{YNodePlain, YScalar}

import scala.concurrent.{ExecutionContext, Future}

class AMFModelAssertionTest extends AsyncFunSuite with Matchers {

  override implicit val executionContext: ExecutionContext = ExecutionContext.Implicits.global

  val basePath                        = "file://amf-cli/shared/src/test/resources/validations"
  val ro: RenderOptions               = RenderOptions().withCompactUris.withPrettyPrint.withSourceMaps
  val graphqlConfig: AMFConfiguration = GraphQLConfiguration.GraphQL().withRenderOptions(ro)
  val ramlConfig: AMFConfiguration    = RAMLConfiguration.RAML10().withRenderOptions(ro)
  val ramlClient: AMFBaseUnitClient   = ramlConfig.baseUnitClient()
  val raml08Config: AMFConfiguration  = RAMLConfiguration.RAML08().withRenderOptions(ro)
  val raml08Client: AMFBaseUnitClient = raml08Config.baseUnitClient()
  val oasConfig: AMFConfiguration     = OASConfiguration.OAS30().withRenderOptions(ro)
  val oasClient: AMFBaseUnitClient    = oasConfig.baseUnitClient()
  val asyncConfig: AMFConfiguration =
    AsyncAPIConfiguration.Async20().withPlugin(NotFinishedAsync20ParsePlugin).withRenderOptions(ro)
  val asyncClient: AMFBaseUnitClient = asyncConfig.baseUnitClient()

  def modelAssertion(
      path: String,
      pipelineId: String = PipelineId.Default,
      transform: Boolean = true
  )(assertion: BaseUnit => Assertion): Future[Assertion] = {
    val client = APIConfiguration.APIWithJsonSchema().baseUnitClient()
    client.parse(path) flatMap { parseResult =>
      if (!transform) assertion(parseResult.baseUnit)
      else {
        val specificClient  = configFor(parseResult.sourceSpec).baseUnitClient()
        val transformResult = specificClient.transform(parseResult.baseUnit, pipelineId)
        assertion(transformResult.baseUnit)
      }
    }
  }

  test("AMF should persist and restore the raw XML schema") {
    val api = s"$basePath/raml/raml-with-xml/api.raml"

    ramlClient.parse(api) flatMap { parseResult =>
      // parsing
      parseResult.conforms shouldBe true
      val schemaShapeType = "http://a.ml/vocabularies/shapes#SchemaShape"
      val xmlType         = "XmlRefSchema"
      val bu              = parseResult.baseUnit
      val types = bu.findByType(schemaShapeType) map { element =>
        element.asInstanceOf[SchemaShape]
      }
      types.length shouldBe 1
      val xml = types.find(_.name.value() == xmlType)
      xml.isDefined shouldBe true
      val raw = xml.get.raw.value()
      raw.isInstanceOf[String] shouldBe true
      raw.isEmpty shouldBe false

      // serialization
      val transformResult = ramlClient.transform(parseResult.baseUnit, PipelineId.Editing)
      val rendered        = ramlClient.render(transformResult.baseUnit, "application/ld+json")

      // restoring
      val client2 = RAMLConfiguration.RAML10().baseUnitClient()
      client2.parseContent(rendered) flatMap { parseResult =>
        val restoredBu = parseResult.baseUnit
        val restoredTypes = restoredBu.findByType(schemaShapeType) map { element =>
          element.asInstanceOf[SchemaShape]
        }
        restoredTypes.length shouldBe 1
        val restoredXml = restoredTypes.find(_.name.value() == xmlType)
        restoredXml.isDefined shouldBe true
        val restoredRaw = restoredXml.get.raw.value()
        restoredRaw.isInstanceOf[String] shouldBe true
        restoredRaw.isEmpty shouldBe false
      }
    }
  }

  test("AMF should persist and restore the raw json schema") {
    val api = s"$basePath/raml/raml-with-json-schema/api.raml"

    ramlClient.parse(api) flatMap { parseResult =>
      // parsing
      parseResult.conforms shouldBe true
      val NodeShapeType = "http://www.w3.org/ns/shacl#NodeShape"
      val typeName      = "JsonSchema"
      val bu            = parseResult.baseUnit
      val nodeShapes = bu.findByType(NodeShapeType) map { element =>
        element.asInstanceOf[NodeShape]
      }
      nodeShapes.length shouldBe 3
      val definedType = nodeShapes.find(_.name.value() == typeName)
      val jsonSchema  = definedType.get.inherits.head
      // this jsonSchema node doesn't have the raw field, only the reference-id to it, and the raw value in the parsed-json-schema annotation.
      val idToRaw = jsonSchema.asInstanceOf[ExternalSourceElement].referenceId
      idToRaw.value().isEmpty shouldBe false

      // serialization
      val transformResult = ramlClient.transform(parseResult.baseUnit, PipelineId.Editing)
      val rendered        = ramlClient.render(transformResult.baseUnit, "application/ld+json")

      // restoring
      val client2 = RAMLConfiguration.RAML10().baseUnitClient()
      client2.parseContent(rendered) flatMap { parseResult =>
        val restoredBu = parseResult.baseUnit
        val restoredNodeShapes = restoredBu.findByType(NodeShapeType) map { element =>
          element.asInstanceOf[NodeShape]
        }
        restoredNodeShapes.length shouldBe 2
        val restoredType       = restoredNodeShapes.find(_.name.value() == typeName)
        val restoredJsonSchema = restoredType.get
        val restoredRaw        = restoredJsonSchema.raw.value()
        restoredRaw.isInstanceOf[String] shouldBe true
        restoredRaw.isEmpty shouldBe false
      }
    }
  }

  // github issue #1086
  test("AMF should not remove well known annotations") {
    modelAssertion(s"$basePath/raml/api-with-annotations.raml") { bu =>
      val ramlAnnotations = getFirstOperation(bu).customDomainProperties
      ramlAnnotations.length shouldBe 2
      val oasTransformResult = oasClient.transform(bu, PipelineId.Compatibility)
      val oasAnnotations     = getFirstOperation(oasTransformResult.baseUnit).customDomainProperties
      oasAnnotations.length shouldBe 2
    }
  }

  // github issue #1121
  test("Declared Raml type with Json Schema should inherit type from it") {
    val ramlApi = s"$basePath/raml/json-schema-scalar-type/json-schema-with-scalar-type.raml"
    modelAssertion(ramlApi, transform = false) { bu =>
      val jsonSchemaType = "http://www.w3.org/2001/XMLSchema#string"
      val declaredTypeWithJsonSchemaNode =
        getFirstDeclaration(bu).asInstanceOf[ScalarShape]
      declaredTypeWithJsonSchemaNode.dataType.value() shouldBe jsonSchemaType
    }
  }

  test("Declared Raml type with Json Schema in external file should inherit type from it") {
    val ramlApi = s"$basePath/raml/json-schema-scalar-type/json-schema-with-scalar-type-in-external-file.raml"
    modelAssertion(ramlApi, transform = false) { bu =>
      val jsonSchemaType = "http://www.w3.org/2001/XMLSchema#string"
      val declaredTypeWithJsonSchemaNode =
        getFirstDeclaration(bu).asInstanceOf[ScalarShape]
      declaredTypeWithJsonSchemaNode.dataType.value() shouldBe jsonSchemaType
    }
  }

  // github issue #1163
  test("Simple inheritance should not delete documentation fields") {
    modelAssertion(s"$basePath/raml/api-with-types.raml", PipelineId.Editing) { bu =>
      val haveNoExamples =
        bu.asInstanceOf[Document]
          .declares
          .filter(s => s.asInstanceOf[AnyShape].examples.isEmpty)
          .map(_.asInstanceOf[AnyShape].name.value())
      val shouldNotHaveExamples = Seq("complex-inheritance-obj", "complex-inheritance-string")
      haveNoExamples == shouldNotHaveExamples shouldBe true
    }
  }

  test("Simple inheritance should not delete documentation fields in uri params") {
    modelAssertion(s"$basePath/raml/uri-params/uri-params.raml") { bu =>
      val parameters    = bu.asInstanceOf[Document].encodes.asInstanceOf[WebApi].endPoints.head.parameters.toList
      val paramExamples = parameters.map(p => p.schema.asInstanceOf[ScalarShape].examples)
      paramExamples.count(_.isEmpty) shouldBe 0
    }
  }

  test("RAML 08 should not delete empty example") {
    val api08 = s"$basePath/raml/empty-example-08.raml"
    val api10 = s"$basePath/raml/empty-example.raml"
    raml08Client.parse(api08) flatMap { parseResult08 =>
      ramlClient.parse(api10) flatMap { parseResult10 =>
        val examplesField08 =
          getFirstPayloadSchema(parseResult08.baseUnit).fields.get(AnyShapeModel.Examples)
        val examplesField =
          getFirstPayloadSchema(parseResult10.baseUnit).fields.get(AnyShapeModel.Examples)
        val range08 = examplesField08.annotations.lexical()
        val range   = examplesField.annotations.lexical()
        range08 shouldEqual range
      }
    }
  }

  test("different datatypes should not fall under simple inheritance") {
    val api = s"$basePath/raml/merge-resourceType.raml"
    ramlClient.parse(api) flatMap { parseResult =>
      val transformResult = ramlClient.transform(parseResult.baseUnit, PipelineId.Editing)
      // parsing or resolution results are not relevant
      ramlClient.validate(transformResult.baseUnit) map (report => report.results.size shouldBe 0)
    }
  }

  test("inline shapes should not include range of parent key") {
    val api = s"$basePath/annotations/inline-shape.yaml"
    modelAssertion(api, transform = false) { bu =>
      val payload                = getFirstResponsePayload(bu, isWebApi = false)
      val schema                 = payload.schema.asInstanceOf[NodeShape]
      val ifField                = schema.fields.fields().find(_.field.toString().endsWith("if")).get
      val inlineShape            = ifField.value.value
      val inlineShapeAnnotations = inlineShape.annotations
      inlineShapeAnnotations.lexical().start shouldBe Position(12, 12)
      inlineShapeAnnotations.lexical().end shouldBe Position(14, 26)
    }
  }

  test("Property name value should have correct lexical") {
    val api = s"$basePath/async20/object-schema.yaml"
    modelAssertion(api, transform = false) { bu =>
      val schema        = getFirstDeclaration(bu)
      val propertyField = schema.fields.fields().find(_.field.toString.endsWith("property")).get
      val propertyNameField = propertyField.value.value
        .asInstanceOf[AmfArray]
        .values
        .head
        .asInstanceOf[PropertyShape]
        .fields
        .fields()
        .find(_.field.toString.endsWith("name"))
        .get
      val nameFieldValue            = propertyNameField.value.value
      val nameFieldValueAnnotations = nameFieldValue.annotations
      nameFieldValueAnnotations.lexical().start shouldBe Position(15, 8)
      nameFieldValueAnnotations.lexical().end shouldBe Position(15, 9)
    }
  }

  // github issue #1119
  test("Applying a trait should not duplicate examples") {
    modelAssertion(s"$basePath/duplicate-examples/trait.raml") { bu =>
      val examples = getFirstResponsePayload(bu).schema.asInstanceOf[NodeShape].examples
      examples.size shouldBe 2
    }
  }

  // github issue #1119 inverse case
  test("Examples with same raw but different names should be present post transformation") {
    modelAssertion(s"$basePath/duplicate-examples/different-names.raml") { bu =>
      val examples = getFirstResponsePayload(bu).schema.asInstanceOf[NodeShape].examples
      examples.size shouldBe 4
    }
  }

  // github issue #1119 complex case
  test("unnamed example should not be equal to same example with name") {
    modelAssertion(s"$basePath/duplicate-examples/unnamed-example.raml") { bu =>
      val examples = getFirstResponsePayload(bu).schema.asInstanceOf[NodeShape].examples
      examples.size shouldBe 2
    }
  }

  test("Expects field should be inferred and Request should be virtual") {
    def isTheOnlyAnnotation[T <: VirtualNode](annotation: Class[T], annotations: Annotations): Boolean = {
      hasAnnotation(annotation, annotations) && annotations.size == 1
    }

    val api = s"$basePath/annotations/api-with-request.yaml"
    modelAssertion(api, transform = false) { bu =>
      val op           = getFirstOperation(bu)
      val expectsField = OperationModel.Request

      isTheOnlyAnnotation(classOf[Inferred], op.fields.getValue(expectsField).annotations) shouldBe true
      isTheOnlyAnnotation(classOf[VirtualElement], op.fields.get(expectsField).annotations) shouldBe true

      op.request.annotations.isVirtual shouldBe true
      hasAnnotation(classOf[BaseVirtualNode], op.request.annotations) shouldBe true

      val requestBodyAnnotations = op.request.annotations.find(classOf[BaseVirtualNode]).get
      requestBodyAnnotations.ast.location.lineFrom shouldBe 13
      requestBodyAnnotations.ast.location.columnFrom shouldBe 6
      requestBodyAnnotations.ast.location.lineTo shouldBe 22
      requestBodyAnnotations.ast.location.columnTo shouldBe 0
    }
  }

  test("When Request has a reference it should have its annotations and should not be virtual") {
    val api = s"$basePath/annotations/api-with-referenced-request.yaml"
    modelAssertion(api, transform = false) { bu =>
      val request = getFirstOperation(bu).request

      request.annotations.lexical().start shouldBe Position(9, 0)
      request.annotations.lexical().end shouldBe Position(10, 0)
      hasAnnotation(classOf[VirtualElement], request.annotations) shouldBe false
    }
  }

  test("Shape's lexical should not only include the referenced type") {
    val api = s"$basePath/annotations/type-reference.raml"
    modelAssertion(api, transform = false) { bu =>
      val typeWithReference = getDeclarations(bu).last
      typeWithReference.annotations.lexical().start shouldBe Position(6, 2)
      typeWithReference.annotations.lexical().end shouldBe Position(7, 0)
    }
  }

  test("Endpoint parameters with default annotation should be replaced during transformation") {
    val api = s"$basePath/parameters/param-in-resource-types.raml"
    modelAssertion(api) { bu =>
      val endpoint   = getFirstEndpoint(bu)
      val parameters = endpoint.parameters.toList
      parameters.size shouldBe 1
      parameters.head.description.value() shouldBe "Name of the logger whose level is to be changed."
    }
  }

  test("xml shape from type should have location annotation") {
    val api = s"$basePath/annotations/xml/api.raml"
    modelAssertion(api, transform = false) { bu =>
      val declaredXmlSchema = getFirstDeclaration(bu)
      declaredXmlSchema.annotations.location().isDefined shouldBe true
      declaredXmlSchema.annotations.lexical() shouldEqual PositionRange((6, 2), (9, 0))
    }
  }

  test("xml shape from payload should have location annotation") {
    val api = "file://amf-cli/shared/src/test/resources/resolution/08/included-schema-and-example/api.raml"
    modelAssertion(api, transform = false) { bu =>
      val xmlSchemaAnnotations = getFirstRequestPayload(bu).schema.asInstanceOf[SchemaShape].annotations
      xmlSchemaAnnotations.location().isDefined shouldBe true
      xmlSchemaAnnotations.lexical() shouldEqual PositionRange((8, 6), (10, 49))
    }
  }

  // W-10758138
  test("Fragments should have isInferred annotation in Encodes field") {
    val api = s"$basePath/raml/simple-datatype/datatype.raml"
    modelAssertion(api, transform = false) { bu =>
      val encodesField = bu.fields.fields().head.value
      encodesField.annotations.nonEmpty && encodesField.isInferred shouldBe true
    }
  }

  // W-11210133
  test("semex and normal annotations should have the same lexical") {
    ramlConfig.withDialect(s"$basePath/raml/semantic-extensions/dialect.yaml") flatMap { config =>
      val client = config.baseUnitClient()
      client.parse(s"$basePath/raml/semantic-extensions/api.raml") map { parseResult =>
        val bu          = parseResult.baseUnit
        val annotations = getDeclarations(bu).last.customDomainProperties
        val lexicals    = annotations map (_.annotations.lexical())
        // both lexicals should start in column 4
        lexicals map (_.start.column) exists (_ != 4) shouldBe false
      }
    }
  }

  test("Declared union should have DeclaredElement annotation") {
    val api = s"$basePath/raml/declared-element-annotation-union.raml"
    modelAssertion(api, PipelineId.Editing) { bu =>
      val union = getFirstDeclaration(bu)
      union.annotations.contains(classOf[DeclaredElement]) shouldBe true
    }
  }

  // W-11350149
  test("implicit path parameter location in RAML") {
    val ramlApi = s"$basePath/raml/uri-params/implicit-path-param.raml"
    modelAssertion(ramlApi, PipelineId.Editing) { bu =>
      val endPoint     = getEndpoints(bu).last
      val paramField   = endPoint.fields.get(EndPointModel.Parameters).asInstanceOf[AmfArray]
      val params       = paramField.values
      val virtualParam = params.head
      virtualParam.annotations.isVirtual shouldBe true

      val virtualLexical        = virtualParam.annotations.lexical()
      val correctVirtualLexical = PositionRange((6, 3), (6, 12))
      virtualLexical.compareTo(correctVirtualLexical) shouldBe 0
    }
  }

  // W-11928480
  test("explicit & implicit baseUri path params in RAML") {
    val ramlApi = s"$basePath/raml/uri-params/base-uri-params.raml"
    modelAssertion(ramlApi, PipelineId.Editing) { bu =>
      val server             = getServers(bu).head
      val virtualServerParam = server.variables.find(_.annotations.isVirtual).get

      val serverParamLexical        = virtualServerParam.annotations.lexical()
      val correctServerParamLexical = PositionRange((6, 33), (6, 48))
      serverParamLexical.compareTo(correctServerParamLexical) shouldBe 0
    }
  }

  // W-11337671
  test("OAS 3 nullable schema should have examples at union level") {
    val oasApi = s"$basePath/oas3/nullable-example.yaml"
    modelAssertion(oasApi, PipelineId.Editing) { bu =>
      val payloadSchema = getFirstResponsePayload(bu).schema.asInstanceOf[UnionShape]
      payloadSchema.examples.size shouldBe 1
      payloadSchema.anyOf.head.asInstanceOf[ScalarShape].examples.size shouldBe 0
    }
  }

  test("OAS2 required parameter should have required field set to true") {
    val api = s"$basePath/oas2/payload-required-field.yaml"
    modelAssertion(api, transform = false) { bu =>
      val payload = getFirstRequestPayload(bu)
      payload.required.value() shouldBe true
    }
  }

  test("TargetName annotation has the name of the schema in the external file") {
    val api = s"$basePath/annotations/target-name/api.yaml"
    modelAssertion(api, transform = false) { bu =>
      val schema     = getFirstDeclaration(bu).asInstanceOf[AnyShape]
      val newName    = schema.name.value()
      val targetName = schema.annotations.find(classOf[TargetName]).map(_.name.asInstanceOf[YNodePlain].value)
      newName shouldBe "new-name"
      targetName shouldBe Some(YScalar("original-name"))
    }
  }

  test("OAS 2 response schema should have lexical in editing pipeline") {
    val api = s"$basePath/oas2/response-payload-lexical.yaml"
    modelAssertion(api, pipelineId = PipelineId.Editing) { bu =>
      val payload        = getFirstResponsePayload(bu)
      val payloadLexical = payload.annotations.lexical()
      val schemaLexical  = payload.schema.annotations.lexical()
      payloadLexical shouldEqual schemaLexical
    }
  }

  test("RAML Request and Payload virtual elements should have lexical in editing pipeline") {
    val api = s"$basePath/raml/optional-scalar.raml"
    modelAssertion(api, pipelineId = PipelineId.Editing) { bu =>
      getFirstRequest(bu).annotations.lexical() shouldBe PositionRange(Position(12, 9), Position(15, 0))
      getFirstRequestPayload(bu).annotations.lexical() shouldBe PositionRange(Position(13, 6), Position(15, 0))
    }
  }

  test("Assert ast annotations aren't modified in allOf") {
    val api = s"$basePath/json-schema-lexical/allOf-sourceypart-is-ast.json"
    modelAssertion(api, transform = false) { unit =>
      val obtainedAst: String = sourcePartOf(unit, _.declares(1).asInstanceOf[Shape].and(1))
      val expectedAst         = """{"properties": {}}"""
      obtainedAst.trim shouldEqual expectedAst.trim
    }
  }

  test("Assert ast annotations aren't modified in anyOf") {
    val api = s"$basePath/json-schema-lexical/anyOf-sourceypart-is-ast.json"
    modelAssertion(api, transform = false) { unit =>
      val obtainedAst: String = sourcePartOf(unit, _.declares(1).asInstanceOf[Shape].or(1))
      val expectedAst         = """{"properties": {}}"""
      obtainedAst.trim shouldEqual expectedAst.trim
    }
  }

  test("Assert ast annotations aren't modified in oneOf") {
    val api = s"$basePath/json-schema-lexical/oneOf-sourceypart-is-ast.json"
    modelAssertion(api, transform = false) { unit =>
      val obtainedAst: String = sourcePartOf(unit, _.declares(1).asInstanceOf[Shape].xone(1))
      val expectedAst         = """{"properties": {}}"""
      obtainedAst.trim shouldEqual expectedAst.trim
    }
  }

  test("Assert union custom domain properties are not propagated to members") {

    def assertForAllTypes(shapes: Shape*)(assertion: Shape => Unit): Unit = {
      shapes.foreach(assertion)
    }

    val api = s"$basePath/raml/union-annots-dont-override-member-annots/api.raml"
    val cp  = new scalatest.Checkpoints.Checkpoint()
    modelAssertion(api, pipelineId = PipelineId.Editing) { unit =>
      val doc   = unit.asInstanceOf[Document]
      val union = doc.declares.collect { case union: UnionShape => union }.head
      val type1 = doc.declares.collect { case node: NodeShape if node.name.value() == "Type1" => node }.head
      val type2 = doc.declares.collect { case node: NodeShape if node.name.value() == "Type2" => node }.head
      union.customDomainProperties.length shouldBe 2
      assertForAllTypes(type1, union.anyOf.head) { shape =>
        cp { shape.customDomainProperties.length shouldBe 1 }
        cp { shape.customDomainProperties.head.extension.asInstanceOf[ScalarNode].value.value() shouldEqual "Type1" }
      }
      assertForAllTypes(type2, union.anyOf(1)) { shape =>
        cp { shape.customDomainProperties.length shouldBe 1 }
        cp { shape.customDomainProperties.head.name.value() shouldEqual "flag" }
      }
      cp.reportAll()
      succeed
    }
  }

  // W-13014769
  test("Servers with same url should have different IDs") {
    val api = s"$basePath/oas3/same-server-urls.yaml"

    def checkServers(bu: BaseUnit) = {
      val servers = getApi(bu).servers.map(s => (s.url, s.description))
      servers.map(s => s._2.toString).intersect(Seq("Prod", "Pre-prod")).size shouldBe 2
    }

    oasClient.parse(api).flatMap { parseResult =>
      val bu                          = oasClient.transform(parseResult.baseUnit, PipelineId.Editing).baseUnit
      val render                      = oasClient.render(bu, Mimes.`application/ld+json`)
      val renderWithoutTransformation = oasClient.render(parseResult.baseUnit, Mimes.`application/ld+json`)

      checkServers(parseResult.baseUnit)

      oasClient.parseContent(renderWithoutTransformation).map { cycleParseResult =>
        checkServers(cycleParseResult.baseUnit)
      }

      oasClient.parseContent(render).map { cycleParseResult =>
        checkServers(cycleParseResult.baseUnit)
      }
    }
  }

  // W-13595824
  test("transforming raml with 3+ unions should flatten union members") {
    val api = "file://amf-cli/shared/src/test/resources/upanddown/raml10/unions/triple-unions.raml"
    ramlClient.parse(api) flatMap { parseResult =>
      val parseBU      = parseResult.baseUnit
      val transformBU  = ramlClient.transform(parseBU, pipeline = PipelineId.Editing).baseUnit
      val declarations = getDeclarations(transformBU)

      val unionInArray      = declarations(1).asInstanceOf[ArrayShape]
      val unionInArrayUnion = unionInArray.items.asInstanceOf[UnionShape]

      val unionInProperty = declarations(2).asInstanceOf[NodeShape]
      val complexProperty = unionInProperty.properties.head
      val complexRange    = complexProperty.range
      val complexUnion    = complexRange.asInstanceOf[UnionShape]

      val unionInArrayProperty = declarations(3).asInstanceOf[NodeShape]
      val complexArrayProperty = unionInArrayProperty.properties.head
      val complexArrayRange    = complexArrayProperty.range
      val complexArrayUnion    = complexArrayRange.asInstanceOf[ArrayShape].items.asInstanceOf[UnionShape]

      unionInArrayUnion.anyOf.length shouldBe 3
      complexUnion.anyOf.length shouldBe 3
      complexArrayUnion.anyOf.length shouldBe 3
    }
  }

  // W-13595824
  test("transforming raml with nested UnionShapes should NOT flatten union members") {
    val api = "file://amf-cli/shared/src/test/resources/union/inner-union.raml"
    ramlClient.parse(api) flatMap { parseResult =>
      val transformBU  = ramlClient.transform(parseResult.baseUnit, pipeline = PipelineId.Editing).baseUnit
      val declarations = getDeclarations(transformBU)

      // in parsing they are an union that inherits an union with anyOf of a ScalarShape and an UnionShape with link-target to the next Union (SomeType, OtherType)
      val unionType = declarations.head.asInstanceOf[UnionShape]
      val someType  = declarations(1).asInstanceOf[UnionShape]

      // in parsing it's an union that inherits an union with scalar and nil shape
      val otherType = declarations(2).asInstanceOf[UnionShape]

      // after transformation they all should be an Union with an anyOf with 2 members
      unionType.anyOf.length shouldBe 2
      someType.anyOf.length shouldBe 2
      otherType.anyOf.length shouldBe 2
    }
  }

  // W-13595824
  test("transforming raml with 3+ unions should NOT duplicate IDs when flattening members") {
    val api = s"$basePath/raml/triple-union.raml"
    ramlClient.parse(api) flatMap { parseResult =>
      val transformBU          = ramlClient.transform(parseResult.baseUnit, pipeline = PipelineId.Editing).baseUnit
      val declarations         = getDeclarations(transformBU)
      val unionInArrayProperty = declarations.last.asInstanceOf[NodeShape]
      val complexArrayProperty = unionInArrayProperty.properties.head
      val complexArrayRange    = complexArrayProperty.range
      val complexArrayUnion    = complexArrayRange.asInstanceOf[ArrayShape].items.asInstanceOf[UnionShape]

      complexArrayUnion.anyOf.map(_.id).distinct.length shouldBe 3
    }
  }

  // W-12689955
  test("async add channel servers transformation") {
    val api = s"$basePath/async20/validations/channel-servers.yaml"
    asyncClient.parse(api) flatMap { parseResult =>
      val transformResult = asyncClient.transform(parseResult.baseUnit)
      val transformBU     = transformResult.baseUnit
      val endpoint        = getFirstEndpoint(transformBU, isWebApi = false)
      val endpointServers = endpoint.servers
      getApi(transformBU, isWebApi = false).servers.intersect(endpointServers).size shouldBe 3
      transformResult.results.size shouldBe 1
    }
  }

  // W-12689955
  test("async2.0 should not emit server channels if not specified") {
    val api = s"$basePath/async20/validations/channel-servers-async20.yaml"
    asyncClient.parse(api) flatMap { parseResult =>
      val transformResult = asyncClient.transform(parseResult.baseUnit)
      val transformBU     = transformResult.baseUnit
      val endpoint        = getFirstEndpoint(transformBU, isWebApi = false)
      val endpointServers = endpoint.servers
      // channel should have all servers linked
      endpointServers.size shouldBe 3

      val render          = asyncClient.render(transformBU)
      val serversInRender = render.linesIterator.filter(_.contains("servers:")).toArray
      // but only the declared root servers should be present in the rendered API
      serversInRender.length shouldBe 1
    }
  }
}
