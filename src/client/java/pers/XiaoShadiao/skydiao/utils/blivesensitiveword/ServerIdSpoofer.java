package pers.XiaoShadiao.skydiao.utils.blivesensitiveword;

import org.apache.commons.lang3.RandomStringUtils;
import pers.XiaoShadiao.skydiao.utils.ToolList;

public class ServerIdSpoofer {

    private static String cachedMegaServerId;
    private static String cachedMiniServerId;
    private static String cachedSmallMegaServerId;
    private static String cachedSmallMiniServerId;

    public static void generateServerIds() {
        generateMegaServerId();
        generateMiniServerId();
    }

    private static void generateMegaServerId() {
        int numberPart = ToolList.getInstance().random.nextInt(20) + 1;
        String letterPart = RandomStringUtils.randomAlphabetic(1).toUpperCase();

        cachedMegaServerId = "mega" + numberPart + letterPart;
        cachedSmallMegaServerId = "M" + numberPart + letterPart;
    }

    private static void generateMiniServerId() {
        int numberPart = ToolList.getInstance().random.nextInt(189) + 10;
        String letterPart = RandomStringUtils.randomAlphabetic(1).toUpperCase();

        cachedMiniServerId = "mini" + numberPart + letterPart;
        cachedSmallMiniServerId = "m" + numberPart + letterPart;
    }

    public static String getMegaServerId() {
        return cachedMegaServerId;
    }

    public static String getMiniServerId() {
        return cachedMiniServerId;
    }

    public static String getSmallMegaServerId() {
        return cachedSmallMegaServerId;
    }

    public static String getSmallMiniServerId() {
        return cachedSmallMiniServerId;
    }

}
