package taylor.lib.framesurfaceview;

import android.os.SystemClock;
import android.util.Log;

public class MethodUtil {

    /**
     * calculate the time consumed by runnable invocation, print log in millisecond
     *
     * @param runnable
     */
    public static long time(Runnable runnable) {
        long start = SystemClock.elapsedRealtime();
        runnable.run();
        long end = SystemClock.elapsedRealtime();
        long span = end - start;
        Log.v("ttaylor", "MethodUtil.time()" + " time span = " + span + " ms");
        return span;
    }

}
