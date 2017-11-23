package amf.model

import amf.domain.extensions
import amf.shape

import scala.language.postfixOps
import scala.scalajs.js
import scala.scalajs.js.JSConverters._
import amf.domain.`abstract`
import amf.domain.security

trait DeclaresModel {

  private[amf] def element: amf.framework.document.DeclaresModel

  /** Declared [[DomainElement]]s that can be re-used from other documents. */
  lazy val declares: js.Iterable[DomainElement] = {
    val declarations = element.declares
      .map {
        case node: shape.NodeShape                     => NodeShape(node)
        case scalar: shape.ScalarShape                 => ScalarShape(scalar)
        case tuple: shape.TupleShape                   => TupleShape(tuple)
        case matrix: shape.MatrixShape                 => MatrixShape(matrix)
        case property: extensions.CustomDomainProperty => CustomDomainProperty(property)
        case tr: `abstract`.Trait                      => Trait(tr)
        case resourceType: `abstract`.ResourceType     => ResourceType(resourceType)
        case security: security.SecurityScheme         => SecurityScheme(security)
        case _                                         => throw new RuntimeException("unsupported domain element type in module declaration")
      }
    declarations.toJSArray
  }

}
