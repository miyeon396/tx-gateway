package com.example.gw;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.spec.InvalidParameterSpecException;
import java.util.Base64;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class RequestTest {

	@Test
	public void test() throws Exception {
		String plainTid = "202509151343009247311007000004";
		String type = "BILL";

		String tidStr = "\"TID\":\""+plainTid+"\",";
		String startStr = "{";
		String plain =
			  "    \"merchantId\": \"CPID0123\",\n"
			+ "    \"serviceType\": \"COMMON\",\n"
			+ "    \"type\": \""+type+"\",\n"
			+ "    \"amount\": 2000,\n"
			+ "    \"orderId\": \"orderidorderidorderid\",\n"
			+ "    \"itemName\": \"사과\",\n"
			+ "    \"userId\": \"asisUserId\",\n"
			+ "    \"userName\": \"asisUser\",\n"
			+ "    \"returnUrl\": \"return.com\",\n"
			+ "    \"testColumn\": \"Test\",\n"
			+ "    \"bankCode\": \"kb\"\n";
		String endStr = "}";

		String join;
		if (type.equals("AUTH")) {
			join = String.format("%s%s%s", startStr, plain, endStr);
		}  else {
			join = String.format("%s%s%s%s",startStr, tidStr, plain, endStr);
		}
		System.out.println("join >> " + join);


		String key = "1234567890123456"; // AES-128 키
		String iv = "1234567890123456";  // IV

		String encrypted = encryptAES(join, key, iv, true);
		System.out.println("Encrypted (Hex): " + encrypted);

		String decrypted = decryptAES(encrypted, key, iv, true);
		System.out.println("Decrypted: " + decrypted);

	}

	public static String encryptAES(String plainText, String key, String iv, boolean isChaining) throws
		GeneralSecurityException {
		return encryptAES(plainText, key, iv, isChaining, "PKCS5Padding");
	}

	public static String encryptAES(String plainText, String key, String iv, boolean isChaining, String padding) throws
		GeneralSecurityException {
		return encrypt(plainText, key, iv, isChaining, padding);
	}

	public static String encrypt(String plainText, String key, String iv, boolean isChaining, String padding) throws
		GeneralSecurityException {

		if(!paddingMode.contains(padding)){
			throw new InvalidParameterSpecException();
		}
		Cipher cipher;
		String encValue;
		SecretKeySpec keySpec = new SecretKeySpec(key.getBytes(), "AES");
		if(isChaining){
			cipher = Cipher.getInstance("AES" + operation_cbc + padding/*, "BC"*/);
			IvParameterSpec ivSpec = new IvParameterSpec(iv.getBytes());
			cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
			byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
			encValue = Base64.getEncoder().encodeToString(encrypted);
		}else{
			cipher = Cipher.getInstance("AES" + operation_ecb + padding/*,"BC"*/);
			cipher.init(Cipher.ENCRYPT_MODE, keySpec);
			byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
			encValue = Base64.getEncoder().encodeToString(encrypted);
		}
		return encValue;
	}

	public static String decryptAES(String cipherText, String key, String iv, boolean isChaining) throws
		GeneralSecurityException {
		return decrypt(cipherText, key, iv, isChaining, "PKCS5Padding");
	}

	private static final String operation_cbc = "/CBC/";
	private static final String operation_ecb = "/ECB/";

	private static final List<String> paddingMode = List.of("PKCS5Padding", "NoPadding", "ZeroPadding");

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
