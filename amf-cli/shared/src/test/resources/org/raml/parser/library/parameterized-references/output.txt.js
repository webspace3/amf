ModelId: file://amf-cli/shared/src/test/resources/org/raml/parser/library/parameterized-references/input.raml
Profile: RAML 1.0
Conforms: false
Number of results: 2

Level: Violation

- Constraint: http://a.ml/vocabularies/amf/validation#example-validation-error
  Message: should be equal to one of the allowed values
  Severity: Violation
  Target: file://amf-cli/shared/src/test/resources/org/raml/parser/library/parameterized-references/input.raml#/web-api/endpoint/%2Fuser/supportedOperation/post/expects/request/header/parameter/header/broken-no-params/scalar/schema/examples/example/default-example
  Property: file://amf-cli/shared/src/test/resources/org/raml/parser/library/parameterized-references/input.raml#/web-api/endpoint/%2Fuser/supportedOperation/post/expects/request/header/parameter/header/broken-no-params/scalar/schema/examples/example/default-example
  Range: [(26,29)-(26,34)]
  Location: file://amf-cli/shared/src/test/resources/org/raml/parser/library/parameterized-references/liba.raml

- Constraint: http://a.ml/vocabularies/amf/validation#example-validation-error
  Message: should be equal to one of the allowed values
  Severity: Violation
  Target: file://amf-cli/shared/src/test/resources/org/raml/parser/library/parameterized-references/input.raml#/web-api/endpoint/%2Fuser/supportedOperation/post/expects/request/header/parameter/header/broken-example-param/scalar/schema/examples/example/default-example
  Property: file://amf-cli/shared/src/test/resources/org/raml/parser/library/parameterized-references/input.raml#/web-api/endpoint/%2Fuser/supportedOperation/post/expects/request/header/parameter/header/broken-example-param/scalar/schema/examples/example/default-example
  Range: [(29,29)-(29,47)]
  Location: file://amf-cli/shared/src/test/resources/org/raml/parser/library/parameterized-references/liba.raml
