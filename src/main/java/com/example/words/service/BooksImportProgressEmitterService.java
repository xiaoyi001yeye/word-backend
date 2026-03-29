package com.example.words.service;

import com.example.words.dto.BooksImportJobResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class BooksImportProgressEmitterService {

    private static final Logger log = LoggerFactory.getLogger(BooksImportProgressEmitterService.class);
    private static final long SSE_TIMEOUT_MS = 0L;

    private final Map<String, List<SseEmitter>> emittersByJobId = new ConcurrentHashMap<>();

    public SseEmitter subscribe(String jobId, BooksImportJobResponse snapshot, boolean completeImmediately) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        emittersByJobId.computeIfAbsent(jobId, ignored -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> removeEmitter(jobId, emitter));
        emitter.onTimeout(() -> removeEmitter(jobId, emitter));
        emitter.onError(ex -> removeEmitter(jobId, emitter));

        try {
            emitter.send(snapshot);
            if (completeImmediately) {
                emitter.complete();
            }
        } catch (IOException ex) {
            log.debug("Failed to send initial books import event for job {}", jobId, ex);
            removeEmitter(jobId, emitter);
            emitter.completeWithError(ex);
        }

        return emitter;
    }

    public void publish(BooksImportJobResponse snapshot, boolean complete) {
        List<SseEmitter> emitters = emittersByJobId.get(snapshot.getJobId());
        if (emitters == null || emitters.isEmpty()) {
            return;
        }

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(snapshot);
                if (complete) {
                    emitter.complete();
                }
            } catch (IOException ex) {
                log.debug("Failed to push books import event for job {}", snapshot.getJobId(), ex);
                removeEmitter(snapshot.getJobId(), emitter);
                emitter.completeWithError(ex);
            }
        }

        if (complete) {
            emittersByJobId.remove(snapshot.getJobId());
        }
    }

    private void removeEmitter(String jobId, SseEmitter emitter) {
        List<SseEmitter> emitters = emittersByJobId.get(jobId);
        if (emitters == null) {
            return;
        }
        emitters.remove(emitter);
        if (emitters.isEmpty()) {
            emittersByJobId.remove(jobId);
        }
    }
}
