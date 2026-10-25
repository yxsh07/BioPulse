import SwaggerParser from "@apidevtools/swagger-parser";
await SwaggerParser.validate(
  new URL("../../docs/openapi.yaml", import.meta.url).pathname.replace(
    /^\/(?=[A-Za-z]:)/,
    "",
  ),
);
console.log("OpenAPI contract valid");
