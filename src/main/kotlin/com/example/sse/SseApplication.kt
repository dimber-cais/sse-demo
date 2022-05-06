package com.example.sse

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.atomic.AtomicInteger

@SpringBootApplication
class SseApplication

fun main(args: Array<String>) {
    runApplication<SseApplication>(*args)
}

private val progressTrackers: ConcurrentMap<String, TrackingJob> = ConcurrentHashMap()

fun cancelTracking(eventStreamId: String) {
    println("Closing")
    progressTrackers[eventStreamId]?.let {
        runCatching { it.emitter.complete() }
        runCatching { it.job.cancel() }
        progressTrackers - eventStreamId
    }
}

private val counter = AtomicInteger(1)

data class Counter(val count: Int)

data class TrackingJob(val emitter: SseEmitter, val job: Job)

@RestController
class Controller {

    private val scope = CoroutineScope(IO)

    @GetMapping("/sse")
    fun trackProgress(): SseEmitter {

        val eventStreamId = UUID.randomUUID().toString()
        val emitter = SseEmitter(-1).apply {
            onCompletion { cancelTracking(eventStreamId) }
        }

        val job: Job = scope.launch(IO) {
            try {
                while (true) {
                    delay(1000)
                    println("Emitting: ${counter.get()}")
                    if (counter.getAndIncrement() % 5 == 0) throw IllegalStateException()
                    emitter.send(Counter(counter.get()))
                }
            } catch (e: Exception) {
                emitter.completeWithError(e)
            }
        }

        progressTrackers[eventStreamId] = TrackingJob(emitter, job)

        return emitter
    }
}
