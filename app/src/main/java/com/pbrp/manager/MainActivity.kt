package com.pbrp.manager

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PBRPTheme {
                AppNavigation()
            }
        }
    }
}

// --- THEME ---
val PBRP_Red = Color(0xFFD32F2F)
val PBRP_Dark = Color(0xFF050505)
val PBRP_Card = Color(0xFF121212)
val PBRP_Orange = Color(0xFFFF9800) // Fixed: Added Custom Orange

@Composable
fun PBRPTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(background = PBRP_Dark, surface = PBRP_Card, primary = PBRP_Red),
        content = content
    )
}

// --- NAVIGATION ---
@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    var selectedDevice by remember { mutableStateOf<String?>(null) }

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = Color.Black) {
                val items = listOf("Home" to Icons.Default.Home, "Search" to Icons.Default.Search, "Tools" to Icons.Default.Build)
                items.forEach { (name, icon) ->
                    NavigationBarItem(
                        icon = { Icon(icon, contentDescription = name) },
                        label = { Text(name) },
                        selected = false,
                        onClick = { 
                            if (name == "Home") selectedDevice = null // Reset to auto-detect on Home click
                            navController.navigate(name.lowercase()) { launchSingleTop = true }
                        },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = PBRP_Red.copy(alpha = 0.3f))
                    )
                }
            }
        }
    ) { padding ->
        NavHost(navController, startDestination = "home", modifier = Modifier.padding(padding)) {
            composable("home") { HomeScreen(selectedDevice) }
            composable("search") { 
                SearchScreen(onDeviceSelected = { codename ->
                    selectedDevice = codename
                    navController.navigate("home")
                }) 
            }
            composable("tools") { ToolsScreen() }
        }
    }
}

// --- SCREEN 1: HOME ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(manualCodename: String?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val api = remember { PbrpApi.create() }
    
    val targetCodename = manualCodename ?: Build.DEVICE.lowercase()
    
    // UI State
    var builds by remember { mutableStateOf<DeviceBuilds?>(null) }
    var maintainer by remember { mutableStateOf("Loading...") }
    var maintainerGithub by remember { mutableStateOf("") }
    var deviceFullTitle by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Scanning servers...") }
    var isLoading by remember { mutableStateOf(true) }
    
    // Fallback Logic State
    var isSourceForgeFallback by remember { mutableStateOf(false) }
    
    // Install Guide State
    var showGuide by remember { mutableStateOf(false) }
    var installSteps by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(targetCodename) {
        scope.launch {
            isLoading = true
            isSourceForgeFallback = false
            maintainer = "Loading..."
            
            try {
                // 1. Try to fetch specific JSON build data
                status = "Checking: $targetCodename..."
                val result = api.getBuilds(targetCodename)
                
                // JSON Found -> Success
                builds = result
                status = "Official Support Found"
                
                // Fetch Metadata (Markdown) to get Maintainer/Install Guide
                try {
                    // We need to find the vendor folder. We query the master list to find the vendor key.
                    val masterList = api.getAllDevices()
                    var detectedVendor = "xiaomi" // Default fallback
                    
                    masterList.entrySet().forEach { vendorEntry ->
                        val vendorObj = vendorEntry.value.asJsonObject
                        if (vendorObj.has(targetCodename)) detectedVendor = vendorEntry.key
                    }

                    val markdownString = api.getDeviceInfo(detectedVendor, targetCodename).string()
                    
                    // Parse Details
                    maintainer = parseMarkdownValue(markdownString, "maintainer")
                    // Clean username for avatar (remove brackets, etc)
                    maintainerGithub = maintainer.split(" ").firstOrNull { it.startsWith("@") }?.replace("@", "") 
                        ?: maintainer.substringAfter("@").substringBefore(" ").substringBefore(")")
                        
                    deviceFullTitle = parseMarkdownValue(markdownString, "title").replace("\"", "")
                    installSteps = parseInstallSteps(markdownString)
                    
                } catch (e: Exception) {
                    maintainer = "Unknown (Metadata Error)"
                }

            } catch (e: Exception) {
                // 2. JSON FAILED -> CHECK MASTER DATABASE FALLBACK
                try {
                    status = "Checking Master Database..."
                    val masterList = api.getAllDevices()
                    var foundInfo: JsonObject? = null

                    masterList.entrySet().forEach { vendorEntry ->
                        val vendorObj = vendorEntry.value.asJsonObject
                        if (vendorObj.has(targetCodename)) {
                            foundInfo = vendorObj.getAsJsonObject(targetCodename)
                        }
                    }

                    if (foundInfo != null) {
                        // DEVICE IS OFFICIAL, BUT JSON IS MISSING -> FALLBACK MODE
                        isSourceForgeFallback = true
                        status = "Official (Database Only)"
                        deviceFullTitle = foundInfo!!.get("name").asString
                        maintainer = foundInfo!!.get("maintainer").asString
                        maintainerGithub = maintainer.split(" ").firstOrNull { it.startsWith("@") }?.replace("@", "") ?: ""
                    } else {
                        status = "Device not officially supported."
                        builds = null
                    }
                } catch (ex: Exception) {
                    status = "Connection Failed"
                }
            }
            isLoading = false
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(PBRP_Dark).padding(16.dp)) {
        // --- HEADER ---
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(if(manualCodename != null) "SELECTED DEVICE" else "DETECTED DEVICE", 
                     fontSize = 10.sp, color = Color.Gray)
                Text(targetCodename.uppercase(), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = PBRP_Red)
                
                if (deviceFullTitle.isNotEmpty()) {
                    Text(deviceFullTitle, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(status, color = Color.Gray, fontSize = 12.sp)
            }
            
            // Maintainer Avatar
            if (!isLoading && maintainerGithub.isNotEmpty()) {
                Spacer(modifier = Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    AsyncImage(
                        model = "https://github.com/$maintainerGithub.png",
                        contentDescription = null,
                        modifier = Modifier.size(40.dp).clip(CircleShape).border(1.dp, Color.White, CircleShape)
                    )
                    Text(maintainer.take(15), color = Color.LightGray, fontSize = 10.sp, maxLines = 1)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        if (isLoading) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PBRP_Red)
            }
        } else if (isSourceForgeFallback) {
            // --- FALLBACK UI ---
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1500)), modifier = Modifier.fillMaxWidth().border(1.dp, PBRP_Orange, RoundedCornerShape(12.dp))) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Build Index Unavailable", color = PBRP_Orange, fontWeight = FontWeight.Bold)
                    Text("This device is supported, but the update index is missing. You can check the file server manually.", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
                    Button(
                        onClick = { 
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://sourceforge.net/projects/pbrp/files/$targetCodename/"))
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PBRP_Orange),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Open SourceForge", color = Color.Black) }
                }
            }
        } else if (builds != null) {
            // --- STANDARD UI ---
            // Guide Button
            Button(
                onClick = { showGuide = true },
                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(0.1f)),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Info, null, tint = PBRP_Red, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Installation Guide")
            }
            
            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn {
                if (builds?.latest != null) {
                    item { 
                        Text("LATEST RELEASE", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 8.dp))
                        BuildCard(builds!!.latest!!, true, context) 
                    }
                }
                if (!builds?.olderBuilds.isNullOrEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("OLDER VERSIONS", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 8.dp))
                    }
                    items(builds?.olderBuilds ?: emptyList()) { build ->
                        BuildCard(build, false, context)
                    }
                }
            }
        } else {
            // --- ERROR UI ---
            Card(colors = CardDefaults.cardColors(containerColor = Color.Red.copy(0.1f)), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Device Not Found", color = Color.Red, fontWeight = FontWeight.Bold)
                    Text("This device is not officially supported by PBRP yet.", color = Color.Gray, fontSize = 12.sp)
                }
            }
        }
        
        // --- GUIDE DIALOG ---
        if (showGuide) {
            AlertDialog(
                onDismissRequest = { showGuide = false },
                confirmButton = { TextButton(onClick = { showGuide = false }) { Text("Close", color = PBRP_Red) } },
                title = { Text("How to Install", color = Color.White) },
                text = {
                    Column {
                        if(installSteps.isEmpty()) Text("No specific guide found.", color = Color.Gray)
                        installSteps.forEachIndexed { i, step ->
                            Text("${i+1}. $step", color = Color.LightGray, modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }
                },
                containerColor = PBRP_Card
            )
        }
    }
}

// --- SCREEN 2: SEARCH ---
@Composable
fun SearchScreen(onDeviceSelected: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val api = remember { PbrpApi.create() }
    var searchQuery by remember { mutableStateOf("") }
    var allDevices by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) } // Pair(Codename, FullName)
    var filteredDevices by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }

    LaunchedEffect(true) {
        scope.launch {
            try {
                val json = api.getAllDevices()
                val list = mutableListOf<Pair<String, String>>()
                json.entrySet().forEach { vendor ->
                    vendor.value.asJsonObject.entrySet().forEach { device ->
                        val name = device.value.asJsonObject.get("name").asString
                        list.add(device.key to name)
                    }
                }
                allDevices = list.sortedBy { it.first }
                filteredDevices = allDevices
            } catch (e: Exception) {}
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(PBRP_Dark).padding(16.dp)) {
        TextField(
            value = searchQuery,
            onValueChange = { query ->
                searchQuery = query
                filteredDevices = allDevices.filter { 
                    it.first.contains(query, ignoreCase = true) || it.second.contains(query, ignoreCase = true)
                }
            },
            placeholder = { Text("Search 150+ Devices...") },
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = PBRP_Card, unfocusedContainerColor = PBRP_Card,
                focusedIndicatorColor = PBRP_Red, unfocusedTextColor = Color.White, focusedTextColor = Color.White
            ),
            shape = RoundedCornerShape(12.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        LazyColumn {
            items(filteredDevices) { (codename, fullname) ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = PBRP_Card),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onDeviceSelected(codename) }
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(fullname, color = Color.White, fontWeight = FontWeight.Bold)
                            Text(codename, color = Color.Gray, fontSize = 12.sp)
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = PBRP_Red)
                    }
                }
            }
        }
    }
}

// --- SCREEN 3: TOOLS ---
@Composable
fun ToolsScreen() {
    var isRooted by remember { mutableStateOf(false) }
    
    LaunchedEffect(true) {
        withContext(Dispatchers.IO) { isRooted = RootUtils.isRooted() }
    }

    Column(modifier = Modifier.fillMaxSize().background(PBRP_Dark).padding(16.dp)) {
        Text("Advanced Tools", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
        
        Card(
            colors = CardDefaults.cardColors(containerColor = if (isRooted) Color.Green.copy(0.1f) else Color.Red.copy(0.1f)),
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp).border(1.dp, if(isRooted) Color.Green else Color.Red, RoundedCornerShape(12.dp))
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if(isRooted) Icons.Default.CheckCircle else Icons.Default.Cancel, null, tint = if(isRooted) Color.Green else Color.Red)
                Spacer(modifier = Modifier.width(12.dp))
                Text(if(isRooted) "Root Access Granted" else "Root Access Denied", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }

        if (isRooted) {
            Text("Power Menu", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
            ToolButton("Reboot System", "reboot")
            ToolButton("Reboot Recovery", "reboot recovery")
            ToolButton("Reboot Bootloader", "reboot bootloader")
            
            Spacer(modifier = Modifier.height(24.dp))
            Text("Flashing", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
            Card(colors = CardDefaults.cardColors(containerColor = PBRP_Card), modifier = Modifier.fillMaxWidth()) {
                Text("Native flashing coming soon. Use TWRP/PBRP recovery to flash zip files.", color = Color.Gray, modifier = Modifier.padding(16.dp), fontSize = 12.sp)
            }
        } else {
            Text("You need Magisk or KernelSU to use these tools.", color = Color.Gray)
        }
    }
}

@Composable
fun ToolButton(label: String, command: String) {
    val scope = rememberCoroutineScope()
    Button(
        onClick = { scope.launch(Dispatchers.IO) { RootUtils.execute(command) } },
        colors = ButtonDefaults.buttonColors(containerColor = PBRP_Card),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color.White.copy(0.1f))
    ) {
        Text(label, color = Color.White)
    }
}

// --- HELPER COMPOSABLES & FUNCTIONS ---

@Composable
fun BuildCard(build: BuildInfo, isLatest: Boolean, context: Context) {
    Card(
        colors = CardDefaults.cardColors(containerColor = PBRP_Card),
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp).border(1.dp, if(isLatest) PBRP_Red else Color.White.copy(0.1f), RoundedCornerShape(12.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("v${build.version}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(1f))
                Text(build.buildType, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (build.buildType == "OFFICIAL") Color.Green else PBRP_Orange, // Used Fixed Orange
                     modifier = Modifier.background(Color.White.copy(0.1f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
            }
            Text(build.date, fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(vertical = 4.dp))
            
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (build.githubLink != null) {
                    Button(
                        onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(build.githubLink))) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(0.1f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) { Icon(Icons.Default.Code, null); Spacer(modifier = Modifier.width(4.dp)); Text("GitHub") }
                }
                
                Button(
                    onClick = { 
                        // Start Native Download
                        DownloadUtils.startDownload(context, build.downloadLink, "PBRP-${build.version}-${build.date}.zip")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PBRP_Red),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) { Icon(Icons.Default.Download, null); Spacer(modifier = Modifier.width(4.dp)); Text("Download") }
            }
        }
    }
}

fun parseMarkdownValue(content: String, key: String): String {
    val regex = Regex("$key:\\s*(.*)", RegexOption.IGNORE_CASE)
    return regex.find(content)?.groupValues?.get(1)?.trim() ?: ""
}

fun parseInstallSteps(content: String): List<String> {
    return when {
        content.contains("fastbootinstall", true) || content.contains("fastbootabinstall", true) -> 
            listOf("Reboot device to Bootloader/Fastboot mode.", "Run: fastboot boot pbrp.img", "Copy zip to device", "Flash Zip in Recovery")
        content.contains("odininstall", true) -> 
            listOf("Reboot to Download Mode.", "Open Odin on PC.", "Select .tar.md5 in AP slot.", "Uncheck 'Auto Reboot'.", "Click Start & Manually Reboot.")
        content.contains("mtkinstall", true) ->
            listOf("Use SP Flash Tool.", "Load Scatter file.", "Select only Recovery partition.", "Click Download.")
        else -> listOf("Download Zip.", "Reboot to current Recovery.", "Flash Zip.", "Reboot.")
    }
}
