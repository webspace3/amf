ModelId: file://amf-cli/shared/src/test/resources/validations/jsonschema/not/api4.raml
Profile: RAML 1.0
Conforms: false
Number of results: 1

Level: Violation

- Constraint: http://a.ml/vocabularies/amf/validation#example-validation-error
  Message: foo should NOT be valid
  Severity: Violation
  Target: file://amf-cli/shared/src/test/resources/validations/jsonschema/not/api4.raml#/web-api/endpoint/%2Fep1/supportedOperation/get/returns/resp/200/payload/application%2Fjson/shape/schema/examples/example/default-example
  Property: file://amf-cli/shared/src/test/resources/validations/jsonschema/not/api4.raml#/web-api/endpoint/%2Fep1/supportedOperation/get/returns/resp/200/payload/application%2Fjson/shape/schema/examples/example/default-example
  Range: [(22,0)-(25,0)]
  Location: file://amf-cli/shared/src/test/resources/validations/jsonschema/not/api4.raml
