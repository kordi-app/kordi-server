package com.kordi_api.global.openapi;

import com.kordi_api.global.exception.CoreExceptionCode;
import io.swagger.v3.core.jackson.ModelResolver;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.servers.Server;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 스펙 (/v3/api-docs) 설정. 스펙은 클라이언트 타입 생성용이다 (client: {@code pnpm gen:api}). 엔드포인트는 {@code
 * springdoc.api-docs.enabled}가 켜졌을 때만 뜨고, 기본값은 꺼짐이다.
 *
 * <p>스펙이 실제 JSON과 같도록 손보는 것:
 *
 * <ul>
 *   <li>DTO의 모든 프로퍼티를 required로: Jackson은 record의 모든 컴포넌트를 (null이어도) 쓴다. null이 될 수 있는 것은 DTO에
 *       {@code @Nullable}로 표시한다.
 *   <li>성공 envelope({@code ApiResponse<T>})은 {@code { data }}만 — code/message/path는 실패 때만 온다.
 *   <li>실패 envelope은 {@value #ERROR_SCHEMA}로 모든 operation의 default 응답에 붙이고, 그 code는 {@link
 *       CoreExceptionCode} 전체를 담은 {@value #ERROR_CODE_SCHEMA} enum이다 ({@code x-error-codes}에 이름·상태
 *       포함).
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

  static final String ERROR_SCHEMA = "ApiErrorResponse";
  static final String ERROR_CODE_SCHEMA = "ErrorCode";

  /** springdoc이 제네릭 {@code ApiResponse<T>}에 붙이는 이름의 접두사 (ApiResponseUserResponse 등) */
  private static final String ENVELOPE_PREFIX = "ApiResponse";

  private static final String VOID_ENVELOPE = ENVELOPE_PREFIX + "Void";

  private static final String SCHEMA_REF = "#/components/schemas/";

  static {
    // enum을 인라인 대신 components/schemas의 이름 있는 스키마로 (클라이언트가 Difficulty 등을 이름으로 쓴다)
    ModelResolver.enumsAsRef = true;
  }

  @Bean
  OpenAPI kordiOpenApi() {
    return new OpenAPI()
        .info(new Info().title("Kordi API").version("v1"))
        // 실행 환경마다 바뀌는 호스트 대신 고정값: 생성된 스펙이 커밋해도 흔들리지 않게
        .servers(List.of(new Server().url("/")));
  }

  @Bean
  OpenApiCustomizer kordiContractCustomizer() {
    return openApi -> {
      Components components = openApi.getComponents();
      if (components == null) {
        components = new Components();
        openApi.setComponents(components);
      }
      if (components.getSchemas() != null) {
        components.getSchemas().forEach(OpenApiConfig::describeJsonShape);
      }
      components.addSchemas(ERROR_CODE_SCHEMA, errorCodeSchema());
      components.addSchemas(ERROR_SCHEMA, errorSchema());

      if (openApi.getPaths() != null) {
        openApi
            .getPaths()
            .values()
            .forEach(
                path ->
                    path.readOperations()
                        .forEach(
                            operation ->
                                operation
                                    .getResponses()
                                    .addApiResponse("default", errorResponse())));
      }
    };
  }

  @SuppressWarnings("rawtypes")
  private static void describeJsonShape(String name, Schema schema) {
    Map<String, Schema> properties = schema.getProperties();
    if (properties == null || properties.isEmpty()) {
      return;
    }
    if (name.startsWith(ENVELOPE_PREFIX)) {
      // ApiResponse<Void> (ApiResponse.success()): data는 null이라 NON_NULL로 빠진다 → 빈 객체
      properties.keySet().retainAll(name.equals(VOID_ENVELOPE) ? List.of() : List.of("data"));
      schema.setRequired(properties.isEmpty() ? null : new ArrayList<>(properties.keySet()));
      return;
    }
    schema.setRequired(new ArrayList<>(properties.keySet()));
  }

  private static Schema<Number> errorCodeSchema() {
    List<Map<String, Object>> codes = new ArrayList<>();
    for (CoreExceptionCode code : CoreExceptionCode.values()) {
      Map<String, Object> entry = new LinkedHashMap<>();
      entry.put("name", code.name());
      entry.put("code", code.getCode());
      entry.put("status", code.getStatus().value());
      entry.put("message", code.getMessage());
      codes.add(entry);
    }
    Schema<Number> schema = new IntegerSchema();
    schema.description("CoreExceptionCode.code — 실패 응답의 상세 코드");
    schema.setEnum(
        Arrays.stream(CoreExceptionCode.values()).<Number>map(CoreExceptionCode::getCode).toList());
    schema.addExtension(
        "x-enum-varnames", Arrays.stream(CoreExceptionCode.values()).map(Enum::name).toList());
    schema.addExtension("x-error-codes", codes);
    return schema;
  }

  @SuppressWarnings("rawtypes")
  private static Schema errorSchema() {
    return new ObjectSchema()
        .description("실패 응답 envelope (GlobalExceptionHandler)")
        .addProperty("code", new Schema<>().$ref(SCHEMA_REF + ERROR_CODE_SCHEMA))
        .addProperty("message", new StringSchema())
        .addProperty("path", new StringSchema())
        .required(List.of("code", "message", "path"));
  }

  private static ApiResponse errorResponse() {
    return new ApiResponse()
        .description("Error (CoreExceptionCode)")
        .content(
            new Content()
                .addMediaType(
                    "application/json",
                    new MediaType().schema(new Schema<>().$ref(SCHEMA_REF + ERROR_SCHEMA))));
  }
}
