package com.orchords.orchordsai.service.assistant

import android.content.Context
import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService

/** Always-running platform boundary. Keep this class intentionally dependency-free and inert. */
class OrchordsVoiceInteractionService : VoiceInteractionService() {
    override fun onReady() {
        super.onReady()
        setDisabledShowContext(
            VoiceInteractionSession.SHOW_WITH_ASSIST or VoiceInteractionSession.SHOW_WITH_SCREENSHOT
        )
    }
}

/** Heavy interaction work belongs here (and later #105/#79 handoff), not in the top-level service. */
class OrchordsVoiceInteractionSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession =
        OrchordsVoiceInteractionSession(this)
}

private class OrchordsVoiceInteractionSession(
    context: Context,
) : VoiceInteractionSession(context)
