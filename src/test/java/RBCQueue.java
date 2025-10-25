import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.LinkedHashMap;
import java.util.Map;

public class RBCQueue {
    private static final Logger LOG = LogManager.getLogger();
    private Object lockme = new Object();
    volatile boolean stopped;

    interface Processor<K, V> {
        void process(K k, V v);
    }

    class DataHolder {
        long k;
        long v;
        long createdAt;
        long readby;

        public DataHolder(long k, long v, long readby) {
            this.k = k;
            this.createdAt = System.currentTimeMillis();
            this.readby = readby + this.createdAt;
            this.v = v;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("DataHolder{");
            sb.append("k=").append(k);
            sb.append(", v=").append(v);
            sb.append(", createdAt=").append(createdAt);
            sb.append(", readby=").append(readby);
            sb.append('}');
            return sb.toString();
        }
    }

    private LinkedHashMap<Long, DataHolder> linkedHashMap = new LinkedHashMap<>();

    public void publish() {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                while (!stopped) {
                    Map.Entry<Long, DataHolder> stringDataHolderEntry;
                    int size = linkedHashMap.size();
                    if (size < 1) {
                        continue;
                    }
                    stringDataHolderEntry = linkedHashMap.firstEntry();
                    while (stringDataHolderEntry != null) {
                        long lnow = System.currentTimeMillis();
                        if (lnow > stringDataHolderEntry.getValue().readby) {
                            synchronized (lockme) {
                                linkedHashMap.pollFirstEntry();
                            }
                            LOG.debug("now is {} {}", lnow, stringDataHolderEntry);
                            stringDataHolderEntry = linkedHashMap.lastEntry();

                        } else {
                            break;
                        }
                    }
                    Thread.yield();
                }
            }
        });
        t.setDaemon(true);
        t.start();
    }

    public void offer(String k, String v) {
        offer(Long.parseLong(k), Long.parseLong(v));
    }

    public void offer(String k, String v, long delay) {
        offer(Long.parseLong(k), Long.parseLong(v), delay);
    }

    public void offer(long k, long v) {
        offer(k, v, v);
    }

    public void offer(long k, long v, long delay) {
        DataHolder dataHolder = new DataHolder(k, v, 300);
        synchronized (lockme) {
            linkedHashMap.putLast(k, dataHolder);
        }
        LOG.debug("added {}", dataHolder);
    }

    public static void main(String[] args) throws InterruptedException {
        RBCQueue rbcQueue = new RBCQueue();
        rbcQueue.publish();
        rbcQueue.offer(1, 500);
        rbcQueue.offer("2", "2000", 2000);
        rbcQueue.offer(3, 2500);

        Thread.sleep(1000);
        rbcQueue.stopped = true;
    }
}
