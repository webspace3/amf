package amf.model.domain

import amf.plugins.domain.shapes.models

import scala.scalajs.js
import scala.scalajs.js.JSConverters._
import scala.scalajs.js.annotation.{JSExportAll, JSExportTopLevel}

/**
  *
  */
@JSExportAll
case class PropertyDependencies(private[amf] val property: models.PropertyDependencies) extends DomainElement {

  @JSExportTopLevel("model.domain.PropertyDependencies")
  def this() = this(models.PropertyDependencies())

  def propertySource: String              = property.propertySource
  def propertyTarget: js.Iterable[String] = Option(property.propertyTarget).getOrElse(Nil).toJSArray

  def withPropertySource(propertySource: String): this.type = {
    property.withPropertySource(propertySource)
    this
  }

  def withPropertyTarget(propertyTarget: js.Iterable[String]): this.type = {
    property.withPropertyTarget(propertyTarget.toSeq)
    this
  }

  override private[amf] def element: models.PropertyDependencies = property
}
