ModelId: file://amf-cli/shared/src/test/resources/org/raml/parser/examples/complex/input.raml
Profile: RAML 1.0
Conforms: false
Number of results: 1

Level: Violation

- Constraint: http://a.ml/vocabularies/amf/validation#example-validation-error
  Message: Head.phone should match pattern "^[0-9|-]+$"
  Severity: Violation
  Target: file://amf-cli/shared/src/test/resources/org/raml/parser/examples/complex/input.raml#/web-api/end-points/%2Forgs%2F%7BorgId%7D/get/200/application%2Fjson/schema/example/default-example
  Property: file://amf-cli/shared/src/test/resources/org/raml/parser/examples/complex/input.raml#/web-api/end-points/%2Forgs%2F%7BorgId%7D/get/200/application%2Fjson/schema/example/default-example
  Range: [(44,0)-(59,31)]
  Location: file://amf-cli/shared/src/test/resources/org/raml/parser/examples/complex/input.raml
