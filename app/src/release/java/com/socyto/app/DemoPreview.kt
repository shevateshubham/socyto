package com.socyto.app

import androidx.compose.runtime.Composable

/** Release variant never exposes a demo screen. */
@Composable
fun DemoPreview(onExit: () -> Unit) { onExit() }
