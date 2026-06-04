package pers.XiaoShadiao.skydiao.utils.blivesensitiveword;

import org.apache.logging.log4j.util.Lazy;

import java.io.InputStream;

public interface IDetectorAccessor {

    public final static Lazy<IDetectorAccessor> lazyInstance = Lazy.lazy(() -> {
//        Detector instance = Detector.instance;
        IDetectorAccessor instance;
        try {
            instance = (IDetectorAccessor) Class.forName("pers.XiaoShadiao.skydiao.utils.blivesensitiveword.Detector").newInstance();
        } catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
        instance.loadWords(IDetectorAccessor.class.getClassLoader().getResourceAsStream("assets/skydiao/illegalwords.txt"));
        return instance;
    });

    public static IDetectorAccessor getInstance() {
        return lazyInstance.get();
    }

    public void loadWords(InputStream inputStream);

    public IllegalWordResult scanIllegalWords(String stringIn, boolean scanEnglish, boolean scanChinese);

}
