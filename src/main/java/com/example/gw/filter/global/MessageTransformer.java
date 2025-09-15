package com.example.gw.filter.global;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.spec.InvalidParameterSpecException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class MessageTransformer {

	private final ObjectMapper objectMapper;
	private final String key;
	private final String iv;

	public MessageTransformer() {
		this.objectMapper = new ObjectMapper();
		this.key = "1234567890123456";
		this.iv = "1234567890123456";
	}

	/**
	 * 암호화된 Legacy JSON → 표준 JSON 문자열로 직접 변환
	 */
	public String transformToJson(String encryptedJson) {
		try {
			// 1. 복호화
			String plainJson = decryptAES(encryptedJson, this.key, this.iv, true);
			log.info("Decrypted legacy JSON: {}", plainJson);

			// 2. Jackson ObjectNode로 변환
			ObjectNode node = (ObjectNode)objectMapper.readTree(plainJson);

			// 3. 키 매핑 (LegacyKey enum 사용)
			ObjectNode mappedNode = objectMapper.createObjectNode();
			Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
			while (fields.hasNext()) {
				Map.Entry<String, JsonNode> entry = fields.next();
				String standardKey = LegacyKey.map(entry.getKey());
				mappedNode.set(standardKey, entry.getValue());
			}

			// 4. context구조 적용
			ObjectNode wrapped = objectMapper.createObjectNode();
			ObjectNode contextHeader = objectMapper.createObjectNode();
			ObjectNode contextData = objectMapper.createObjectNode();

			// 4-1) 헤더로 올릴 키 목록 (PaymentBase의 공통 필드)
			Set<String> headerKeys = new HashSet<>(Arrays.asList(
				"businessMethod", "merchantId"// TODO :: 일부는 생성 필요
			));

			// 4-2) Auth로 내릴 키
			//TODO :: 전문별 공통 객체들 모듈, 전문별 유연한 처리 필요
			Set<String> authKeys = new HashSet<>(Arrays.asList(
				"amount", "orderId", "itemName", "userId", "userName", "cancelUrl", "returnUrl", "isNoti"
			));

			// 4-3) 전문 Object 처리 TODO ::
			ObjectNode authNode = objectMapper.createObjectNode();
			JsonNode authObj = mappedNode.get("auth");
			if (authObj != null && authObj.isObject()) {
				authNode = ((ObjectNode) authObj).deepCopy();
			}

			// 4-4) 필드 분배
			Iterator<Map.Entry<String, JsonNode>> it = mappedNode.fields();
			while (it.hasNext()) {
				Map.Entry<String, JsonNode> e = it.next();
				String key = e.getKey();
				JsonNode value = e.getValue();

				if (headerKeys.contains(key)) {
					contextHeader.set(key, value);
					continue;
				}

				if (authKeys.contains(key)) {
					authNode.set(key, value);
					continue;
				}

				// 그 밖의 키는 contextData 루트로
				if (!"auth".equals(key)) { // 이미 preAuth를 병합했으므로 중복 방지
					contextData.set(key, value);
				}
			}

			// 4-5) auth가 하나라도 있으면 contextData.auth로 삽입
			if (authNode.size() > 0) {
				contextData.set("auth", authNode);
			}

			// 최종 래핑
			contextHeader.put("timestamp", String.valueOf(LocalDateTime.now()));
			contextHeader.put("sourceSystem", "ASIS");
			contextHeader.put("traceId", "ASISTraceIdGenerated");

			wrapped.set("contextHeader", contextHeader);
			wrapped.set("contextData", contextData);

			// 5. 변환된 JSON 문자열 반환
			log.info("Mapped legacy JSON (context wrapped): {}", wrapped);
			return objectMapper.writeValueAsString(wrapped);

		} catch (Exception e) {
			log.error("Failed to transform legacy request", e);
			throw new RuntimeException("Legacy transformation failed", e);
		}
	}

	//TODO :: encrypt util 사용하도록 변경 필요

	private static final String operation_cbc = "/CBC/";
	private static final String operation_ecb = "/ECB/";

	private static final List<String> paddingMode = List.of("PKCS5Padding", "NoPadding", "ZeroPadding");


	public static String decryptAES(String cipherText, String key, String iv, boolean isChaining) throws
		GeneralSecurityException {
		return decrypt(cipherText, key, iv, isChaining, "PKCS5Padding");
	}

	public static String decrypt(String cipherText, String key, String iv, boolean isChaining, String padding) throws
		GeneralSecurityException {

		if(!paddingMode.contains(padding)){
			throw new InvalidParameterSpecException();
		}

		String decValue;
		SecretKeySpec keySpec = new SecretKeySpec(key.getBytes(), "AES");
		byte[] decodeBytes = Base64.getDecoder().decode(cipherText);

		if(isChaining){
			Cipher cipher = Cipher.getInstance("AES" + operation_cbc + padding/*, "BC"*/);
			IvParameterSpec ivSpec = new IvParameterSpec(iv.getBytes());
			cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
			byte[] decrypted = cipher.doFinal(decodeBytes);
			decValue = new String(decrypted, StandardCharsets.UTF_8);
		}else{
			Cipher cipher = Cipher.getInstance("AES" + operation_ecb + padding/*, "BC"*/);
			cipher.init(Cipher.DECRYPT_MODE, keySpec);
			byte[] decrypted = cipher.doFinal(decodeBytes);
			decValue = new String(decrypted, StandardCharsets.UTF_8);
		}
		return decValue;
	}

}
