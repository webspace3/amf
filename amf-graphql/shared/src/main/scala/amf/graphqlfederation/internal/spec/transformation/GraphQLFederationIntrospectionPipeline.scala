package amf.graphqlfederation.internal.spec.transformation

import amf.apicontract.internal.transformation.GraphQLEditingPipeline
import amf.core.client.scala.transform.{TransformationPipeline, TransformationStep}
import amf.core.internal.transform.stages.UrlShortenerStage

object GraphQLFederationIntrospectionPipeline extends TransformationPipeline {
  override val name: String = "Introspection"

  override def steps: Seq[TransformationStep] =
    GraphQLEditingPipeline().steps.filterNot(_.isInstanceOf[UrlShortenerStage]) ++ List(
      IntrospectionElementsAdditionStep,
      new UrlShortenerStage()
    )
}
