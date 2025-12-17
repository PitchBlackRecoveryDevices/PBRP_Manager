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

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val api = remember { PbrpApi.create() }
    
    // FIXED: Convert to lowercase (FOG -> fog) because GitHub URLs are case-sensitive
    val deviceCodename = Build.DEVICE.lowercase()
    
    var builds by remember { mutableStateOf<DeviceBuilds?>(null) }
    var status by remember { mutableStateOf("Checking support for ${deviceCodename}...") }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(true) {
        scope.launch {
            try {
                builds = api.getBuilds(deviceCodename)
                status = "Official Support Found"
                isLoading = false
            } catch (e: Exception) {
                // FIXED: Show the actual error message for debugging
                status = "Error: ${e.localizedMessage}\n(Target: ${deviceCodename})"
                isLoading = false
            }
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
                Text("DETECTED DEVICE", fontSize = 10.sp, color = Color.Gray)
                Text(
                    text = deviceCodename.uppercase(), 
                    fontSize = 24.sp, 
                    fontWeight = FontWeight.Bold, 
                    color = PBRP_Red
                )
                Text(text = status, color = Color.White, modifier = Modifier.padding(top = 4.dp))
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
            
            Button(
                onClick = { onDownload(build.downloadLink) },
                colors = ButtonDefaults.buttonColors(containerColor = PBRP_Red),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Download", fontWeight = FontWeight.Bold)
            }
        }
    }
}
