package undercooked.concurrency;

/**
 * Deadlock prevention utility implementing Strict Linear Resource Ordering
 * (CMPE 436 lecture3-semaphore-monitor.pdf p.31, 33; lecture4-1-deadlock.pdf p.9, 15).
 *
 * Any multi-resource transaction acquires locks strictly in ascending {@code resourceId}
 * order and releases them in reverse order, making Circular Wait structurally impossible.
 */
public final class ResourceLocker {

    public interface LockableResource {
        int getResourceId();
        Lock getLock();
    }

    public interface Action<T> {
        T execute();
    }

    private ResourceLocker() {
    }

    public static void withLock(int pid, LockableResource resource, Runnable action) {
        resource.getLock().requestCS(pid);
        try {
            action.run();
        } finally {
            resource.getLock().releaseCS(pid);
        }
    }

    public static void withOrderedLocks(int pid, LockableResource a, LockableResource b, Runnable action) {
        callWithOrderedLocks(pid, a, b, () -> {
            action.run();
            return null;
        });
    }

    public static <T> T callWithOrderedLocks(int pid, LockableResource a, LockableResource b, Action<T> action) {
        if (a.getResourceId() == b.getResourceId()) {
            a.getLock().requestCS(pid);
            try {
                return action.execute();
            } finally {
                a.getLock().releaseCS(pid);
            }
        }

        LockableResource first = (a.getResourceId() < b.getResourceId()) ? a : b;
        LockableResource second = (a.getResourceId() < b.getResourceId()) ? b : a;

        first.getLock().requestCS(pid);
        try {
            second.getLock().requestCS(pid);
            try {
                return action.execute();
            } finally {
                second.getLock().releaseCS(pid);
            }
        } finally {
            first.getLock().releaseCS(pid);
        }
    }
}
