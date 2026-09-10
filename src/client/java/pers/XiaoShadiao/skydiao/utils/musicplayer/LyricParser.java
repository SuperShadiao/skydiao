package pers.XiaoShadiao.skydiao.utils.musicplayer;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LyricParser {

    public final File lyric;

    public final StringLyric[] orderedString;
    
    private StringLyric[] lyricStrings;
    private double[] lyricTimes;
    
    public int lyricLocIndex = 0;

    public LyricParser(File lyric) {
        this.lyric = lyric;
        orderedString = parse();
    }

    private StringLyric[] parse() {

        List<StringLyric> lyricList = new ArrayList<>();
        try {
            String[] lines = FileUtils.readFileToString(lyric, "UTF-8").replace("\r", "").split("\n");

            lyricStrings = new StringLyric[lines.length + 1];
            lyricTimes = new double[lines.length + 1];
            int i = 0;
            double lasttime = 0;
            for(String line : lines) {
                Matcher matcher = Pattern.compile("\\[([0-9\\.:]+)\\](.*)").matcher(line);
                double time;
                if(matcher.find()) {
                    String[] numbers = matcher.group(1).split(":");
                    try {
                        time = Double.parseDouble(numbers[numbers.length - 1]) + Double.parseDouble(numbers[numbers.length - 2]) * 60;
                        // double deltatime = time - lasttime - 0.5;
                        // if(deltatime < 0) time -= deltatime;
                    } catch(Exception e) {
                        continue;
                    }
                    String lyricString = matcher.group(2).trim().replace(" ", " ");
                    if(lyricString.isEmpty()) continue;

                    lyricStrings[i] = new StringLyric(lyricString);
                    lasttime = lyricTimes[i] = time;
                    lyricList.add(new StringLyric(line));
                    i++;
                }
                lyricStrings[i] = null;
                lyricTimes[i] = Double.MAX_VALUE;
            }
            
            return lyricList.toArray(new StringLyric[0]);
        } catch (IOException e) {
            // TODO 自动生成的 catch 块
            e.printStackTrace();
            lyricStrings = new StringLyric[0];
            return new StringLyric[0];
        }
    }

    public StringLyric getCurrentLyric(double musicLoc, int offerset) {
        for(int i = lyricLocIndex;i < lyricStrings.length;i++) {
            /*
                if(orderedString[i].contains(temp1 + ":" + temp3) && orderedString[i].contains(nearestString)) {
                    try {
                        if(i + offerset < orderedString.length) {
                            return orderedString[i + offerset].substring(orderedString[i + offerset].indexOf("]") + 1);
                        } else return null;
                    } catch(Exception e) {
                        return null;
                    }
                }
             */

            double temp = musicLoc - lyricTimes[i];
            if(temp <= 0) {
                lyricLocIndex = i;
                int jj = i - 1 + offerset;
                if(jj < 0 || jj >= lyricStrings.length) return null;
                return lyricStrings[jj];
            }
            
        }
        //}

        return null;
    }

    public void setLoc(int a) {
        lyricLocIndex = a;
    }
    
}


