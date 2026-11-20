package undercooked.concurrency;

/**
 * Thread-safe unbounded FIFO queue built as a Java Monitor
 * directly matching CMPE 436 lecture3-semaphore-monitor.pdf p.61.
 */
public class ListQueue<T> {

    private static final class Node<T> {
        T data;
        Node<T> next;
    }

    private Node<T> head = null;
    private Node<T> tail = null;
    private int size = 0;

    public synchronized void enqueue(T data) {
        Node<T> temp = new Node<>();
        temp.data = data;
        temp.next = null;
        if (tail == null) {
            tail = temp;
            head = tail;
        } else {
            tail.next = temp;
            tail = temp;
        }
        size++;
        notifyAll();
    }

    public synchronized T dequeue() {
        while (head == null) {
            Util.myWait(this);
        }
        T returnVal = head.data;
        if (tail == head) {
            tail = null;
        }
        head = head.next;
        size--;
        return returnVal;
    }

    public synchronized T dequeueInterruptible() throws InterruptedException {
        while (head == null) {
            wait();
        }
        T returnVal = head.data;
        if (tail == head) {
            tail = null;
        }
        head = head.next;
        size--;
        return returnVal;
    }

    public synchronized boolean isEmpty() {
        return head == null;
    }

    public synchronized int size() {
        return size;
    }
}
