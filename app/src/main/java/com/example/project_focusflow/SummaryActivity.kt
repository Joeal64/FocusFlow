package com.example.project_focusflow

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.project_focusflow.ui.theme.ProjectFocusFlowTheme

// This Activity shows a summary after a focus session ends
class SummaryActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Read the number of minutes from the Intent sent by the timer screen
        val minutes = intent.getIntExtra("SESSION_MINUTES", 0)

        setContent {
            // State to control dark/light theme
            var darkTheme by remember { mutableStateOf(true) }

            // Wrap UI in app theme
            ProjectFocusFlowTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Show the summary screen and pass minutes and back action
                    SummaryScreen(
                        minutes = minutes,
                        onBackHome = { finish() }   // Close this activity and go back
                    )
                }
            }
        }
    }
}

@Composable
fun SummaryScreen(
    minutes: Int,          // Number of minutes user focused in this session
    onBackHome: () -> Unit // What to do when user taps Back button
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // App name at the top
        Text(
            text = stringResource(R.string.app_name),
            fontSize = 28.sp,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Summary message
        Text(
            text = stringResource(R.string.summary_message, minutes),
            fontSize = 20.sp,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Button to go back to main focus screen
        Button(onClick = onBackHome) {
            Text(text = stringResource(R.string.back_to_focus))
        }
    }
}
