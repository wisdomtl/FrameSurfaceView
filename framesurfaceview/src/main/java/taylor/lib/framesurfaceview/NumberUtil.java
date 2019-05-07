package taylor.lib.framesurfaceview;

import android.text.TextUtils;
import android.util.Log;

public class NumberUtil {

    private static long total;
    private static int times;

    private static String tag;

    /**
     * calculate the average of a series long number and print it
     * @param tag
     * @param l
     */
    public static void average(String tag, Long l) {
        if (!TextUtils.isEmpty(tag) && !tag.equals(NumberUtil.tag)) {
            reset();
            NumberUtil.tag = tag;
        }
        times++;
        total += l;
        Log.v("ttaylor", "Average.average() " + NumberUtil.tag + " average = " + (total / times));
    }

    private static void reset() {
        total = 0;
        times = 0;
    }
}
