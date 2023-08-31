ModelId: file://amf-cli/shared/src/test/resources/validations/production/inheritance.raml
Profile: RAML 1.0
Conforms: false
Number of results: 4

Level: Violation

- Constraint: http://a.ml/vocabularies/amf/resolution#invalid-type-inheritance
  Message: Resolution error: Invalid scalar inheritance base type http://a.ml/vocabularies/shapes#number < http://www.w3.org/2001/XMLSchema#string 
  Severity: Violation
  Target: file://amf-cli/shared/src/test/resources/validations/production/inheritance.raml#/declares/shape/NumberOrString/property/property/name/union/name/0
  Property: http://a.ml/vocabularies/shapes#inherits
  Range: [(48,14)-(48,20)]
  Location: file://amf-cli/shared/src/test/resources/validations/production/inheritance.raml

- Constraint: http://a.ml/vocabularies/amf/resolution#invalid-type-inheritance
  Message: Resolution error: Invalid scalar inheritance base type http://a.ml/vocabularies/shapes#number < http://www.w3.org/2001/XMLSchema#boolean 
  Severity: Violation
  Target: file://amf-cli/shared/src/test/resources/validations/production/inheritance.raml#/declares/shape/NumberOrString/property/property/name/union/name/2
  Property: http://a.ml/vocabularies/shapes#inherits
  Range: [(48,14)-(48,20)]
  Location: file://amf-cli/shared/src/test/resources/validations/production/inheritance.raml

- Constraint: http://a.ml/vocabularies/amf/resolution#invalid-type-inheritance
  Message: Resolution error: Invalid scalar inheritance base type http://www.w3.org/2001/XMLSchema#string < http://a.ml/vocabularies/shapes#number 
  Severity: Violation
  Target: file://amf-cli/shared/src/test/resources/validations/production/inheritance.raml#/declares/shape/NumberOrString/property/property/name/union/name/4
  Property: http://a.ml/vocabularies/shapes#inherits
  Range: [(48,21)-(48,27)]
  Location: file://amf-cli/shared/src/test/resources/validations/production/inheritance.raml

- Constraint: http://a.ml/vocabularies/amf/resolution#invalid-type-inheritance
  Message: Resolution error: Invalid scalar inheritance base type http://www.w3.org/2001/XMLSchema#string < http://www.w3.org/2001/XMLSchema#boolean 
  Severity: Violation
  Target: file://amf-cli/shared/src/test/resources/validations/production/inheritance.raml#/declares/shape/NumberOrString/property/property/name/union/name/5
  Property: http://a.ml/vocabularies/shapes#inherits
  Range: [(48,21)-(48,27)]
  Location: file://amf-cli/shared/src/test/resources/validations/production/inheritance.raml
