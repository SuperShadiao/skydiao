package pers.XiaoShadiao.skydiao.utils;

public enum DeployableType {
    FLUX,
    LANTERN,
    UMBERELLA,
    BLACK_HOLE,
    ;

    @Override
    public String toString() {
        return switch (this) {
            case FLUX -> "Power Orb §7(§d§lPlasmaflux§7)";
            case LANTERN -> "Lantern §7(§5Glacite Lantern§7)";
            case UMBERELLA -> "§9Umberella";
            case BLACK_HOLE -> "§5Black Hole";
        };
    }
}
