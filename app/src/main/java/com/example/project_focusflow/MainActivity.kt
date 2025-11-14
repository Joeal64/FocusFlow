package com.example.project_focusflow

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.project_focusflow.ui.theme.ProjectFocusFlowTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ProjectFocusFlowTheme {

                var showTimer by remember { mutableStateOf(false) }
                var minutes by remember { mutableStateOf(25) }

                if (showTimer) {
                    PomodoroTimer(startMinutes = minutes)
                } else {
                    FocusFlowScreen(
                        onStart = { userMinutes ->
                            minutes = userMinutes
                            showTimer = true
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun FocusFlowScreen(
    onStart: (Int) -> Unit
) {
    var studyMinutes by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        Text(
            text = "FocusFlow",
            fontSize = 32.sp
        )

        Spacer(modifier = Modifier.height(32.dp))

        TextField(
            value = studyMinutes,
            onValueChange = { studyMinutes = it },
            placeholder = { Text("Enter minutes you want to study for") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                val mins = studyMinutes.toIntOrNull() ?: 25
                onStart(mins)
            }
        ) {
            Text("Start Timer")
        }
    }
}
