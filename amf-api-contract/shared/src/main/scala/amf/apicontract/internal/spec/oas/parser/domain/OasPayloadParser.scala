package amf.apicontract.internal.spec.oas.parser.domain

import amf.apicontract.client.scala.model.domain.Payload
import amf.apicontract.internal.metamodel.domain.PayloadModel
import amf.apicontract.internal.spec.common.parser.{SpecParserOps}
import amf.apicontract.internal.spec.oas.parser.context.OasWebApiContext
import amf.core.internal.parser.YMapOps
import amf.core.internal.parser.domain.{Annotations, ScalarNode}
import amf.shapes.internal.domain.resolution.ExampleTracking.tracking
import amf.shapes.internal.spec.common.parser.AnnotationParser
import amf.shapes.internal.spec.oas.parser.OasTypeParser
import org.yaml.model.{YMap, YNode}

case class OasPayloadParser(node: YNode, producer: Option[String] => Payload)(implicit ctx: OasWebApiContext)
    extends SpecParserOps {
  def parse(): Payload = {
    val map = node.as[YMap]
    val payload = producer(
      map.key("mediaType").map(entry => ScalarNode(entry.value).text().value.toString)
    ).add(Annotations.valueNode(map))

    // todo set again for not lose annotations?

    map.key("name", PayloadModel.Name in payload)
    map.key("mediaType", PayloadModel.MediaType in payload)

    map.key(
      "schema",
      entry => {
        OasTypeParser(entry, shape => shape.withName("schema"))
          .parse()
          .map(s => payload.setWithoutId(PayloadModel.Schema, tracking(s, payload), Annotations(entry)))
      }
    )

    AnnotationParser(payload, map).parse()

    payload
  }
}
