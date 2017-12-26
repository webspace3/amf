package amf.plugins.document

import java.util.concurrent.CompletableFuture

import amf.core.metamodel.Obj
import amf.core.remote.FutureConverter._
import amf.core.unsafe.PlatformSecrets
import amf.model.document.DialectFragment
import amf.model.domain.DomainEntity
import amf.plugins.document.vocabularies.RAMLVocabulariesPlugin
import amf.plugins.document.vocabularies.metamodel.document.DialectNodeFragmentModel
import amf.plugins.document.vocabularies.metamodel.domain.DialectEntityModel
import amf.plugins.document.vocabularies.model.{document, domain}
import amf.plugins.document.vocabularies.spec.Dialect

object Vocabularies extends PlatformSecrets{

  def register() = {
    val p: (Obj) => Boolean = (x: Obj) => x.isInstanceOf[DialectEntityModel]
    platform.registerWrapperPredicate(p) {
      case m: domain.DomainEntity => new DomainEntity(m)
    }
    platform.registerWrapper(DialectNodeFragmentModel) {
      case d: document.DialectFragment => new DialectFragment(d)
    }

    amf.Core.registerPlugin(RAMLVocabulariesPlugin)
  }

  def registerDialect(url: String): CompletableFuture[Dialect] = RAMLVocabulariesPlugin.registerDialect(url).asJava
  def registerDialect(url: String, dialectText: String): CompletableFuture[Dialect] = RAMLVocabulariesPlugin.registerDialect(url, dialectText).asJava

}
