package com.mylittleblover.mkwplayer.player

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.*

class PlaybackStartupGateTest {
    @Test fun heavyLoadingYieldsUntilTheRequestedServiceStartIsAcknowledged()=runBlocking {
        val gate=PlaybackStartupGate();val request=gate.requested()
        var loaded=false
        val load=launch(start=CoroutineStart.UNDISPATCHED) { gate.awaitPromotion();loaded=true }
        yield();assertFalse(loaded);gate.promoted(request);load.join();assertTrue(loaded)
    }
    @Test fun anOlderServiceCallbackCannotReleaseAHeavyLoadWaitingForANewerStart()=runBlocking {
        val gate=PlaybackStartupGate();val old=gate.requested();val latest=gate.requested()
        var loaded=false
        val load=launch(start=CoroutineStart.UNDISPATCHED) { gate.awaitPromotion();loaded=true }
        gate.promoted(old);yield();assertFalse(loaded)
        gate.promoted(latest);load.join();assertTrue(loaded)
        gate.promoted(old) // A late callback must never roll the acknowledgment backwards.
        gate.awaitPromotion()
    }
    @Test fun anAlreadyRunningServiceStillAcknowledgesEachNewStartBeforeHeavyLoading()=runBlocking {
        val gate=PlaybackStartupGate();gate.promoted(gate.requested());val next=gate.requested()
        var loaded=false
        val load=launch(start=CoroutineStart.UNDISPATCHED) { gate.awaitPromotion();loaded=true }
        yield();assertFalse(loaded);gate.promoted(next);load.join();assertTrue(loaded)
    }
}
