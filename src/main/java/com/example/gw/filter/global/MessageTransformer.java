package com.example.gw.filter.global;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.spec.InvalidParameterSpecException;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

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

			// 4. 변환된 JSON 문자열 반환
			return objectMapper.writeValueAsString(mappedNode);

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
