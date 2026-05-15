package com.answufeng.net.websocket

import java.util.concurrent.LinkedBlockingQueue

internal class MessageQueueManager(
    private val config: WebSocketManager.Config,
    private val connectionId: String,
    private val wsLogger: DefaultWebSocketLogger,
) {
    private val messageQueue = LinkedBlockingQueue<WebSocketClientImpl.QueuedMessage>(config.messageQueueCapacity)

    sealed class EnqueueResult {
        data object Success : EnqueueResult()

        data object Dropped : EnqueueResult()

        data object DroppedOldest : EnqueueResult()

        data object QueueFull : EnqueueResult()
    }

    fun enqueue(message: WebSocketClientImpl.QueuedMessage): EnqueueResult {
        if (messageQueue.offer(message)) return EnqueueResult.Success
        if (config.dropOldestWhenQueueFull) {
            messageQueue.poll()
            return if (messageQueue.offer(message)) {
                EnqueueResult.DroppedOldest
            } else {
                wsLogger.w(connectionId, "消息队列已满，丢弃消息（已尝试丢弃最旧消息）")
                EnqueueResult.QueueFull
            }
        }
        wsLogger.w(connectionId, "消息队列已满，丢弃消息")
        return EnqueueResult.QueueFull
    }

    fun drainTo(target: MutableList<WebSocketClientImpl.QueuedMessage>): Int {
        return messageQueue.drainTo(target)
    }

    fun isEmpty(): Boolean = messageQueue.isEmpty()

    fun size(): Int = messageQueue.size

    fun clear() = messageQueue.clear()

    fun reoffer(messages: List<WebSocketClientImpl.QueuedMessage>) {
        messages.reversed().forEach { messageQueue.offer(it) }
    }
}
