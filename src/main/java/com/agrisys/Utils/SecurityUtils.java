package com.agrisys.Utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class SecurityUtils {

    /**
     * Tager en klartekst-streng og returnerer en SHA-256 hex-streng,
     * som matcher præcis det format, SQL Servers HASHBYTES spytter ud.
     */
    public static String hashPassword(String password) {
        if (password == null) return "";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(password.getBytes(StandardCharsets.UTF_8));

            // Byg hex-strengen med det præfix "0x" som MSSQL sys.fn_varbintohexstr bruger
            StringBuilder hexString = new StringBuilder("0x");
            for (byte b : encodedhash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();

        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Kritisk fejl: SHA-256 algoritmen blev ikke fundet i Java.", e);
        }
    }
}