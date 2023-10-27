package amf.shapes.internal.spec.oas.parser

import amf.core.client.scala.errorhandling.AMFErrorHandler
import amf.core.client.scala.model.DataType
import amf.core.client.scala.model.domain._
import amf.core.internal.annotations.{ExplicitField, NilUnion}
import amf.core.internal.datanode.{DataNodeParser, ScalarNodeParser}
import amf.core.internal.metamodel.domain.LinkableElementModel
import amf.core.internal.parser._
import amf.core.internal.parser.domain.Annotations.{inferred, synthesized}
import amf.core.internal.parser.domain.{Annotations, Fields, FutureDeclarations, SearchScope}
import amf.core.internal.plugins.syntax.SyamlAMFErrorHandler
import amf.core.internal.utils._
import amf.shapes.client.scala.model.domain._
import amf.shapes.internal.annotations.CollectionFormatFromItems
import amf.shapes.internal.domain.metamodel.DiscriminatorValueMappingModel.{
  DiscriminatorValue,
  DiscriminatorValueTarget
}
import amf.shapes.internal.domain.metamodel.IriTemplateMappingModel.{LinkExpression, TemplateVariable}
import amf.shapes.internal.domain.metamodel._
import amf.shapes.internal.domain.metamodel.common.ExamplesField
import amf.shapes.internal.domain.parser.XsdTypeDefMapping
import amf.shapes.internal.spec.SemanticContextParser
import amf.shapes.internal.spec.common.TypeDef._
import amf.shapes.internal.spec.common._
import amf.shapes.internal.spec.common.parser._
import amf.shapes.internal.spec.jsonschema.parser.{ContentParser, UnevaluatedParser}
import amf.shapes.internal.spec.oas.parser.field.ShapeParser.{
  AdditionalProperties,
  AllOf,
  AnyOf,
  Const,
  Contains,
  Default,
  Dependencies,
  DependentRequired,
  DependentSchemas,
  Description,
  Else,
  Enum,
  ExclusiveMaximumBoolean,
  ExclusiveMaximumNumeric,
  ExclusiveMinimumBoolean,
  ExclusiveMinimumNumeric,
  ExplicitTypeAnnotation,
  ExternalDocs,
  FacetsExtension,
  Id,
  If,
  MaxItems,
  MaxLength,
  MaxProperties,
  Maximum,
  MinItems,
  MinLength,
  MinProperties,
  Minimum,
  MultipleOf,
  Not,
  OasExtensions,
  OneOf,
  Pattern,
  Properties,
  ReadOnly,
  Then,
  Title,
  Type,
  UnevaluatedProperties,
  UniqueItems,
  WriteOnly,
  Xml,
  `$Comment`,
  `@Context`
}
import amf.shapes.internal.spec.oas.parser.field.{FieldParser, ShapeParser}
import amf.shapes.internal.spec.oas.{OasShapeDefinitions, parser}
import amf.shapes.internal.validation.definitions.ShapeParserSideValidations._
import org.yaml.model._

case class InlineOasTypeParser(
    entryOrNode: YMapEntryLike,
    name: String,
    map: YMap,
    adopt: Shape => Unit,
    version: SchemaVersion,
    isDeclaration: Boolean = false
)(implicit val ctx: ShapeParserContext)
    extends QuickFieldParserOps {

  private val ast: YPart = entryOrNode.ast

  private val nameAnnotations: Annotations = entryOrNode.key.map(n => Annotations(n)).getOrElse(Annotations.virtual())

  def parse(): Option[AnyShape] = {

    val parsedShape = if (detectDisjointUnion()) {
      validateUnionType()
      Some(parseDisjointUnionType())
    } else {
      val parsedShape = detect(version) match {
        case UnionType                   => Some(parseUnionType())
        case ObjectType                  => Some(parseObjectType())
        case ArrayType                   => Some(parseArrayType())
        case AnyType                     => Some(parseAnyType())
        case typeDef if typeDef.isScalar => Some(parseScalarType(typeDef))
        case _                           => None
      }
      parsedShape match {
        case Some(shape: AnyShape) =>
          version match {
            case oas: OASSchemaVersion if oas.position != SchemaPosition.Other =>
              ctx.closedShape(shape, map, oas.position.toString)
            case _ => // Nothing to do
          }
          if (isOas3) Some(checkOas3Nullable(shape))
          else Some(shape)
        case None => None
      }
    }

    parsedShape
  }

  private def validateUnionType(): Unit =
    if (version.isInstanceOf[OAS30SchemaVersion])
      ctx.eh.violation(
        InvalidJsonSchemaType,
        "",
        s"Value of field 'type' must be a string, multiple types are not supported",
        map.key("type").get.location
      )

  protected def isOas: Boolean  = version.isInstanceOf[OASSchemaVersion]
  protected def isOas3: Boolean = version.isInstanceOf[OAS30SchemaVersion]

  protected def moveExamplesToUnion(parsed: AnyShape, union: UnionShape): Unit = {
    val AmfArray(values, annotations) = parsed.fields.get(ExamplesField.Examples)
    if (values.nonEmpty) {
      union.setWithoutId(ExamplesField.Examples, AmfArray(values, annotations), annotations ++= inferred())
      parsed.fields.removeField(ExamplesField.Examples)
    }
  }

  def checkOas3Nullable(parsed: AnyShape): AnyShape = {
    map.key("nullable") match {
      case Some(nullableEntry) if nullableEntry.value.toOption[Boolean].getOrElse(false) =>
        val union = UnionShape().withName(name, nameAnnotations).withId(parsed.id + "/nilUnion")
        parsed.annotations += NilUnion(nullableEntry.key.range.toString())
        union.withAnyOf(
          Seq(
            parsed,
            NilShape().withId(union.id + "_nil")
          ),
          synthesized()
        )
        moveExamplesToUnion(parsed, union)
        union
      case _ =>
        parsed
    }
  }

  /** JSON Schema allows to define multiple types for a shape. In this case we can parse this as a union because
    * properties are going to be disjoint for each of them
    */
  private def detectDisjointUnion(): Boolean = {
    map.key("type").isDefined && map.key("type").get.value.asOption[YSequence].isDefined
  }

  private def detect(version: SchemaVersion): TypeDef = {
    val defaultType = version match {
      case oasSchema: OASSchemaVersion if oasSchema.position.toString == "parameter" => UndefinedType
      case _                                                                         => AnyType
    }
    TypeDetector.detect(map, version)(new SyamlAMFErrorHandler(ctx.eh)).getOrElse(defaultType)
  }

  private def parseDisjointUnionType(): UnionShape = {

    // val detectedTypes = map.key("type").get.value.as[YSequence].nodes.map(_.as[String])
    val allEntriesExceptType = YMap(map.entries.filter(_.key.as[String] != "type"), map.sourceName)

    val parser = UnionShapeParser(YMapEntryLike(allEntriesExceptType), name)
    adopt(parser.shape) // We need to set the shape id before parsing to properly adopt nested nodes
    val union = parser.parse()

    val filterKeys = Seq(
      "example",
      "examples",
      "examples".asOasExtension,
      "title",
      "description",
      "default",
      "enum",
      "externalDocs",
      "xml",
      "facets".asOasExtension,
      "anyOf",
      "allOf",
      "oneOf",
      "not"
    )
    val filteredEntries = allEntriesExceptType.entries.filter { entry =>
      !filterKeys.contains(entry.key.as[String])
    }
    val propsToPropagate = YMap(filteredEntries, filteredEntries.headOption.map(_.sourceName).getOrElse(""))
    val typesSeq         = map.key("type").get.value.as[YSequence]
    var index            = 0
    val parsedTypes: Seq[AmfElement] = typesSeq.nodes map { node =>
      index += 1
      if (node.tagType == YType.Str) {
        node.as[String] match {
          case "object" =>
            Some(parseObjectType(name + index, propsToPropagate, s => s.withId(union.id + "/object")))
          case "array" =>
            Some(parseArrayType(name + index, propsToPropagate, s => s.withId(union.id + "/array")))
          case "number" =>
            Some(
              parseScalarType(TypeDef.NumberType, name + index, propsToPropagate, s => s.withId(union.id + "/number"))
            )
          case "integer" =>
            Some(parseScalarType(TypeDef.IntType, name + index, propsToPropagate, s => s.withId(union.id + "/integer")))
          case "string" =>
            Some(parseScalarType(TypeDef.StrType, name + index, propsToPropagate, s => s.withId(union.id + "/string")))
          case "boolean" =>
            Some(
              parseScalarType(TypeDef.BoolType, name + index, propsToPropagate, s => s.withId(union.id + "/boolean"))
            )
          case "null" =>
            Some(parseScalarType(TypeDef.NilType, name + index, propsToPropagate, s => s.withId(union.id + "/nil")))
          case "any" =>
            Some(parseAnyType(name + index, propsToPropagate, s => s.withId(union.id + "/any")))
          case other =>
            ctx.eh.violation(
              InvalidDisjointUnionType,
              union,
              s"Invalid type for disjointUnion $other",
              map.key("type").get.value.location
            )
            None
        }
      } else if (node.tagType == YType.Map) {
        val entry = YMapEntry(s"union_member_$index", node)
        OasTypeParser(entry, shape => Unit, version).parse()
      } else {
        ctx.eh.violation(
          InvalidDisjointUnionType,
          union,
          s"Invalid type for disjointUnion ${node.tagType}",
          map.key("type").get.value.location
        )
        None
      }
    } collect { case Some(t: AmfElement) => t }

    if (parsedTypes.nonEmpty) union.setArrayWithoutId(UnionShapeModel.AnyOf, parsedTypes, Annotations(typesSeq))

    union
  }

  private def parseScalarType(
      typeDef: TypeDef,
      name: String = name,
      map: YMap = map,
      adopt: Shape => Unit = adopt
  ): AnyShape = {
    val parsed = typeDef match {
      case NilType =>
        val shape = NilShape(ast).withName(name, nameAnnotations)
        adopt(shape)
        shape
      case FileType =>
        val shape = FileShape(ast).withName(name, nameAnnotations)
        adopt(shape)
        FileShapeParser(typeDef, shape, map).parse()
      case _ =>
        val shape = ScalarShape(ast).withName(name, nameAnnotations)
        adopt(shape)
        ScalarShapeParser(typeDef, shape, map).parse()
    }
    parsed
  }

  private def parseAnyType(name: String = name, map: YMap = map, adopt: Shape => Unit = adopt): AnyShape = {
    val shape = AnyShape(ast).withName(name, nameAnnotations)
    adopt(shape)
    AnyTypeShapeParser(shape, map).parse()
  }

  private def parseArrayType(name: String = name, map: YMap = map, adopt: Shape => Unit = adopt): AnyShape = {
    DataArrangementParser(name, ast, map, (shape: Shape) => adopt(shape)).parse()
  }

  private def parseObjectType(name: String = name, map: YMap = map, adopt: Shape => Unit = adopt): AnyShape = {
    if (map.key("schema".asOasExtension).isDefined) {
      val shape = SchemaShape(ast).withName(name, nameAnnotations)
      adopt(shape)
      SchemaShapeParser(shape, map)(ctx.eh).parse()
    } else {
      val shape = NodeShape(ast).withName(name, nameAnnotations)
      checkJsonIdentity(shape, map, adopt, ctx.futureDeclarations)
      NodeShapeParser(shape, map).parse()
    }
  }

  private def checkJsonIdentity(
      shape: AnyShape,
      map: YMap,
      adopt: Shape => Unit,
      futureDeclarations: FutureDeclarations
  ): Unit = {
    adopt(shape)
    if (!isOas && !isOas3) {
      map.map.get("id") foreach { f =>
        f.asOption[String].foreach { id =>
          futureDeclarations.resolveRef(id, shape)
          ctx.registerJsonSchema(id, shape)
        }
      }
    } else if (isOas && isDeclaration && ctx.isMainFileContext && shape.name.option().isDefined) {
      val localRef = buildLocalRef(shape.name.option().get)
      val fullRef  = ctx.loc + localRef
      ctx.registerJsonSchema(fullRef, shape)
    }

    def buildLocalRef(name: String) = ctx match {
      case _ if ctx.isOas3Context  => s"#/components/schemas/$name"
      case _ if ctx.isAsyncContext => s"#/components/schemas/$name"
      case _                       => s"#/definitions/$name"
    }
  }

  private def parseUnionType(): UnionShape = UnionShapeParser(entryOrNode, name).parse()

  def parseSemanticContext(shape: AnyShape): Option[SemanticContext] =
    SemanticContextParser(entryOrNode.asMap, shape)(ctx).parse()

  trait CommonScalarParsingLogic {
    def parseScalar(map: YMap, shape: Shape, typeDef: TypeDef): TypeDef = {
      checkPatternAndFormatCombination(map, shape)

      typeDef match {
        case TypeDef.StrType | TypeDef.FileType =>
          Pattern.parse(map, shape)
          MinLength.parse(map, shape)
          MaxLength.parse(map, shape)
        case n if n.isNumber =>
          Minimum.parse(map, shape)
          Maximum.parse(map, shape)
          MultipleOf.parse(map, shape)
          if (version isBiggerThanOrEqualTo JSONSchemaDraft6SchemaVersion) {
            parseNumericExclusive(map, shape)
          } else {
            parseBooleanExclusive(map, shape)
          }
        case _ => // Nothing to do
      }
      ScalarFormatType(shape, typeDef).parse(map)
    }

    private def parseNumericExclusive(map: YMap, shape: Shape): Unit = {
      ExclusiveMinimumNumeric.parse(map, shape)
      ExclusiveMaximumNumeric.parse(map, shape)
    }

    private def parseBooleanExclusive(map: YMap, shape: Shape): Unit = {
      ExclusiveMinimumBoolean.parse(map, shape)
      ExclusiveMaximumBoolean.parse(map, shape)
    }
  }

  private def checkPatternAndFormatCombination(map: YMap, shape: Shape): Unit = {
    val pattern = map.key("pattern")
    val format  = map.key("format")

    if (pattern.isDefined && format.isDefined) {
      val formatName = format.get.value.as[YScalar].text
      ctx.eh.warning(
        PossiblyIgnoredPatternWarning,
        shape,
        s"Pattern property may be ignored if format '$formatName' already defines a standard pattern",
        pattern.get.location
      )
    }
  }

  case class ScalarShapeParser(typeDef: TypeDef, shape: ScalarShape, map: YMap)
      extends AnyShapeParser()
      with CommonScalarParsingLogic {

    override lazy val dataNodeParser: YNode => DataNode =
      ScalarNodeParser().parse
    override lazy val enumParser: YNode => DataNode = CommonEnumParser(shape.id, enumType = EnumParsing.SCALAR_ENUM)

    override def parse(): ScalarShape = {
      super.parse()
      val validatedTypeDef = parseScalar(map, shape, typeDef)

      map
        .key("type")
        .fold(
          shape
            .setWithoutId(ScalarShapeModel.DataType, AmfScalar(XsdTypeDefMapping.xsd(validatedTypeDef)), synthesized())
        )(entry =>
          shape.setWithoutId(
            ScalarShapeModel.DataType,
            AmfScalar(XsdTypeDefMapping.xsd(validatedTypeDef), Annotations(entry.value)),
            Annotations(entry)
          )
        )

      if (isStringScalar(shape) && version.isBiggerThanOrEqualTo(JSONSchemaDraft7SchemaVersion)) {
        ContentParser(s => Unit, version).parse(shape, map)
      }

      shape
    }

    private def isStringScalar(shape: ScalarShape) = shape.dataType.option().fold(false) { value =>
      value == DataType.String
    }
  }

  case class UnionShapeParser(nodeOrEntry: YMapEntryLike, name: String) extends AnyShapeParser() {

    val node: YNode = nodeOrEntry.value

    private def nameAnnotations: Annotations = nodeOrEntry.key.map(n => Annotations(n)).getOrElse(Annotations())
    override val map: YMap                   = node.as[YMap]

    override val shape: UnionShape = {
      val union = UnionShape(Annotations.valueNode(node)).withName(name, nameAnnotations)
      adopt(union)
      union
    }

    override def parse(): UnionShape = {
      super.parse()

      map.key(
        "x-amf-union",
        { entry =>
          entry.value.to[Seq[YNode]] match {
            case Right(seq) =>
              val unionNodes = seq.zipWithIndex
                .map { case (unionNode, index) =>
                  val name  = s"item$index"
                  val entry = YMapEntryLike(name, unionNode)
                  parser
                    .OasTypeParser(entry, name, item => Unit, version)
                    .parse()
                }
                .filter(_.isDefined)
                .map(_.get)
              shape.setArray(UnionShapeModel.AnyOf, unionNodes, Annotations(entry.value))
            case _ =>
              ctx.eh
                .violation(InvalidUnionType, shape, "Unions are built from multiple shape nodes", entry.value.location)

          }
        }
      )

      shape
    }
  }

  case class DataArrangementParser(name: String, ast: YPart, map: YMap, adopt: Shape => Unit) {

    def lookAhead(): Option[Either[TupleShape, ArrayShape]] = {
      map.key("items") match {
        case Some(entry) =>
          entry.value.to[Seq[YNode]] match {
            // this is a sequence, we need to create a tuple
            case Right(_) => Some(Left(TupleShape(ast).withName(name, nameAnnotations)))
            // not an array regular array parsing
            case _ => Some(Right(ArrayShape(ast).withName(name, nameAnnotations)))

          }
        case None => None
      }
    }

    def parse(): AnyShape = {
      lookAhead() match {
        case None =>
          val array = ArrayShape(ast).withName(name, nameAnnotations)
          val shape = ArrayShapeParser(array, map, adopt).parse()
          validateMissingItemsField(shape)
          shape
        case Some(Left(tuple))  => TupleShapeParser(tuple, map, adopt).parse()
        case Some(Right(array)) => ArrayShapeParser(array, map, adopt).parse()
      }
    }

    private def validateMissingItemsField(shape: Shape): Unit = {
      if (version.isInstanceOf[OAS30SchemaVersion]) {
        ctx.eh.violation(ItemsFieldRequired, shape, "'items' field is required when schema type is array", map.location)
      }
    }
  }

  trait DataArrangementShapeParser extends AnyShapeParser {

    override def parse(): AnyShape = {
      super.parse()

      MinItems.parse(map, shape)
      MaxItems.parse(map, shape)
      UniqueItems.parse(map, shape)
//      since(JSONSchemaDraft7SchemaVersion)(version)(Contains(version, adopt))
      if (version isBiggerThanOrEqualTo JSONSchemaDraft7SchemaVersion)
        InnerShapeParser("contains", ArrayShapeModel.Contains, map, shape, adopt, version).parse()
      shape
    }

  }

  case class TupleShapeParser(shape: TupleShape, map: YMap, adopt: Shape => Unit) extends DataArrangementShapeParser() {

    override def parse(): AnyShape = {
      adopt(shape)
      super.parse()

      map.key("additionalItems").foreach { entry =>
        entry.value.tagType match {
          case YType.Bool =>
            (TupleShapeModel.ClosedItems in shape).negated(entry)
            if (version isBiggerThanOrEqualTo JSONSchemaDraft7SchemaVersion)
              additionalItemViolation(entry, "Invalid part type for additional items node. Expected a map")
          case YType.Map =>
            parser.OasTypeParser(entry, s => Unit, version).parse().foreach { s =>
              shape.setWithoutId(TupleShapeModel.AdditionalItemsSchema, s, Annotations(entry))
            }
          case _ =>
            additionalItemViolation(
              entry,
              if (version isBiggerThanOrEqualTo JSONSchemaDraft7SchemaVersion)
                "Invalid part type for additional items node. Expected a map"
              else
                "Invalid part type for additional items node. Should be a boolean or a map"
            )
        }
      }
      map.key(
        "items",
        entry => {
          val items = entry.value
            .as[YSequence]
            .nodes
            .collect { case node if node.tagType == YType.Map => node }
            .zipWithIndex
            .map { case (elem, index) =>
              parser
                .OasTypeParser(YMapEntryLike(elem), s"member$index", item => Unit, version)
                .parse()
            }
          shape.withItems(items.filter(_.isDefined).map(_.get))
        }
      )

      shape
    }

    private def additionalItemViolation(entry: YMapEntry, msg: String): Unit = {
      ctx.eh.violation(InvalidAdditionalItemsType, shape, msg, entry.location)
    }
  }

  case class ArrayShapeParser(shape: ArrayShape, map: YMap, adopt: Shape => Unit) extends DataArrangementShapeParser() {
    override def parse(): AnyShape = {
      checkJsonIdentity(shape, map, adopt, ctx.futureDeclarations)
      super.parse()

      map.key("collectionFormat", ArrayShapeModel.CollectionFormat in shape)

      map
        .key("items")
        .flatMap(_.value.toOption[YMap])
        .foreach(
          _.key(
            "collectionFormat",
            (ArrayShapeModel.CollectionFormat in shape)
              .withAnnotation(CollectionFormatFromItems())
          )
        )

      val finalShape = for {
        entry <- map.key("items")
        item  <- parser.OasTypeParser(entry, items => Unit, version).parse()
      } yield {
        item match {
          case array: ArrayShape =>
            shape.setWithoutId(ArrayShapeModel.Items, array, Annotations(entry)).toMatrixShape
          case matrix: MatrixShape =>
            shape.setWithoutId(ArrayShapeModel.Items, matrix, Annotations(entry)).toMatrixShape
          case other: AnyShape =>
            shape.setWithoutId(ArrayShapeModel.Items, other, Annotations(entry))
        }
      }

      if (version.isBiggerThanOrEqualTo(JSONSchemaDraft201909SchemaVersion)) {
        new UnevaluatedParser(version, UnevaluatedParser.unevaluatedItemsInfo).parse(map, shape)
        map.key("minContains", ArrayShapeModel.MinContains in shape)
        map.key("maxContains", ArrayShapeModel.MaxContains in shape)
      }

      finalShape match {
        case Some(parsed: AnyShape) => parsed.withId(shape.id)
        case None                   => shape.withItems(AnyShape())
      }
    }
  }

  case class AnyTypeShapeParser(shape: AnyShape, map: YMap) extends AnyShapeParser {
    override val options: ExampleOptions = ExampleOptions(strictDefault = true, quiet = true)
  }

  abstract class AnyShapeParser() extends ShapeParser() {

    override val shape: AnyShape
    val options: ExampleOptions = ExampleOptions(strictDefault = true, quiet = false)

    override def parse(): AnyShape = {

      val parsers =
        List(`@Context`) ++
          this.parsers ++
          List(
            parseExample(version, options),
            ExplicitTypeAnnotation,
            since(JSONSchemaDraft7SchemaVersion)(version)(`$Comment`)
          )

      parsers.foldLeft[Shape](shape)((shape, parser) => parser.parse(map, shape))
      shape
    }

    private def parseExample(version: SchemaVersion, options: ExampleOptions): FieldParser = new FieldParser {
      override def parse(map: YMap, shape: Shape)(implicit ctx: ShapeParserContext): Shape = {
        if (version isBiggerThanOrEqualTo JSONSchemaDraft6SchemaVersion) {
          parseExamplesArray(options)(map, shape)
          shape
        } else {
          shape match {
            case any: AnyShape =>
              RamlExamplesParser(map, "example", "examples".asOasExtension, any, options).parse()
              shape
            case _ => shape
          }
        }
      }
    }

    private def parseExamplesArray(options: ExampleOptions)(map: YMap, shape: Shape): Unit =
      map
        .key("examples")
        .map { entry =>
          val sequence = entry.value.as[YSequence]
          val examples = ExamplesDataParser(sequence, options, shape.id).parse()
          shape.fields.setWithoutId(
            AnyShapeModel.Examples,
            AmfArray(examples, Annotations(entry.value)),
            Annotations(entry)
          )
        }
  }

  def since(version: JSONSchemaVersion)(schemaVersion: SchemaVersion)(parser: FieldParser): FieldParser =
    new FieldParser {
      override def parse(map: YMap, shape: Shape)(implicit ctx: ShapeParserContext): Shape = {
        if (schemaVersion isBiggerThanOrEqualTo version) parser.parse(map, shape)
        else shape
      }
    }

  case class NodeShapeParser(shape: NodeShape, map: YMap)(implicit val ctx: ShapeParserContext)
      extends AnyShapeParser() {
    override def parse(): NodeShape = {

      super.parse()

      map.key("type", _ => shape.add(ExplicitField()))

      MinProperties.parse(map, shape)
      MaxProperties.parse(map, shape)

      AdditionalProperties(version).parse(map, shape)

      if (version.isBiggerThanOrEqualTo(JSONSchemaDraft201909SchemaVersion)) {
        UnevaluatedProperties(version).parse(map, shape)
      }

      parseDiscriminator()

      Properties(version, adopt).parse(map, shape)

      parseShapeDependencies(shape)

      parseAmfMergeAnnotation(shape)

      shape
    }

    private def parseDiscriminator(): Unit = {
      if (isOas3) {
        map.key("discriminator", DiscriminatorParser(shape, _).parse())
      } else {
        map.key("discriminator", NodeShapeModel.Discriminator in shape)
        map.key(
          "discriminatorValue".asOasExtension,
          entry => {
            val setDiscriminatorValueField = NodeShapeModel.DiscriminatorValue in shape
            setDiscriminatorValueField(entry)

            val valueDataNode = DataNodeParser(entry.value).parse()
            shape.set(NodeShapeModel.DiscriminatorValueDataNode, valueDataNode, Annotations(entry.key))
          }
        )
      }
    }
  }

  private def parseAmfMergeAnnotation(shape: Shape): Unit = {
    map.key(
      "x-amf-merge",
      entry => {
        val inherits = AllOfParser(entry.value.as[Seq[YNode]], s => Unit, version).parse()
        shape.setWithoutId(NodeShapeModel.Inherits, AmfArray(inherits, Annotations(entry.value)), Annotations(entry))
      }
    )
  }

  private def parseShapeDependencies(shape: NodeShape): Unit = {
    if (version == JSONSchemaDraft201909SchemaVersion) {
      val parentId = shape.id
      DependentSchemas(version, parentId).parse(map, shape)
      DependentRequired(parentId).parse(map, shape)
    } else {
      Dependencies(version).parse(map, shape)
    }
  }

  case class DiscriminatorParser(shape: NodeShape, entry: YMapEntry) {
    def parse(): Unit = {
      val map = entry.value.as[YMap]
      map.key("propertyName") match {
        case Some(entry) =>
          (NodeShapeModel.Discriminator in shape)(entry)
        case None =>
          ctx.eh.violation(
            DiscriminatorNameRequired,
            shape,
            s"Discriminator must have a propertyName defined",
            map.location
          )
      }
      map.key("mapping", parseMappings)
      ctx.closedShape(shape, map, "discriminator")
    }

    private def parseMappings(mappingsEntry: YMapEntry): Unit = {
      val map = mappingsEntry.value.as[YMap]
      val mappings = map.entries.map(entry => {
        val mapping  = IriTemplateMapping(Annotations(entry))
        val element  = amf.core.internal.parser.domain.ScalarNode(entry.key).string()
        val variable = amf.core.internal.parser.domain.ScalarNode(entry.value).string()
        mapping.setWithoutId(TemplateVariable, element, Annotations(entry.key))
        mapping.setWithoutId(LinkExpression, variable, Annotations(entry.value))
      })
      shape.fields.setWithoutId(
        NodeShapeModel.DiscriminatorMapping,
        AmfArray(mappings, Annotations(mappingsEntry.value)),
        Annotations(mappingsEntry)
      )

      val discriminatorValueMapping = map.entries.map { entry =>
        val key: YNode         = entry.key
        val discriminatorValue = amf.core.internal.parser.domain.ScalarNode(key).string()
        val targetShape = {
          val rawRef: String = entry.value
          val definitionName = OasShapeDefinitions.stripDefinitionsPrefix(rawRef)
          ctx.findType(definitionName, SearchScope.All) match {
            case Some(s) =>
              s.link(AmfScalar(key.toString), Annotations(ast), synthesized())
                .asInstanceOf[AnyShape]
                .withName(name, nameAnnotations)
                .withSupportsRecursion(true)
            case _ =>
              val resultShape = AnyShape(ast).withName(key, Annotations(key))
              val tmpShape = UnresolvedShape(
                Fields(),
                Annotations(entry.value),
                entry.value,
                None,
                Some((k: String) => resultShape.set(LinkableElementModel.TargetId, k)),
                shouldLink = false
              )
                .withName(key, Annotations())
                .withSupportsRecursion(true)
              tmpShape.unresolved(definitionName, Nil, Some(entry.value.location), "warning")(ctx)
              tmpShape.withContext(ctx)
              val encodedKey = key.toString.urlComponentEncoded
              tmpShape.withId(s"${shape.id}/discriminator/$encodedKey/unresolved")
              resultShape.withId(s"${shape.id}/discriminator/$encodedKey")
              resultShape.withLinkTarget(tmpShape).withLinkLabel(key)
          }
        }

        val discriminatorMapping = DiscriminatorValueMapping(Annotations(entry))
        discriminatorMapping.setWithoutId(DiscriminatorValue, discriminatorValue, Annotations(key))
        discriminatorMapping.setWithoutId(DiscriminatorValueTarget, targetShape, Annotations(entry.value))
      }

      val fieldValue = AmfArray(discriminatorValueMapping, Annotations(mappingsEntry.value))
      shape.setWithoutId(NodeShapeModel.DiscriminatorValueMapping, fieldValue, Annotations(mappingsEntry))
    }
  }

  abstract class ShapeParser(implicit ctx: ShapeParserContext) {

    val shape: Shape
    val map: YMap

    lazy val dataNodeParser: YNode => DataNode = DataNodeParser.parse(new IdCounter())
    lazy val enumParser: YNode => DataNode     = CommonEnumParser(shape.id)

    protected lazy val parsers: List[FieldParser] = List(
      Title,
      Description,
      Default,
      Enum(enumParser),
      ExternalDocs,
      Xml,
      FacetsExtension(version),
      Type,
      AnyOf(version),
      AllOf(version, adopt),
      OneOf(version, adopt),
      Not(version, adopt),
      ReadOnly,
      oas3AndDraft7AndBiggerFields(version),
      draft7OrBiggerFields(version, adopt),
      OasExtensions,
      Id
    )

    def parse(): Shape = {
      parsers.foldLeft(shape)((shape, parser) => parser.parse(map, shape))
    }

    private def draft7OrBiggerFields(version: SchemaVersion, adopt: Shape => Unit): FieldParser = {
      when(() => version isBiggerThanOrEqualTo JSONSchemaDraft7SchemaVersion) { (map, shape) =>
        If(version, adopt).parse(map, shape)
        Then(version, adopt).parse(map, shape)
        Else(version, adopt).parse(map, shape)
        Const(dataNodeParser).parse(map, shape)
        shape
      }
    }

    private def oas3AndDraft7AndBiggerFields(version: SchemaVersion): FieldParser = {
      when(() =>
        version.isInstanceOf[OAS30SchemaVersion] || version.isBiggerThanOrEqualTo(JSONSchemaDraft7SchemaVersion)
      ) { (map, shape) =>
        WriteOnly.parse(map, shape)
        ShapeParser.Deprecated.parse(map, shape)
        shape
      }
    }

    def when(predicate: () => Boolean)(block: (YMap, Shape) => Shape): FieldParser = new FieldParser {
      override def parse(map: YMap, shape: Shape)(implicit ctx: ShapeParserContext): Shape = {
        if (predicate()) {
          block(map, shape)
        } else shape
      }
    }
  }

  case class FileShapeParser(typeDef: TypeDef, shape: FileShape, map: YMap)
      extends AnyShapeParser()
      with CommonScalarParsingLogic {
    override def parse(): FileShape = {
      super.parse()

      parseScalar(map, shape, typeDef)

      map.key("fileTypes".asOasExtension, FileShapeModel.FileTypes in shape)

      shape
    }
  }

  case class SchemaShapeParser(shape: SchemaShape, map: YMap)(implicit errorHandler: AMFErrorHandler)
      extends AnyShapeParser()
      with CommonScalarParsingLogic {
    super.parse()

    override def parse(): AnyShape = {
      map.key(
        "schema".asOasExtension,
        { entry =>
          entry.value.to[String] match {
            case Right(str) => shape.withRaw(str)
            case _ =>
              errorHandler.violation(
                InvalidSchemaType,
                shape,
                "Cannot parse non string schema shape",
                entry.value.location
              )
              shape.withRaw("")
          }
        }
      )

      map.key(
        "mediaType".asOasExtension,
        { entry =>
          entry.value.to[String] match {
            case Right(str) => shape.withMediaType(str)
            case _ =>
              errorHandler.violation(
                InvalidMediaTypeType,
                shape,
                "Cannot parse non string schema shape",
                entry.value.location
              )
              shape.withMediaType("*/*")
          }
        }
      )

      shape
    }
  }
}
