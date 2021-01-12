package net.pedroloureiro.mvflow.samples.android

import net.pedroloureiro.mvflow.EffectSender

/**
 * EffectSender for testing - stores a list of all effects that were sent.
 *
 * If you like mockito, you could also use mockito to implement the same interface and check the invocations the mock
 * received
 */
class CollectingEffectSender<T> : EffectSender<T> {
    private val _effectsSeen = mutableListOf<T>()
    val effectsSeen: List<T> = _effectsSeen

    override suspend fun send(effect: T) {
        _effectsSeen.add(effect)
    }

    override fun offer(effect: T): Boolean {
        _effectsSeen.add(effect)
        return true
    }
}
