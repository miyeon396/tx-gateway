package com.example.gw.filter.global;

import java.util.Set;

import lombok.Getter;

@Getter
public enum LegacyKey {
	USER_ID("id", Set.of("USERID", "USER", "USERIDENTIFICATION")),
	USER_EMAIL("userEmail", Set.of("EMAIL")),
	USER_NAME("userName", Set.of("USERNAME")),

	TX_ID("txId", Set.of("TID", "TRANSACTIONID")),

	AMOUNT("amount", Set.of("AMOUNT", "PRICE", "TOTAL")),
	TYPE("type", Set.of("TYPE", "EVENT")),
	URL("authUrl", Set.of("URL", "RETURNURL")),
	PAYMENT_METHOD("method", Set.of("PAYMENTMETHOD", "METHOD", "PM"));

	private final String standardKey;
	private final Set<String> legacyKeys;

	LegacyKey(String standardKey, Set<String> legacyKeys) {
		this.standardKey = standardKey;
		this.legacyKeys = legacyKeys;
	}

	public static String map(String legacyKey) {
		if (legacyKey == null)
			return null;
		for (LegacyKey key : values()) {
			if (key.legacyKeys.stream().anyMatch(k -> k.equalsIgnoreCase(legacyKey))) {
				return key.standardKey;
			}
		}
		return legacyKey; // 매핑 없으면 원래 키 반환
	}
}
