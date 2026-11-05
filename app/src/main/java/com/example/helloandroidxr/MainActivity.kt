/*
 * Copyright 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.helloandroidxr

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.helloandroidxr.ui.CurvedWingsDemo
import com.example.helloandroidxr.ui.launch.SpatialLaunch
import com.example.helloandroidxr.ui.theme.HelloAndroidXRTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HelloAndroidXRTheme {
                var launched by remember { mutableStateOf(false) }
                if (launched) {
                    CurvedWingsDemo()
                } else {
                    // Stands in for the app's data load: the mark holds until this flips.
                    var ready by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) { delay(8_000); ready = true }
                    SpatialLaunch(ready = ready, onFinished = { launched = true })
                }
            }
        }
    }
}