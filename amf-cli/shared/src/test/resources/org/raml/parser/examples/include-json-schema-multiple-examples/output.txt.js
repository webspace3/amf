ModelId: file://amf-cli/shared/src/test/resources/org/raml/parser/examples/include-json-schema-multiple-examples/input.raml
Profile: RAML 1.0
Conforms: false
Number of results: 1

Level: Violation

- Constraint: http://a.ml/vocabularies/amf/validation#example-validation-error
  Message: age should be integer
name should be string

  Severity: Violation
  Target: file://amf-cli/shared/src/test/resources/org/raml/parser/examples/include-json-schema-multiple-examples/input.raml#/declarations/types/User/example/bob
  Property: file://amf-cli/shared/src/test/resources/org/raml/parser/examples/include-json-schema-multiple-examples/input.raml#/declarations/types/User/example/bob
  Range: [(8,10)-(11,11)]
  Location: file://amf-cli/shared/src/test/resources/org/raml/parser/examples/include-json-schema-multiple-examples/input.raml
