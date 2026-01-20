package com.scms.util;

import org.mindrot.jbcrypt.BCrypt;

public class PasswordUtil {
    // NOTE: workFactor of 12 is secure enough for this prototype
    private static final int WORK_FACTOR = 12;

    public static String hashPassword(String plain) {
        return BCrypt.hashpw(plain, BCrypt.gensalt(WORK_FACTOR));
    }

    public static boolean checkPassword(String plain, String hashed) {
        return BCrypt.checkpw(plain, hashed);
    }
}
