ModelId: file://amf-cli/shared/src/test/resources/org/raml/parser/types/multiple-inheritance-validation/input.raml
Profile: RAML 1.0
Conforms: false
Number of results: 1

Level: Violation

- Constraint: http://a.ml/vocabularies/amf/validation#example-validation-error
  Message: age should be integer
id should be string

  Severity: Violation
  Target: file://amf-cli/shared/src/test/resources/org/raml/parser/types/multiple-inheritance-validation/input.raml#/web-api/endpoint/%2Fresource/supportedOperation/get/returns/resp/200/payload/application%2Fjson/shape/schema/examples/example/default-example
  Property: file://amf-cli/shared/src/test/resources/org/raml/parser/types/multiple-inheritance-validation/input.raml#/web-api/endpoint/%2Fresource/supportedOperation/get/returns/resp/200/payload/application%2Fjson/shape/schema/examples/example/default-example
  Range: [(27,0)-(29,25)]
  Location: file://amf-cli/shared/src/test/resources/org/raml/parser/types/multiple-inheritance-validation/input.raml
