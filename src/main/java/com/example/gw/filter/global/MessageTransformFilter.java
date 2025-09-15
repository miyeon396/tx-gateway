package com.example.gw.filter.global;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import org.springframework.web.server.ServerWebExchange;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@Slf4j
@Order(1)
public class MessageTransformFilter implements GlobalFilter {
	// before 암호화 데이터 → 복호화 → JSON 파싱 → Object 생성 → Object를 JSON으로 다시 변환 → byte[]
	// after 암호화 데이터 → 복호화 → JSON 파싱 → 키 매핑 → JSON 문자열 → byte[]

	private final MessageTransformer transformer;

	public MessageTransformFilter(MessageTransformer transformer) {
		this.transformer = transformer;
	}

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		ServerHttpRequest request = exchange.getRequest();

		if (isLegacyRequest(request)) {
			log.info("Legacy request detected: {}", request.getURI());

			// Query parameter에서 DATA 추출
			String dataParam = request.getQueryParams().getFirst("DATA");
			if (ObjectUtils.isEmpty(dataParam)) {
				log.error("DATA query parameter 미존재");
				return handleError(exchange, "DATA query parameter 미존재");
			}

			try {
				// Legacy 데이터를 바로 JSON으로 변환
				String transformedJson = transformer.transformToJson(dataParam);
				byte[] transformedBody = transformedJson.getBytes(StandardCharsets.UTF_8);

				// 새로운 Request 생성 (헤더 + 바디 변환)
				ServerHttpRequest modifiedRequest = exchange.getRequest().mutate()
					.header("Content-Type", "application/json")
					.header("Content-Length", String.valueOf(transformedBody.length))
					.header("X-Transformed", "true")
					.header("X-Original-Format", "legacy")
					// .header("X-TXID", generateTxId()) // 공통 헤더 추가
					.header("X-Request-Time", Instant.now().toString())
					.method(HttpMethod.POST) // Legacy는 보통 POST로 변환
					.build();

				// Body를 가진 새로운 Request 생성 (간단한 방법)
				ServerHttpRequestDecorator decorator = new ServerHttpRequestDecorator(modifiedRequest) {
					@Override
					public Flux<DataBuffer> getBody() {
						DataBufferFactory bufferFactory = exchange.getResponse().bufferFactory();
						DataBuffer buffer = bufferFactory.wrap(transformedBody);
						return Flux.just(buffer);
					}
				};
				return chain.filter(exchange.mutate().request(decorator).build());

			} catch (Exception e) {
				log.error("Failed to transform legacy request", e);
				return handleError(exchange, "Legacy transformation failed");
			}
		} else {
			log.info("Standard request detected: {}", request.getURI());
			// 표준 요청에도 공통 헤더 추가
			ServerHttpRequest modifiedRequest = exchange.getRequest().mutate()
				// .header("X-TXID", generateTxId())
				.header("X-Request-Time", Instant.now().toString())
				.build();

			return chain.filter(exchange.mutate().request(modifiedRequest).build());
		}

	}


	private boolean isLegacyRequest(ServerHttpRequest request) {
		return request.getQueryParams().containsKey("DATA");
	}

	private Mono<Void> handleError(ServerWebExchange exchange, String message) {
		ServerHttpResponse response = exchange.getResponse();
		response.setStatusCode(HttpStatus.BAD_REQUEST);
		response.getHeaders().add("Content-Type", "application/json");

		String errorResponse = "{\"error\":\"" + message + "\"}";
		DataBuffer buffer = response.bufferFactory().wrap(errorResponse.getBytes());
		return response.writeWith(Mono.just(buffer));
	}
}
