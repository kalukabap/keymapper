package com.example.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StateTransitionsTest {
    @Test
    fun `startup flow reaches ready and accepts input`() {
        assertTrue(StateTransitions.canTransition(EngineState.IDLE, EngineState.PROFILE_LOADING))
        assertTrue(StateTransitions.canTransition(EngineState.PROFILE_LOADING, EngineState.READY))
        assertTrue(StateTransitions.canAcceptInput(EngineState.READY))
    }

    @Test
    fun `non interactive states reject input`() {
        assertFalse(StateTransitions.canAcceptInput(EngineState.IDLE))
        assertFalse(StateTransitions.canAcceptInput(EngineState.PROFILE_LOADING))
        assertFalse(StateTransitions.canAcceptInput(EngineState.PERMISSION_MISSING))
    }
}
