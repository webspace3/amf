package amf.apicontract.internal.spec.oas.parser.domain

import amf.apicontract.client.scala.model.domain.security.OAuth2Flow
import amf.apicontract.internal.metamodel.domain.security.OAuth2FlowModel
import amf.apicontract.internal.validation.definitions.ParserSideValidations.MissingOAuthFlowField
import amf.core.client.scala.errorhandling.AMFErrorHandler
import amf.core.internal.metamodel.Field
import org.yaml.model.YPart

object OAuth2FlowValidations {
  case class ParticularFlow(name: String, requiredFields: List[FlowField])
  case class FlowField(name: String, field: Field)

  val authorizationUrl: FlowField = FlowField("authorizationUrl", OAuth2FlowModel.AuthorizationUri)
  val tokenUrl: FlowField         = FlowField("tokenUrl", OAuth2FlowModel.AccessTokenUri)
  val refreshUrl: FlowField       = FlowField("refreshUrl", OAuth2FlowModel.RefreshUri)
  val scopes: FlowField           = FlowField("scopes", OAuth2FlowModel.Scopes)

  val requiredFieldsPerFlow: Map[String, ParticularFlow] = Seq(
    // oas20 & 0as30
    ParticularFlow("implicit", List(authorizationUrl, scopes)),
    ParticularFlow("password", List(tokenUrl, scopes)),
    // oas30
    ParticularFlow("clientCredentials", List(tokenUrl, scopes)),
    ParticularFlow("authorizationCode", List(authorizationUrl, tokenUrl, scopes)),
    // oas20
    ParticularFlow("application", List(tokenUrl, scopes)),
    ParticularFlow("accessCode", List(authorizationUrl, tokenUrl, scopes))
  ).map(x => (x.name, x)).toMap

  def validateFlowFields(flow: OAuth2Flow, errorHandler: AMFErrorHandler, ast: YPart): Unit = {
    val flowName            = flow.flow.value()
    val requiredFlowsOption = requiredFieldsPerFlow.get(flowName)
    if (requiredFlowsOption.nonEmpty) {
      val missingFields =
        requiredFlowsOption.get.requiredFields.filter(flowField => flow.fields.entry(flowField.field).isEmpty)
      missingFields.foreach { flowField =>
        {
          val message = s"Missing ${flowField.name} for $flowName flow"
          errorHandler.violation(MissingOAuthFlowField, flow, message, ast.location)
        }
      }
    }
  }
}
