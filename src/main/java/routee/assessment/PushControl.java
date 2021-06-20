package routee.assessment;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;

public class PushControl {
    private static final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(1);

    private final ScheduledFuture<?> pusherHandle;

    private final Runnable pusher;

    private AtomicInteger count = new AtomicInteger(10);

    /**
     * Invokes the callback function 10 times with a fixed 10 minutes interval
     * @param callback
     */
    public PushControl(ICallback callback) {
        pusher = new Runnable() {
            public void run() {
                int i = count.decrementAndGet();
                callback.call();
                if (i <= 0) {
                    // Submit a once-shot new task which uses the returned future to cancel the scheduled task
                    scheduler.schedule(new Runnable() {
                        @Override
                        public void run() {
                            pusherHandle.cancel(true);
                        }
                    }, 0, SECONDS);
                }
            }
        };

        pusherHandle =
                scheduler.scheduleAtFixedRate(pusher, 0, 10, MINUTES);

    }

}