package pers.XiaoShadiao.skydiao.utils;

import javax.net.ssl.*;
import java.security.cert.X509Certificate;

public class HttpSSLDisabler {

    private static SSLSocketFactory defaultSSLSocketFactory = HttpsURLConnection.getDefaultSSLSocketFactory();
    private static SSLSocketFactory trustAll = HttpsURLConnection.getDefaultSSLSocketFactory();
    private static boolean trustAllInited = false;

    private static void initTrustAll() {
        if(true) return; // 新版Java应该是没有证书问题了, 不再禁用证书
        try {
            defaultSSLSocketFactory = HttpsURLConnection.getDefaultSSLSocketFactory();
            TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        public void checkClientTrusted(X509Certificate[] chain, String authType) {}
                        public void checkServerTrusted(X509Certificate[] chain, String authType) {}
                        public X509Certificate[] getAcceptedIssuers() { return null; }
                    }
            };

            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            // HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            trustAll = sc.getSocketFactory();
            HttpsURLConnection.setDefaultSSLSocketFactory(trustAll);
        } catch (Exception e) {
            e.printStackTrace();
            trustAllInited = false;
        }
    }

    public static SSLSocketFactory getDefault() {
        return defaultSSLSocketFactory;
    }

    public static SSLSocketFactory getTrustAll() {
        if (!trustAllInited) {
            trustAllInited = true;
            initTrustAll();
        }
        HttpsURLConnection.setDefaultSSLSocketFactory(trustAll);
        return trustAll;
    }

}
