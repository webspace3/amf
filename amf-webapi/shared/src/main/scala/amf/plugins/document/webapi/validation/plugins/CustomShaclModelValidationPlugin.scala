package amf.plugins.document.webapi.validation.plugins
import amf.client.remod.amfcore.plugins.validate.ValidationInfo
import amf.core.services.RuntimeValidator.CustomShaclFunctions
import amf.core.services.ShaclValidationOptions
import amf.plugins.document.webapi.validation.plugins.BaseApiValidationPlugin.standardApiProfiles
import amf.plugins.features.validation.shacl.ShaclValidator
import amf.plugins.features.validation.shacl.custom.CustomShaclValidator
import amf.validations.CustomShaclFunctions

object CustomShaclModelValidationPlugin {

  protected val id: String                      = this.getClass.getSimpleName
  protected val functions: CustomShaclFunctions = CustomShaclFunctions.functions

  def apply() = new CustomShaclModelValidationPlugin()
}

class CustomShaclModelValidationPlugin extends ShaclValidationPlugin {

  override val id: String = CustomShaclModelValidationPlugin.id

  override def applies(element: ValidationInfo): Boolean =
    super.applies(element) && standardApiProfiles.contains(element.profile)

  override protected def validator(options: ShaclValidationOptions): ShaclValidator =
    new CustomShaclValidator(CustomShaclModelValidationPlugin.functions, options)
}
