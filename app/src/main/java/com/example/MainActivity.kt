package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.data.database.SanaDatabase
import com.example.data.database.SanaRepository
import com.example.session.SanaSessionManager
import com.example.ui.screens.SanaMainScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private lateinit var sessionManager: SanaSessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = SanaDatabase.getDatabase(applicationContext)
        val repository = SanaRepository(database)
        sessionManager = SanaSessionManager(applicationContext, repository)

        setContent {
            MyApplicationTheme {
                SanaMainScreen(sessionManager = sessionManager)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (::sessionManager.isInitialized) {
            sessionManager.onAppPaused()
        }
    }

    override fun onResume() {
        super.onResume()
        if (::sessionManager.isInitialized) {
            sessionManager.onAppResumed()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::sessionManager.isInitialized) {
            sessionManager.stopLiveConversation()
            sessionManager.audioManager.stopSpeaking()
            sessionManager.audioManager.stopListening()
        }
    }
}
