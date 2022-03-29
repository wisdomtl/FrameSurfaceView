package taylor.lib.framesurfaceview;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.AttributeSet;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * a SurfaceView which draws bitmaps one after another like frame animation
 */
public class FrameSurfaceView extends BaseSurfaceView {
    public static final int INVALID_INDEX = Integer.MAX_VALUE;
    private int bufferSize = 3;
    public static final String DECODE_THREAD_NAME = "DecodingThread";
    public static final int INFINITE = -1;
    //-1 means repeat infinitely
    private int repeatTimes;
    private int repeatedCount;

    /**
     * the resources of frame animation
     */
    private List<Integer> bitmapIds = new ArrayList<>();
    /**
     * the index of bitmap resource which is decoding
     */
    private int bitmapIdIndex;
    /**
     * the index of frame which is drawing
     */
    private volatile int frameIndex = INVALID_INDEX;
    /**
     * decoded bitmaps stores in this queue
     * consumer is drawing thread, producer is decoding thread.
     */
    private LinkedBlockingQueue decodedBitmaps = new LinkedBlockingQueue(bufferSize);
    /**
     * bitmaps already drawn by canvas stores in this queue
     * consumer is decoding thread, producer is drawing thread.
     */
    private LinkedBlockingQueue drawnBitmaps = new LinkedBlockingQueue(bufferSize);
    /**
     * the thread for decoding bitmaps
     */
    private HandlerThread decodeThread;
    /**
     * the Runnable describes how to decode one bitmap
     */
    private DecodeRunnable decodeRunnable;
    /**
     * this handler helps to decode bitmap one after another
     */
    private Handler handler;
    private BitmapFactory.Options options;
    private Paint paint = new Paint();
    private Rect srcRect;
    private Rect dstRect = new Rect();
    private int defaultWidth;
    private int defaultHeight;

    public FrameSurfaceView(Context context) {
        super(context);
    }

    public FrameSurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public FrameSurfaceView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setRepeatTimes(int repeatTimes) {
        this.repeatTimes = repeatTimes;
    }

    @Override
    protected void init() {
        super.init();
        options = new BitmapFactory.Options();
        options.inMutable = true;
        decodeThread = new HandlerThread(DECODE_THREAD_NAME);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        dstRect.set(0, 0, getWidth(), getHeight());
    }

    @Override
    protected int getDefaultWidth() {
        return defaultWidth;
    }

    @Override
    protected int getDefaultHeight() {
        return defaultHeight;
    }

    @Override
    protected void onFrameDrawFinish() {
    }

    /**
     * set the duration of frame animation
     *
     * @param duration time in milliseconds
     */
    public void setDuration(int duration) {
        int frameDuration = duration / bitmapIds.size();
        setFrameDuration(frameDuration);
    }

    /**
     * set the materials of frame animation which is an array of bitmap resource id
     *
     * @param bitmapIds an array of bitmap resource id
     */
    public void setBitmapIds(List<Integer> bitmapIds) {
        if (bitmapIds == null || bitmapIds.size() == 0) {
            return;
        }
        this.bitmapIds = bitmapIds;
        //by default, take the first bitmap's dimension into consideration
        getBitmapDimension(bitmapIds.get(bitmapIdIndex));
        preloadFrames();
        decodeRunnable = new DecodeRunnable(bitmapIdIndex, bitmapIds, options);
    }

    private void getBitmapDimension(int bitmapId) {
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(this.getResources(), bitmapId, options);
        defaultWidth = options.outWidth;
        defaultHeight = options.outHeight;
        srcRect = new Rect(0, 0, defaultWidth, defaultHeight);
        //we have to re-measure to make defaultWidth in use in onMeasure()
        requestLayout();
    }

    /**
     * load the first several frames of animation before it is started
     */
    private void preloadFrames() {
        decodeAndPutBitmap(bitmapIds.get(bitmapIdIndex++), options, new LinkedBitmap());
        decodeAndPutBitmap(bitmapIds.get(bitmapIdIndex++), options, new LinkedBitmap());
    }

    /**
     * recycle the bitmap used by frame animation.
     * Usually it should be invoked when the ui of frame animation is no longer visible
     */
    public void destroy() {
        if (drawnBitmaps != null) {
            drawnBitmaps.clear();
        }
        if (decodeThread != null) {
            decodeThread.quit();
            decodeThread = null;
        }
        if (handler != null) {
            handler = null;
        }
    }

    @Override
    protected void onFrameDraw(Canvas canvas) {
        clearCanvas(canvas);
        if (!isStart()) {
            return;
        }
        if (!isFinish()) {
            drawOneFrame(canvas);
        } else {
            onFrameAnimationEnd();
            if (repeatTimes != 0 && repeatTimes == INFINITE) {
                start();
            } else if (repeatedCount < repeatTimes) {
                start();
                repeatedCount++;
            } else {
                repeatedCount = 0;
            }
        }
    }

    /**
     * draw a single frame which is a bitmap
     *
     * @param canvas
     */
    private void drawOneFrame(Canvas canvas) {
        LinkedBitmap linkedBitmap = getDecodedBitmap();
        if (linkedBitmap != null) {
            canvas.drawBitmap(linkedBitmap.bitmap, srcRect, dstRect, paint);
        }
        putDrawnBitmap(linkedBitmap);
        frameIndex++;
    }

    /**
     * invoked when frame animation is done
     */
    private void onFrameAnimationEnd() {
        reset();
    }

    /**
     * reset the index of frame, preparing for the next frame animation
     */
    private void reset() {
        frameIndex = INVALID_INDEX;
    }

    /**
     * whether frame animation is finished
     *
     * @return true: animation is finished, false: animation is doing
     */
    private boolean isFinish() {
        return frameIndex >= bitmapIds.size() - 1;
    }

    /**
     * whether frame animation is started
     *
     * @return true: animation is started, false: animation is not started
     */
    private boolean isStart() {
        return frameIndex != INVALID_INDEX;
    }

    /**
     * start frame animation from the first frame
     */
    public void start() {
        frameIndex = 0;
        if (decodeThread == null) {
            decodeThread = new HandlerThread(DECODE_THREAD_NAME);
        }
        if (!decodeThread.isAlive()) {
            decodeThread.start();
        }
        if (handler == null) {
            handler = new Handler(decodeThread.getLooper());
        }
        if (decodeRunnable != null) {
            decodeRunnable.setIndex(0);
        }
        handler.post(decodeRunnable);
    }


    /**
     * clear out the drawing on canvas,preparing for the next frame
     * * @param canvas
     */
    private void clearCanvas(Canvas canvas) {
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        canvas.drawPaint(paint);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC));
    }

    /**
     * decode bitmap by BitmapFactory.decodeStream(), it is about twice faster than BitmapFactory.decodeResource()
     *
     * @param resId   the bitmap resource
     * @param options
     * @return
     */
    private Bitmap decodeBitmap(int resId, BitmapFactory.Options options) {
        options.inScaled = false;
        InputStream inputStream = getResources().openRawResource(resId);
        return BitmapFactory.decodeStream(inputStream, null, options);
    }

    /**
     * reuse bitmap in drawnBitmaps to decode new bitmap
     * @param resId
     * @param options
     */
    private void decodedBitmapByReuse(int resId, BitmapFactory.Options options) {
        LinkedBitmap linkedBitmap = getDrawnBitmap();
        if (linkedBitmap == null) {
            linkedBitmap = new LinkedBitmap();
        }
        options.inBitmap = linkedBitmap.bitmap;
        decodeAndPutBitmap(resId, options, linkedBitmap);
    }

    /**
     * decode bitmap and put it into decodedBitmaps
     * @param resId
     * @param options
     * @param linkedBitmap
     */
    private void decodeAndPutBitmap(int resId, BitmapFactory.Options options, LinkedBitmap linkedBitmap) {
        Bitmap bitmap = decodeBitmap(resId, options);
        linkedBitmap.bitmap = bitmap;
        try {
            decodedBitmaps.put(linkedBitmap);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void putDrawnBitmap(LinkedBitmap bitmap) {
        drawnBitmaps.offer(bitmap);
    }

    /**
     * get bitmap which already drawn by canvas
     *
     * @return
     */
    private LinkedBitmap getDrawnBitmap() {
        LinkedBitmap bitmap = null;
        try {
            bitmap = drawnBitmaps.take();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return bitmap;
    }

    /**
     * get decoded bitmap in the decoded bitmap queue
     * it might block due to new bitmap is not ready
     *
     * @return
     */
    private LinkedBitmap getDecodedBitmap() {
        LinkedBitmap bitmap = null;
        try {
            bitmap = decodedBitmaps.take();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return bitmap;
    }

    private class DecodeRunnable implements Runnable {

        private int index;
        private List<Integer> bitmapIds;
        private BitmapFactory.Options options;

        public DecodeRunnable(int index, List<Integer> bitmapIds, BitmapFactory.Options options) {
            this.index = index;
            this.bitmapIds = bitmapIds;
            this.options = options;
        }

        public void setIndex(int index) {
            this.index = index;
        }

        @Override
        public void run() {
            decodedBitmapByReuse(bitmapIds.get(index), options);
            index++;
            if (index < bitmapIds.size()) {
                handler.post(this);
            } else {
                index = 0;
            }
        }
    }
}
