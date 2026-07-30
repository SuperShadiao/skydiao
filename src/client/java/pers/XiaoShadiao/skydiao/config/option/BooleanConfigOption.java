package pers.XiaoShadiao.skydiao.config.option;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static pers.XiaoShadiao.skydiao.utils.i18n.CrowdinI18nManager.translate;

public class BooleanConfigOption extends ConfigOption<Boolean> {

    private final List<BooleanConfigOption> dependsFeatures = new ArrayList<>();
    private List<BooleanConfigOption> immutableDependsFeatures = Collections.emptyList();
    private final List<BooleanConfigOption> usingThisFeatures = new ArrayList<>();
    private List<BooleanConfigOption> immutableUsingThisFeatures = Collections.emptyList();

    public BooleanConfigOption(String name, boolean defaultValue) {
        super(name, defaultValue);
    }

    @Override
    public String getI18nValue() {
        return translate(value ? "config.1" : "config.0");
    }

    public List<BooleanConfigOption> getDependsFeatures() {
        return immutableDependsFeatures;
    }

    public List<BooleanConfigOption> getUsingThisFeatures() {
        return immutableUsingThisFeatures;
    }

    public BooleanConfigOption addDependFeature(BooleanConfigOption feature) {
        dependsFeatures.add(feature);
        immutableDependsFeatures = Collections.unmodifiableList(dependsFeatures);
        feature.usingThisFeatures.add(this);
        feature.immutableUsingThisFeatures = Collections.unmodifiableList(feature.usingThisFeatures);
        return this;
    }

    public Boolean getValue() {
        if(isForceDisabled()) return false;
        return value;
    }

}
