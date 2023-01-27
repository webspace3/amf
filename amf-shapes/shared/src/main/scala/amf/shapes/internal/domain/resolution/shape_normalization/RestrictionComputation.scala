package amf.shapes.internal.domain.resolution.shape_normalization

import amf.core.client.scala.model.Annotable
import amf.core.internal.annotations.{InheritanceProvenance, LexicalInformation, SourceAST, SourceNode}
import amf.core.internal.metamodel.{Field, Obj}
import amf.core.internal.metamodel.domain.ShapeModel
import amf.core.internal.metamodel.domain.extensions.PropertyShapeModel
import amf.core.client.scala.model.domain._
import amf.core.internal.parser.domain.{Annotations, Value}
import amf.shapes.client.scala.model.domain.AnyShape
import amf.shapes.internal.domain.metamodel._
import amf.shapes.internal.domain.resolution.shape_normalization.AmfElementComparer._

private[shape_normalization] trait RestrictionComputation {

  val keepEditingInfo: Boolean

  protected def computeNarrowLogical(baseShape: Shape, superShape: Shape) = {
    var and: Seq[Shape]    = Seq()
    var or: Seq[Shape]     = Seq()
    var xor: Seq[Shape]    = Seq()
    var not: Option[Shape] = None

    val baseOr  = Option(baseShape.or).getOrElse(Nil)
    val superOr = Option(baseShape.or).getOrElse(Nil)
    if (baseOr.nonEmpty && superOr.nonEmpty)
      and ++= Seq(
        AnyShape()
          .withId(baseShape.id + s"/andOr")
          .withAnd(
            Seq(
              AnyShape().withId(baseShape.id + "/andOrBase").withOr(baseOr),
              AnyShape().withId(baseShape.id + "/andOrSuper").withOr(superOr)
            )
          )
      ) // both constraints must match => AND
    if (baseOr.nonEmpty || superOr.nonEmpty) or ++= (baseOr ++ superOr)

    // here we just aggregate ands
    val baseAnd  = Option(baseShape.and).getOrElse(Nil)
    val superAnd = Option(baseShape.and).getOrElse(Nil)
    and ++= (baseAnd ++ superAnd)

    val baseXone  = Option(baseShape.xone).getOrElse(Nil)
    val superXone = Option(baseShape.xone).getOrElse(Nil)
    if (baseXone.nonEmpty && superXone.nonEmpty)
      and ++= Seq(
        AnyShape()
          .withId(baseShape.id + s"/andXone")
          .withAnd(
            Seq(
              AnyShape().withId(baseShape.id + "/andXoneBase").withXone(baseXone),
              AnyShape().withId(baseShape.id + "/andXoneSuper").withXone(superXone)
            )
          )
      ) // both constraints must match => AND
    if (baseXone.nonEmpty || superXone.nonEmpty) or ++= (baseOr ++ superOr)

    val baseNot  = Option(baseShape.not)
    val superNot = Option(baseShape.not)
    if (baseNot.isDefined && superNot.isDefined)
      and ++= Seq(
        AnyShape()
          .withId(baseShape.id + s"/andNot")
          .withAnd(
            Seq(
              AnyShape().withId(baseShape.id + "/andNotBase").withNot(baseNot.get),
              AnyShape().withId(baseShape.id + "/andNotSuper").withNot(superNot.get)
            )
          )
      ) // both constraints must match => AND
    if (baseNot.isDefined || superNot.isDefined) not = baseNot.orElse(superNot)

    baseShape.fields.removeField(ShapeModel.And)
    baseShape.fields.removeField(ShapeModel.Or)
    baseShape.fields.removeField(ShapeModel.Xone)
    baseShape.fields.removeField(ShapeModel.Not)

    if (and.nonEmpty) baseShape.setArrayWithoutId(ShapeModel.And, and)
    if (or.nonEmpty) baseShape.setArrayWithoutId(ShapeModel.Or, or)
    if (xor.nonEmpty) baseShape.setArrayWithoutId(ShapeModel.Xone, xor)
    if (not.isDefined) baseShape.set(ShapeModel.Not, not.get)
  }

  protected def computeNarrowRestrictions(meta: Obj, base: Shape, superShape: Shape, ignore: Seq[Field]): Shape = {
    computeNarrowRestrictions(meta.fields, base, superShape, ignore)
  }

  protected def computeNarrowRestrictions(
      fields: Seq[Field],
      baseShape: Shape,
      superShape: Shape,
      filteredFields: Seq[Field] = Seq.empty
  ): Shape = {
    fields.foreach { f =>
      if (!filteredFields.contains(f)) {
        val baseValue  = baseShape.fields.getValueAsOption(f)
        val superValue = superShape.fields.getValueAsOption(f)
        (baseValue, superValue) match {
          case (Some(base), None) => baseShape.set(f, base.value, base.annotations)

          case (None, Some(superVal)) =>
            val finalAnnotations = Annotations(superVal.annotations)
            if (keepEditingInfo) inheritAnnotations(finalAnnotations, superShape)
            baseShape.fields.setWithoutId(f, superVal.value, finalAnnotations)

          case (Some(bvalue), Some(superVal)) =>
            val finalAnnotations = Annotations(bvalue.annotations)
            val finalValue       = computeNarrow(f, bvalue.value, superVal.value)
            if (finalValue != bvalue.value && keepEditingInfo) inheritAnnotations(finalAnnotations, superShape)
            val effective = finalValue.add(finalAnnotations)
            baseShape.fields.setWithoutId(f, effective, finalAnnotations)
          case _ => // ignore
        }
      }
    }

    baseShape
  }

  private def inheritAnnotations(annotations: Annotations, from: Shape) = {
    if (!annotations.contains(classOf[InheritanceProvenance]))
      annotations += InheritanceProvenance(from.id)
    annotations
  }

  protected def restrictShape(restriction: Shape, shape: Shape): Shape = {
    shape.id = restriction.id
    restriction.fields.foreach { case (field, derivedValue) =>
      if (field != NodeShapeModel.Inherits) {
        Option(shape.fields.getValue(field)) match {
          case Some(superValue) => shape.set(field, computeNarrow(field, derivedValue.value, superValue.value))
          case None             => shape.fields.setWithoutId(field, derivedValue.value, derivedValue.annotations)
        }
      }
    }
    shape
  }

  protected def computeNumericRestriction(
      comparison: String,
      lvalue: AmfElement,
      rvalue: AmfElement
  ): AmfElement = {
    (lvalue, rvalue) match {
      case BothNumeric(lnum, rnum) =>
        comparison match {
          case "max" =>
            if (lnum.intValue() <= rnum.intValue()) {
              rvalue
            } else {
              lvalue
            }
          case "min" =>
            if (lnum.intValue() >= rnum.intValue()) {
              rvalue
            } else {
              lvalue
            }
          case _ => throw new InheritanceIncompatibleShapeError(s"Unknown numeric comparison $comparison")
        }
      case _ =>
        throw new InheritanceIncompatibleShapeError("Cannot compare non numeric or missing values")
    }
  }

  protected def computeEnum(
      derivedEnumeration: Seq[AmfElement],
      superEnumeration: Seq[AmfElement],
      annotations: Annotations
  ): Unit = {
    if (derivedEnumeration.nonEmpty && superEnumeration.nonEmpty) {
      val headOption = derivedEnumeration.headOption
      if (headOption.exists(h => superEnumeration.headOption.exists(_.getClass != h.getClass)))
        throw new InheritanceIncompatibleShapeError(
          s"Values in subtype enumeration are from different class '${derivedEnumeration.head.getClass}' of the super type enumeration '${superEnumeration.head.getClass}'",
          Some(ShapeModel.Values.value.iri()),
          headOption.flatMap(_.location()),
          headOption.flatMap(_.position())
        )

      derivedEnumeration match {
        case Seq(_: ScalarNode) =>
          val superScalars = superEnumeration.collect({ case s: ScalarNode => s.value.value() })
          val ds           = derivedEnumeration.asInstanceOf[Seq[ScalarNode]]
          ds.foreach { e =>
            if (!superScalars.contains(e.value.value())) {
              throw new InheritanceIncompatibleShapeError(
                s"Values in subtype enumeration (${ds.map(_.value).mkString(",")}) not found in the supertype enumeration (${superScalars
                    .mkString(",")})",
                Some(ShapeModel.Values.value.iri()),
                e.location(),
                e.position()
              )
            }
          }
        case _ => // ignore
      }
    }
  }

  protected def maybeAsString(value: AmfElement): Option[String] = {
    value match {
      case scalar: AmfScalar => Option(scalar.value).map(_.toString)
      case _                 => None
    }
  }

  protected def computeNarrow(field: Field, derivedValue: AmfElement, superValue: AmfElement): AmfElement = {
    field match {

      case ShapeModel.Name =>
        val derivedStrValue = maybeAsString(derivedValue)
        val superStrValue   = maybeAsString(superValue)
        (superStrValue, derivedStrValue) match {
          case (Some(_), None | Some("schema")) => superValue
          case _                                => derivedValue
        }

      case NodeShapeModel.MinProperties =>
        if (
          lessOrEqualThan(
            superValue,
            derivedValue,
            incompatibleException(NodeShapeModel.MinProperties, derivedValue)
          )
        ) {
          computeNumericRestriction(
            "max",
            superValue,
            derivedValue
          )
        } else {
          throw new InheritanceIncompatibleShapeError(
            "Resolution error: sub type has a weaker constraint for min-properties than base type for minProperties",
            Some(NodeShapeModel.MinProperties.value.iri()),
            derivedValue.location(),
            derivedValue.position()
          )
        }

      case NodeShapeModel.MaxProperties =>
        if (
          moreOrEqualThan(
            superValue,
            derivedValue,
            incompatibleException(NodeShapeModel.MaxProperties, derivedValue)
          )
        ) {
          computeNumericRestriction(
            "min",
            superValue,
            derivedValue
          )
        } else {
          throw new InheritanceIncompatibleShapeError(
            "Resolution error: sub type has a weaker constraint for max-properties than base type for maxProperties",
            Some(NodeShapeModel.MaxProperties.value.iri()),
            derivedValue.location(),
            derivedValue.position()
          )
        }

      case ScalarShapeModel.MinLength =>
        if (
          lessOrEqualThan(
            superValue,
            derivedValue,
            incompatibleException(ScalarShapeModel.MinLength, derivedValue)
          )
        ) {
          computeNumericRestriction(
            "max",
            superValue,
            derivedValue
          )
        } else {
          throw new InheritanceIncompatibleShapeError(
            "Resolution error: sub type has a weaker constraint for min-length than base type for maxProperties",
            Some(ScalarShapeModel.MinLength.value.iri()),
            derivedValue.location(),
            derivedValue.position()
          )
        }

      case ScalarShapeModel.MaxLength =>
        if (
          moreOrEqualThan(
            superValue,
            derivedValue,
            incompatibleException(ScalarShapeModel.MaxLength, derivedValue)
          )
        ) {
          computeNumericRestriction(
            "min",
            superValue,
            derivedValue
          )
        } else {
          throw new InheritanceIncompatibleShapeError(
            "Resolution error: sub type has a weaker constraint for max-length than base type for maxProperties",
            Some(ScalarShapeModel.MaxLength.value.iri()),
            derivedValue.location(),
            derivedValue.position()
          )
        }

      case ScalarShapeModel.Minimum =>
        if (
          lessOrEqualThan(
            superValue,
            derivedValue,
            incompatibleException(ScalarShapeModel.Minimum, derivedValue)
          )
        ) {
          computeNumericRestriction(
            "max",
            superValue,
            derivedValue
          )
        } else {
          throw new InheritanceIncompatibleShapeError(
            "Resolution error: sub type has a weaker constraint for min-minimum than base type for minimum",
            Some(ScalarShapeModel.Minimum.value.iri()),
            derivedValue.location(),
            derivedValue.position()
          )
        }

      case ScalarShapeModel.Maximum =>
        if (
          moreOrEqualThan(
            superValue,
            derivedValue,
            incompatibleException(ScalarShapeModel.Maximum, derivedValue)
          )
        ) {
          computeNumericRestriction(
            "min",
            superValue,
            derivedValue
          )
        } else {
          throw new InheritanceIncompatibleShapeError(
            "Resolution error: sub type has a weaker constraint for maximum than base type for maximum",
            Some(ScalarShapeModel.Maximum.value.iri()),
            derivedValue.location(),
            derivedValue.position()
          )
        }

      case ArrayShapeModel.MinItems =>
        if (
          lessOrEqualThan(
            superValue,
            derivedValue,
            incompatibleException(ArrayShapeModel.MinItems, derivedValue)
          )
        ) {
          computeNumericRestriction(
            "max",
            superValue,
            derivedValue
          )
        } else {
          throw new InheritanceIncompatibleShapeError(
            "Resolution error: sub type has a weaker constraint for minItems than base type for minItems",
            Some(ArrayShapeModel.MinItems.value.iri()),
            derivedValue.location(),
            derivedValue.position(),
            isViolation = true
          )
        }

      case ArrayShapeModel.MaxItems =>
        if (
          moreOrEqualThan(
            superValue,
            derivedValue,
            incompatibleException(ArrayShapeModel.MaxItems, derivedValue)
          )
        ) {
          computeNumericRestriction(
            "min",
            superValue,
            derivedValue
          )
        } else {
          throw new InheritanceIncompatibleShapeError(
            "Resolution error: sub type has a weaker constraint for maxItems than base type for maxItems",
            Some(ArrayShapeModel.MaxItems.value.iri()),
            derivedValue.location(),
            derivedValue.position()
          )
        }

      case ScalarShapeModel.Format =>
        if (
          areEqualStrings(
            superValue,
            derivedValue,
            incompatibleException(ScalarShapeModel.Format, derivedValue)
          )
        ) {
          derivedValue
        } else {
          throw new InheritanceIncompatibleShapeError(
            "different values for format constraint",
            Some(ScalarShapeModel.Format.value.iri()),
            derivedValue.location(),
            derivedValue.position()
          )
        }

      case ScalarShapeModel.Pattern =>
        if (
          areEqualStrings(
            superValue,
            derivedValue,
            incompatibleException(ScalarShapeModel.Pattern, derivedValue)
          )
        ) {
          derivedValue
        } else {
          throw new InheritanceIncompatibleShapeError(
            "different values for pattern constraint",
            Some(ScalarShapeModel.Pattern.value.iri()),
            derivedValue.location(),
            derivedValue.position()
          )
        }

      case NodeShapeModel.Discriminator =>
        if (
          !areEqualStrings(
            superValue,
            derivedValue,
            incompatibleException(NodeShapeModel.Discriminator, derivedValue)
          )
        ) {
          derivedValue
        } else {
          throw new InheritanceIncompatibleShapeError(
            "shape has same discriminator value as parent",
            Some(NodeShapeModel.Discriminator.value.iri()),
            derivedValue.location(),
            derivedValue.position()
          )
        }

      case NodeShapeModel.DiscriminatorValue =>
        if (
          !areEqualStrings(
            superValue,
            derivedValue,
            incompatibleException(NodeShapeModel.DiscriminatorValue, derivedValue)
          )
        ) {
          derivedValue
        } else {
          throw new InheritanceIncompatibleShapeError(
            "shape has same discriminator value as parent",
            Some(NodeShapeModel.DiscriminatorValue.value.iri()),
            derivedValue.location(),
            derivedValue.position()
          )
        }

      case ShapeModel.Values =>
        computeEnum(
          derivedValue.asInstanceOf[AmfArray].values,
          superValue.asInstanceOf[AmfArray].values,
          derivedValue.annotations
        )
        derivedValue

      case ArrayShapeModel.UniqueItems =>
        if (
          areEqualBooleans(
            superValue,
            derivedValue,
            incompatibleException(ArrayShapeModel.UniqueItems, derivedValue)
          ) ||
          areEqualBooleans(
            superValue,
            derivedValue,
            incompatibleException(ArrayShapeModel.UniqueItems, derivedValue)
          ) ||
          areExpectedBooleans(
            superValue,
            derivedValue,
            expectedLeft = false,
            expectedRight = true,
            incompatibleException(ArrayShapeModel.UniqueItems, derivedValue)
          )
        ) {
          derivedValue
        } else {
          throw new InheritanceIncompatibleShapeError(
            "different values for unique items constraint",
            Some(ArrayShapeModel.UniqueItems.value.iri()),
            derivedValue.location(),
            derivedValue.position()
          )
        }

      case PropertyShapeModel.MinCount =>
        if (
          lessOrEqualThan(
            superValue,
            derivedValue,
            incompatibleException(PropertyShapeModel.MinCount, derivedValue)
          )
        ) {
          computeNumericRestriction(
            "max",
            superValue,
            derivedValue
          )
        } else {
          throw new InheritanceIncompatibleShapeError(
            "Resolution error: sub type has a weaker constraint for minCount than base type for minCount",
            Some(PropertyShapeModel.MinCount.value.iri()),
            derivedValue.location(),
            derivedValue.position()
          )
        }

      case PropertyShapeModel.MaxCount =>
        if (
          moreOrEqualThan(
            superValue,
            derivedValue,
            incompatibleException(PropertyShapeModel.MaxCount, derivedValue)
          )
        ) {
          computeNumericRestriction(
            "min",
            superValue,
            derivedValue
          )
        } else {
          throw new InheritanceIncompatibleShapeError(
            "Resolution error: sub type has a weaker constraint for maxCount than base type for maxCount",
            Some(PropertyShapeModel.MaxCount.value.iri()),
            derivedValue.location(),
            derivedValue.position()
          )
        }

      case PropertyShapeModel.Path =>
        if (
          areEqualStrings(
            superValue,
            derivedValue,
            incompatibleException(PropertyShapeModel.Path, derivedValue)
          )
        ) {
          derivedValue
        } else {
          throw new InheritanceIncompatibleShapeError(
            "different values for discriminator value path",
            Some(PropertyShapeModel.Path.value.iri()),
            derivedValue.location(),
            derivedValue.position()
          )
        }

      case PropertyShapeModel.Range =>
        if (
          areEqualStrings(
            superValue,
            derivedValue,
            incompatibleException(PropertyShapeModel.Range, derivedValue)
          )
        ) {
          derivedValue
        } else {
          throw new InheritanceIncompatibleShapeError(
            "different values for discriminator value range",
            Some(PropertyShapeModel.Range.value.iri()),
            derivedValue.location(),
            derivedValue.position()
          )
        }

      case _ => derivedValue
    }
  }
}
