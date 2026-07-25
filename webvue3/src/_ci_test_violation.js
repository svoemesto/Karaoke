// Временный файл для живого теста T072 (specs/002-ci-lint-enforcement) —
// намеренно нарушает prettier (double quotes + semi, хотя конфиг требует
// singleQuote+no-semi). Будет удалён сразу после подтверждения, что CI валится.
export const ciTestViolation = "double quotes not allowed";
