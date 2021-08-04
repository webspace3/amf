package amf.apicontract.internal.spec.raml

import amf.apicontract.internal.plugins.ApiElementRenderPlugin
import amf.apicontract.internal.spec.common.emitter.DomainElementEmitterFactory
import amf.apicontract.internal.spec.raml.emitter.domain.Raml10EmitterFactory
import amf.core.client.scala.errorhandling.AMFErrorHandler
import amf.core.internal.remote.Spec

object Raml10ElementRenderPlugin extends ApiElementRenderPlugin {

  override protected def spec: Spec = Spec.RAML10

  override protected def emitterFactory: AMFErrorHandler => DomainElementEmitterFactory =
    eh => Raml10EmitterFactory(eh)
}
