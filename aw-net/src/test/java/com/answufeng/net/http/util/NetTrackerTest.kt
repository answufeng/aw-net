package com.answufeng.net.http.util

import com.answufeng.net.http.annotations.NetTracker
import com.answufeng.net.http.model.NetEvent
import com.answufeng.net.http.model.NetEventStage
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * NetTracker 全局分发器的单元测试。
 */
class NetTrackerTest {

    private fun createEvent(name: String = "test"): NetEvent {
        return NetEvent(name = name, stage = NetEventStage.START, timestampMs = System.currentTimeMillis())
    }

    @Before
    fun setUp() {
        NetTracker.delegate = null
    }

    @After
    fun tearDown() {
        NetTracker.delegate = null
    }

    @Test
    fun `track does nothing when delegate is null`() {
        // 不应抛异常
        NetTracker.track(createEvent())
    }

    @Test
    fun `track dispatches event to delegate`() {
        val events = mutableListOf<NetEvent>()
        NetTracker.delegate = object : NetTracker {
            override fun onEvent(event: NetEvent) {
                events.add(event)
            }
        }

        val event = createEvent("getUser")
        NetTracker.track(event)

        assertEquals(1, events.size)
        assertSame(event, events[0])
    }

    @Test
    fun `replacing delegate stops old delegate from receiving events`() {
        val eventsOld = mutableListOf<NetEvent>()
        val eventsNew = mutableListOf<NetEvent>()

        NetTracker.delegate = object : NetTracker {
            override fun onEvent(event: NetEvent) { eventsOld.add(event) }
        }
        NetTracker.track(createEvent("a"))

        NetTracker.delegate = object : NetTracker {
            override fun onEvent(event: NetEvent) { eventsNew.add(event) }
        }
        NetTracker.track(createEvent("b"))

        assertEquals(1, eventsOld.size)
        assertEquals(1, eventsNew.size)
    }

    @Test
    fun `concurrent track calls are safe`() {
        val events = CopyOnWriteArrayList<NetEvent>()
        NetTracker.delegate = object : NetTracker {
            override fun onEvent(event: NetEvent) { events.add(event) }
        }

        val threadCount = 10
        val latch = CountDownLatch(threadCount)
        val threads = (0 until threadCount).map { i ->
            Thread {
                NetTracker.track(createEvent("req-$i"))
                latch.countDown()
            }
        }
        threads.forEach { it.start() }
        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertEquals(threadCount, events.size)
    }
}
