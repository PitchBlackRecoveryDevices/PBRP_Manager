package com.pbrp.manager

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PBRPTheme {
                MainScreen()
            }
        }
    }
}

// --- Theme Colors ---
val PBRP_Red = Color(0xFFD32F2F)
val PBRP_Dark = Color(0xFF050505)
val PBRP_Card = Color(0xFF121212)

@Composable
fun PBRPTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = PBRP_Dark,
            surface = PBRP_Card,
            primary = PBRP_Red
        ),
        content = content
    )
}

// Helper to hold retry combinations
data class DeviceCandidate(val vendor: String, val codename: String)

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val api = remember { PbrpApi.create() }
    
    // Raw values from Android System
    val rawDevice = Build.DEVICE      // e.g. "FOG"
    val rawVendor = Build.MANUFACTURER // e.g. "Xiaomi"
    
    // Generate candidates (Priority: Lowercase -> Raw -> Uppercase)
    // This solves the case-sensitivity issue on GitHub
    val candidates = listOf(
        DeviceCandidate(rawVendor.lowercase(), rawDevice.lowercase()), 
        DeviceCandidate(rawVendor, rawDevice),                         
        DeviceCandidate(rawVendor.uppercase(), rawDevice.uppercase())  
    ).distinct()

    var builds by remember { mutableStateOf<DeviceBuilds?>(null) }
    var maintainer by remember { mutableStateOf("Loading...") }
    var deviceFullTitle by remember { mutableStateOf("") }
    var detectedName by remember { mutableStateOf(rawDevice) } 
    var status by remember { mutableStateOf("Scanning servers...") }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(true) {
        scope.launch {
            var success = false
            
            // --- SMART RETRY LOOP ---
            for (candidate in candidates) {
                try {
                    status = "Checking: ${candidate.codename}..."
                    
                    // 1. Try to fetch Builds JSON
                    val result = api.getBuilds(candidate.codename)
                    
                    // If JSON exists, we found the device!
                    builds = result
                    detectedName = candidate.codename
                    status = "Official Support Found"
                    success = true
                    
                    // 2. Now try to fetch Metadata (Markdown) for Maintainer name
                    // We do this inside a try/catch so missing markdown doesn't crash the app
                    try {
                        val markdownString = api.getDeviceInfo(candidate.vendor, candidate.codename).string()
                        maintainer = parseMarkdownValue(markdownString, "maintainer")
                        deviceFullTitle = parseMarkdownValue(markdownString, "title").replace("\"", "")
                    } catch (e: Exception) {
                        maintainer = "Unknown (Metadata missing)"
                    }

                    break // Stop looking, we found it!
                    
                } catch (e: Exception) {
                    // This candidate failed (404), try next
                    continue
                }
            }

            if (!success) {
                status = "Device not found.\nChecked: ${candidates.joinToString { it.codename }}"
            }
            isLoading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PBRP_Dark)
            .padding(16.dp)
    ) {
        // App Title
        Text(
            text = "PBRP Manager",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // Device Info Card
        Card(
            colors = CardDefaults.cardColors(containerColor = PBRP_Card),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = detectedName.uppercase(), 
                        fontSize = 24.sp, 
                        fontWeight = FontWeight.Bold, 
                        color = PBRP_Red,
                        modifier = Modifier.weight(1f)
                    )
                    // Status Badge
                    if (!isLoading && builds != null) {
                        Text(
                            text = "OFFICIAL",
                            color = Color.Green,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .background(Color.Green.copy(0.1f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                
                if (deviceFullTitle.isNotEmpty()) {
                    Text(text = deviceFullTitle, color = Color.White, fontWeight = FontWeight.SemiBold)
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Maintainer Row
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Maintainer: ", color = Color.Gray, fontSize = 12.sp)
                    Text(text = maintainer, color = Color.LightGray, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                
                if (!isLoading && builds == null) {
                     Text(text = status, color = Color.Red, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (isLoading) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PBRP_Red)
            }
        } else {
            LazyColumn {
                if (builds?.latest != null) {
                    item {
                        Text("LATEST RELEASE", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 8.dp))
                        BuildItem(builds!!.latest!!, true) { link ->
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
                            context.startActivity(intent)
                        }
                    }
                }

                if (!builds?.olderBuilds.isNullOrEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("OLDER VERSIONS", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 8.dp))
                    }
                    items(builds!!.olderBuilds!!) { build ->
                        BuildItem(build, false) { link ->
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
                            context.startActivity(intent)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Regex parser to extract values from Jekyll Front Matter (Markdown)
 */
fun parseMarkdownValue(content: String, key: String): String {
    val regex = Regex("$key:\\s*(.*)", RegexOption.IGNORE_CASE)
    val match = regex.find(content)
    return match?.groupValues?.get(1)?.trim() ?: "Unknown"
}

@Composable
fun BuildItem(build: BuildInfo, isLatest: Boolean, onDownload: (String) -> Unit) {
    val borderColor = if (isLatest) PBRP_Red else Color.White.copy(alpha = 0.1f)
    
    Card(
        colors = CardDefaults.cardColors(containerColor = PBRP_Card),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "v${build.version}",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = build.buildType,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (build.buildType == "OFFICIAL") Color.Green else Color(0xFFFF9800),
                    modifier = Modifier
                        .background(Color.White.copy(0.1f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            
            Text(text = build.date, fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(vertical = 4.dp))
            
            if (build.changelog != null) {
                Text(
                    text = build.changelog.replace("-", "• "), 
                    fontSize = 12.sp, 
                    color = Color.LightGray, 
                    maxLines = 3,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            Button(
                onClick = { onDownload(build.downloadLink) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = PBRP_Red,
                    contentColor = Color.White
                ),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Download", fontWeight = FontWeight.Bold)
            }
        }
    }
}
