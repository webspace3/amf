ModelId: file://amf-cli/shared/src/test/resources/validations/facets/min-max-zeros-other.raml
Profile: RAML 1.0
Conforms: false
Number of results: 1

Level: Violation

- Constraint: http://a.ml/vocabularies/amf/validation#example-validation-error
  Message: should be <= 33
  Severity: Violation
  Target: file://amf-cli/shared/src/test/resources/validations/facets/min-max-zeros-other.raml#/shape/property/SSN%3F/scalar/SSN%3F/example/default-example
  Property: file://amf-cli/shared/src/test/resources/validations/facets/min-max-zeros-other.raml#/shape/property/SSN%3F/scalar/SSN%3F/example/default-example
  Range: [(7,13)-(7,19)]
  Location: file://amf-cli/shared/src/test/resources/validations/facets/min-max-zeros-other.raml
