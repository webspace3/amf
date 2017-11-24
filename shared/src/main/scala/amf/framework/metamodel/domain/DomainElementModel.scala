package amf.framework.metamodel.domain

import amf.framework.metamodel.Type.Array
import amf.framework.metamodel.document.SourceMapModel
import amf.framework.metamodel.{Field, ModelDefaultBuilder, Obj}
import amf.plugins.domain.webapi.metamodel.extensions.DomainExtensionModel
import amf.framework.vocabulary.Namespace.{Document, SourceMaps}
import amf.framework.vocabulary.ValueType

/**
  * Domain element meta-model
  */
trait DomainElementModel extends Obj with ModelDefaultBuilder {

  lazy val Extends = Field(Array(DomainElementModel), Document + "extends")

  val Includes = Field(Array(DomainElementModel), Document + "includes")

  val Sources = Field(SourceMapModel, SourceMaps + "sources")

  // This creates a cycle in the among DomainModels, triggering a classnotdef problem
  // I need lazy evaluation here.
  // It cannot even be defined in the list of fields below
  lazy val CustomDomainProperties = Field(Array(DomainExtensionModel), Document + "customDomainProperties")

}

object DomainElementModel extends DomainElementModel {

  // 'Static' values, we know the element schema before parsing
  // If the domain element is dynamic, the value from the model,
  // not the meta-model, should be retrieved instead

  override val `type`: List[ValueType] = List(Document + "DomainElement")

  override def fields: List[Field] = List(Extends, Includes)

  override def modelInstance =  throw new Exception("DomainElement is an abstract class")
}
