package com.keran.kac.data;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.logging.Logger;

/** Owns disk writes; immutable snapshots are produced on the server thread. */
public final class RecordWriter implements AutoCloseable {
    private final Path target;
    private final Logger logger;
    // Complete snapshots supersede queued older snapshots, keeping memory bounded.
    private final ExecutorService executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(1), r -> {
                Thread t = new Thread(r, "KAC-RecordWriter"); t.setDaemon(true); return t;
            }, new ThreadPoolExecutor.DiscardOldestPolicy());

    public RecordWriter(Path target, Logger logger) { this.target = target; this.logger = logger; }

    public void submit(String snapshot) {
        executor.execute(() -> {
            Path tmp = null;
            try {
                Files.createDirectories(target.getParent());
                tmp = Files.createTempFile(target.getParent(), "vl-", ".tmp");
                Files.writeString(tmp, snapshot, StandardCharsets.UTF_8);
                try { Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
                catch (AtomicMoveNotSupportedException e) { Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING); }
            } catch (Exception e) { logger.warning("保存违规记录失败: " + e.getMessage()); }
            finally { if (tmp != null) try { Files.deleteIfExists(tmp); } catch (Exception ignored) {} }
        });
    }

    public void close() {
        executor.shutdown();
        try { if (!executor.awaitTermination(5, TimeUnit.SECONDS)) logger.warning("记录写入仍在完成，请勿立即终止进程"); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
