package pers.XiaoShadiao.skydiao.utils.mircosoftaccount.hwid;

import oshi.SystemInfo;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Random;

public class HWIDGenerator_v4 {

    public static String cache;

    public static String generateHWID() {

        if(cache == null) {
            SystemInfo info = new SystemInfo();
            StringBuilder sb = new StringBuilder();
            info.getHardware().getDiskStores().forEach(disk -> sb.append(disk.getSerial()));
            sb.append(info.getHardware().getProcessor().getProcessorIdentifier().getProcessorID());
            sb.append(info.getHardware().getComputerSystem().getBaseboard().getSerialNumber());

            cache = hashString(sb.toString());
        }

        return cache;

    }

    private static String hashString(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = input.getBytes(StandardCharsets.UTF_8);
            Random r = new Random(input.hashCode());
            for (int i = 0; i < bytes.length; i++) {
                bytes[i] ^= (byte) (r.nextInt() % Byte.MAX_VALUE);
            }
            byte[] hashBytes = digest.digest(bytes);
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }

}
