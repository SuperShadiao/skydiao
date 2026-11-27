package pers.XiaoShadiao.skydiao.utils.tab;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record MiningTabData(MineshaftProgress mineshaft, List<String> pickaxeAbility) {

    public static final MiningTabData PREVIEW = new MiningTabData(
            new MineshaftProgress(1240, 2000), List.of("Mining Speed Boost: 42s"));

    private static final Pattern MINESHAFT = Pattern.compile("Glacite Mineshafts:\\s*([0-9][0-9,]{0,8})\\s*/\\s*([0-9][0-9,]{0,8})");
    private static final String PICKAXE_ABILITY = "Pickaxe Ability:";

    public static MiningTabData read(List<String> lines) {
        MineshaftProgress mineshaft = null;
        List<String> ability = new ArrayList<>();
        boolean inAbilityWidget = false;

        for (String line : lines) {
            String text = line.strip();
            Matcher progress = MINESHAFT.matcher(text);
            if (progress.matches()) {
                int current = Integer.parseInt(progress.group(1).replace(",", ""));
                int maximum = Integer.parseInt(progress.group(2).replace(",", ""));
                if (maximum > 0) mineshaft = new MineshaftProgress(Math.min(current, maximum), maximum);
            }
            if (text.startsWith(PICKAXE_ABILITY)) {
                inAbilityWidget = true;
                String status = text.substring(PICKAXE_ABILITY.length()).strip();
                if (!status.isEmpty()) ability.add(status);
            } else if (inAbilityWidget && !text.isEmpty() && Character.isWhitespace(line.charAt(0))) {
                ability.add(text);
            } else {
                inAbilityWidget = false;
            }
        }
        return new MiningTabData(mineshaft, List.copyOf(ability));
    }

    public record MineshaftProgress(int current, int maximum) {
        public int remaining() {
            return maximum - current;
        }

        public float fraction() {
            return (float) current / maximum;
        }
    }
}
