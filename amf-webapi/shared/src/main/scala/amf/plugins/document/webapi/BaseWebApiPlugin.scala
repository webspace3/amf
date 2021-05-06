package amf.plugins.document.webapi

import amf.ProfileName
import amf.client.plugins._
import amf.client.remod.amfcore.config.RenderOptions
import amf.client.remod.amfcore.plugins.validate.AMFValidatePlugin
import amf.core.annotations.{AutoGeneratedName, DeclaredElement, ExternalFragmentRef, InlineElement}
import amf.core.errorhandling.ErrorHandler
import amf.core.metamodel.document.FragmentModel
import amf.core.model.domain.AnnotationGraphLoader
import amf.core.remote.Vendor
import amf.core.unsafe.PlatformSecrets
import amf.core.validation.core.ValidationProfile
import amf.plugins.document.webapi.annotations._
import amf.plugins.document.webapi.contexts.SpecEmitterContext
import amf.plugins.document.webapi.metamodel.FragmentsTypesModels._
import amf.plugins.document.webapi.metamodel.{ExtensionModel, OverlayModel}
import amf.plugins.document.webapi.references.WebApiReferenceHandler
import amf.plugins.document.webapi.validation.remod.ValidatePlugins.{MODEL_PLUGIN, PARSER_PLUGIN, PAYLOAD_PLUGIN}
import amf.plugins.domain.shapes.DataShapesDomainPlugin
import amf.plugins.domain.webapi.APIDomainPlugin

import scala.concurrent.{ExecutionContext, Future}

trait BaseWebApiPlugin extends AMFDocumentPlugin with AMFValidationPlugin with PlatformSecrets {

  protected def vendor: Vendor

  override val ID: String = vendor.name

  override def referenceHandler(eh: ErrorHandler) = new WebApiReferenceHandler(ID)

  override def dependencies(): Seq[AMFPlugin] = Seq(
    APIDomainPlugin,
    DataShapesDomainPlugin,
    ExternalJsonYamlRefsPlugin
  )

  def validVendorsToReference: Seq[String] = List(ExternalJsonYamlRefsPlugin.ID)

  def specContext(options: RenderOptions, errorHandler: ErrorHandler): SpecEmitterContext

  /**
    * Does references in this type of documents be recursive?
    */
  override val allowRecursiveReferences: Boolean = false

  override def modelEntities: Seq[FragmentModel] = Seq(
    ExtensionModel,
    OverlayModel,
    DocumentationItemFragmentModel,
    DataTypeFragmentModel,
    NamedExampleFragmentModel,
    ResourceTypeFragmentModel,
    TraitFragmentModel,
    AnnotationTypeDeclarationFragmentModel,
    SecuritySchemeFragmentModel
  )

  override def serializableAnnotations(): Map[String, AnnotationGraphLoader] = Map(
    "parsed-json-schema"         -> ParsedJSONSchema,
    "parsed-raml-datatype"       -> ParsedRamlDatatype,
    "external-fragment-ref"      -> ExternalFragmentRef,
    "json-schema-id"             -> JSONSchemaId,
    "declared-element"           -> DeclaredElement,
    "inline-element"             -> InlineElement,
    "local-link-path"            -> LocalLinkPath,
    "form-body-parameter"        -> FormBodyParameter,
    "parameter-name-for-payload" -> ParameterNameForPayload,
    "required-param-payload"     -> RequiredParamPayload,
    "auto-generated-name"        -> AutoGeneratedName
  )

  val validationProfile: ProfileName

  /**
    * Validation profiles supported by this plugin by default
    */
  override def domainValidationProfiles: Seq[ValidationProfile]

  override protected[amf] def getRemodValidatePlugins(): Seq[AMFValidatePlugin] =
    Seq(PARSER_PLUGIN, MODEL_PLUGIN, PAYLOAD_PLUGIN)

  override def init()(implicit executionContext: ExecutionContext): Future[AMFPlugin] = Future.successful(this)

}
