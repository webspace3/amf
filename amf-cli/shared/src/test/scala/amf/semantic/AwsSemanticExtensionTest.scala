package amf.semantic

import amf.core.client.scala.config.RenderOptions
import amf.core.internal.remote.{Mimes, Oas30}
import amf.io.FileAssertionTest
import org.scalatest.Assertion
import org.scalatest.funsuite.AsyncFunSuite

import scala.concurrent.{ExecutionContext, Future}

class AwsSemanticExtensionTest extends AsyncFunSuite with SemanticExtensionParseTest with FileAssertionTest {

  override implicit val executionContext: ExecutionContext = ExecutionContext.Implicits.global

  override protected val basePath = "amf-cli/shared/src/test/resources/semantic/aws"

  private val renderOptions =
    RenderOptions().withPrettyPrint.withoutSourceMaps.withoutRawSourceMaps.withCompactUris

  fs.syncFile(s"$basePath/apis")
    .list
    .filter(_.endsWith(".yaml"))
    .foreach { file =>
      test(s"AWS semex > Test $file") {
        cycleAndAssertGoldenJsonLd(file)
      }
    }

  protected def cycleAndAssertGoldenJsonLd(api: String): Future[Assertion] = {
    val spec        = Oas30
    val dialectFile = "dialect.yaml"
    val goldenFile  = api.replace(".yaml", ".jsonld")
    for {
      dialect <- getDialect(s"file://$basePath/$dialectFile").map(_.dialect)
      client  <- Future.successful { extendConfig(dialect, spec).withRenderOptions(renderOptions).baseUnitClient() }
      parsingResult <- client.parseDocument(s"file://$basePath/apis/$api")
      actualString <- Future.successful {
        val transformResult = client.transform(parsingResult.baseUnit)
        client.render(transformResult.baseUnit, Mimes.`application/ld+json`)
      }
      actualFile <- writeTemporaryFile(s"$basePath/apis/$goldenFile")(actualString)
      assertion  <- assertDifferences(actualFile, s"$basePath/apis/$goldenFile")
    } yield {
      assertion
    }
  }

}
