package taylor.lib.framesurfaceview;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class LinkedBlockingQueue {
    /**
     * Current number of elements
     */
    private final AtomicInteger count = new AtomicInteger();
    /**
     * Lock held by take, poll, etc
     */
    private final ReentrantLock takeLock = new ReentrantLock();

    /**
     * Wait queue for waiting takes
     */
    private final Condition notEmpty = takeLock.newCondition();

    /**
     * Lock held by put, offer, etc
     */
    private final ReentrantLock putLock = new ReentrantLock();

    /**
     * Wait queue for waiting puts
     */
    private final Condition notFull = putLock.newCondition();
    /**
     * The capacity bound, or Integer.MAX_VALUE if none
     */
    private final int capacity;
    /**
     * the first element in the queue
     */
    private LinkedBitmap head;
    /**
     * the last element int the queue
     */
    private LinkedBitmap tail;


    public LinkedBlockingQueue(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException();
        this.capacity = capacity;
    }

    public void put(LinkedBitmap bitmap) throws InterruptedException {
        if (bitmap == null) throw new NullPointerException();
        // Note: convention in all put/take/etc is to preset local var
        // holding count negative to indicate failure unless set.
        int c = -1;
        final ReentrantLock putLock = this.putLock;
        final AtomicInteger count = this.count;
        putLock.lockInterruptibly();
        try {
            /*
             * Note that count is used in wait guard even though it is
             * not protected by lock. This works because count can
             * only decrease at this point (all other puts are shut
             * out by lock), and we (or some other waiting put) are
             * signalled if it ever changes from capacity. Similarly
             * for all other uses of count in other wait guards.
             */
            while (count.get() == capacity) {
                notFull.await();
            }
            enqueue(bitmap);
            c = count.getAndIncrement();
            if (c + 1 < capacity)
                notFull.signal();
        } finally {
            putLock.unlock();
        }
        if (c == 0)
            signalNotEmpty();
    }

    public boolean offer(LinkedBitmap bitmap) {
        if (bitmap == null) throw new NullPointerException();
        final AtomicInteger count = this.count;
        if (count.get() == capacity)
            return false;
        int c = -1;
        final ReentrantLock putLock = this.putLock;
        putLock.lock();
        try {
            if (count.get() < capacity) {
                enqueue(bitmap);
                c = count.getAndIncrement();
                if (c + 1 < capacity)
                    notFull.signal();
            }
        } finally {
            putLock.unlock();
        }
        if (c == 0)
            signalNotEmpty();
        return c >= 0;
    }

    public LinkedBitmap take() throws InterruptedException {
        LinkedBitmap x;
        int c = -1;
        final AtomicInteger count = this.count;
        final ReentrantLock takeLock = this.takeLock;
        takeLock.lockInterruptibly();
        try {
            while (count.get() == 0) {
                notEmpty.await();
            }
            x = dequeue();
            c = count.getAndDecrement();
            if (c > 1)
                notEmpty.signal();
        } finally {
            takeLock.unlock();
        }
        if (c == capacity)
            signalNotFull();
        return x;
    }

    /**
     * insert element into the end of queue
     *
     * @param bitmap
     */
    private void enqueue(LinkedBitmap bitmap) {
        if (head == null) {
            head = bitmap;
            tail = bitmap;
            bitmap.next = null;
        } else {
            tail.next = bitmap;
            bitmap.next = null;
        }
    }

    /**
     * get and remove the first element of the queue
     *
     * @return
     */
    private LinkedBitmap dequeue() {
        LinkedBitmap p = head;
        if (p == null) {
            return null;
        } else {
            head = head.next;
        }
        return p;
    }

    /**
     * Signals a waiting take. Called only from put/offer (which do not
     * otherwise ordinarily lock takeLock.)
     */
    private void signalNotEmpty() {
        final ReentrantLock takeLock = this.takeLock;
        takeLock.lock();
        try {
            notEmpty.signal();
        } finally {
            takeLock.unlock();
        }
    }

    /**
     * Signals a waiting put. Called only from take/poll.
     */
    private void signalNotFull() {
        final ReentrantLock putLock = this.putLock;
        putLock.lock();
        try {
            notFull.signal();
        } finally {
            putLock.unlock();
        }
    }

    /**
     * recycle the bitmaps one by one
     */
    public void clear() {
        LinkedBitmap p = head;
        if (p == null) {
            return;
        }
        while (p != null) {
            if (p.bitmap != null) {
                p.bitmap.recycle();
            }
            p.bitmap = null;
            p = p.next;
        }
    }
}
