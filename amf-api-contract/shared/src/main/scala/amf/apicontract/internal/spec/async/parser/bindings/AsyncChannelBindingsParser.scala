package amf.apicontract.internal.spec.async.parser.bindings

import amf.apicontract.client.scala.model.domain.bindings.{ChannelBinding, ChannelBindings}
import amf.apicontract.internal.metamodel.domain.bindings._
import amf.apicontract.internal.spec.async.parser.bindings.Bindings.{Amqp, AnypointMQ, IBMMQ, WebSockets}
import amf.apicontract.internal.spec.async.parser.bindings.channel.{
  Amqp091ChannelBindingParser,
  AnypointMQChannelBindingParser,
  IBMMQChannelBindingParser,
  WebSocketsChannelBindingParser
}
import amf.apicontract.internal.spec.async.parser.context.AsyncWebApiContext
import amf.apicontract.internal.spec.common.WebApiDeclarations.ErrorChannelBindings
import amf.apicontract.internal.spec.spec.OasDefinitions
import amf.core.client.scala.model.domain.AmfScalar
import amf.core.internal.metamodel.Field
import amf.core.internal.parser.domain.{Annotations, SearchScope}
import amf.shapes.internal.spec.common.parser.YMapEntryLike

object AsyncChannelBindingsParser {
  private val parserMap: Map[String, BindingParser[ChannelBinding]] = Map(
    Amqp       -> Amqp091ChannelBindingParser,
    WebSockets -> WebSocketsChannelBindingParser,
    IBMMQ      -> IBMMQChannelBindingParser,
    AnypointMQ -> AnypointMQChannelBindingParser
  )
}

case class AsyncChannelBindingsParser(entryLike: YMapEntryLike)(implicit ctx: AsyncWebApiContext)
    extends AsyncBindingsParser(entryLike) {

  override type Binding            = ChannelBinding
  override protected type Bindings = ChannelBindings
  override protected val bindingsField: Field                                = ChannelBindingsModel.Bindings
  override protected val parsers: Map[String, BindingParser[ChannelBinding]] = AsyncChannelBindingsParser.parserMap

  override protected def createBindings(): ChannelBindings = ChannelBindings()

  override protected def createParser(entryLike: YMapEntryLike)(implicit ctx: AsyncWebApiContext): AsyncBindingsParser =
    AsyncChannelBindingsParser(entryLike)

  def handleRef(fullRef: String): ChannelBindings = {
    val label = OasDefinitions.stripOas3ComponentsPrefix(fullRef, "channelBindings")
    ctx.declarations
      .findChannelBindings(label, SearchScope.Named)
      .map(channelBindings =>
        nameAndAdopt(
          channelBindings.link(AmfScalar(label), entryLike.annotations, Annotations.synthesized()),
          entryLike.key
        )
      )
      .getOrElse(remote(fullRef, entryLike))
  }

  override protected def errorBindings(fullRef: String, entryLike: YMapEntryLike): ChannelBindings =
    new ErrorChannelBindings(fullRef, entryLike.asMap)
}
