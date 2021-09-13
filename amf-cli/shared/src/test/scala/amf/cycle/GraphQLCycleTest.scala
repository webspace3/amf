package amf.cycle

import amf.apicontract.client.scala.AMFConfiguration
import amf.core.client.scala.config.RenderOptions
import amf.core.client.scala.errorhandling.{AMFErrorHandler, IgnoringErrorHandler, UnhandledErrorHandler}
import amf.core.internal.remote.{AmfJsonHint, GraphQLHint, GrpcProtoHint}
import amf.graphql.client.scala.GraphQLConfiguration
import amf.io.FunSuiteCycleTests

trait GraphQLFunSuiteCycleTests extends FunSuiteCycleTests {
  override def buildConfig(options: Option[RenderOptions], eh: Option[AMFErrorHandler]): AMFConfiguration = {
    val amfConfig: AMFConfiguration = GraphQLConfiguration.GraphQL()
    val renderedConfig: AMFConfiguration = options.fold(amfConfig.withRenderOptions(renderOptions()))(r => {
      amfConfig.withRenderOptions(r)
    })
    eh.fold(renderedConfig.withErrorHandlerProvider(() => IgnoringErrorHandler))(e =>
      renderedConfig.withErrorHandlerProvider(() => e))
  }

}


class GraphQLCycleTest extends GraphQLFunSuiteCycleTests {
  override def basePath: String = "amf-cli/shared/src/test/resources/upanddown/cycle/graphql/"


  test("Can cycle through a simple GraphQL API") {
    cycle("simple/api.graphql", "simple/api.jsonld", GraphQLHint, AmfJsonHint)
  }

  test("HERE_HERE Can cycle through a simple GraphQL API") {
    cycle("simple/api.graphql", "simple/dumped.graphql", GraphQLHint, GraphQLHint)
  }

}

/*
class GraphQLParserTest extends GraphQLFunSuiteCycleTests {
  override def basePath: String = "amf-cli/shared/src/test/resources/upanddown/graphql/simple/"

  multiGoldenTest("HERE_HERE Can generate simple GraphQL specs", "api.%s") { config =>
    cycle(
      "api.graphql",
      config.golden,
      GraphQLHint,
      AmfJsonHint,
      renderOptions = Some(config.renderOptions.withSourceMaps.withPrettyPrint),
      eh = Some(UnhandledErrorHandler)
    )
  }
}
*/