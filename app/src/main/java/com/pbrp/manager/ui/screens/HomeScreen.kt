package com.pbrp.manager.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.gson.JsonObject
import com.pbrp.manager.DeviceBuilds
import com.pbrp.manager.DeviceCandidate
import com.pbrp.manager.PbrpApi
import com.pbrp.manager.R
import com.pbrp.manager.ui.components.BuildCard
import com.pbrp.manager.ui.theme.*
import com.pbrp.manager.utils.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalTextApi::class)
@Composable
fun HomeScreen(manualCodename: String?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val api = remember { PbrpApi.create() }
    
    val targetCodename = manualCodename ?: Build.DEVICE.lowercase()
    val rawVendor = Build.MANUFACTURER
    
    // UI State
    var builds by remember { mutableStateOf<DeviceBuilds?>(null) }
    var maintainer by remember { mutableStateOf(context.getString(R.string.loading)) }
    var maintainerGithub by remember { mutableStateOf("") }
    var deviceFullTitle by remember { mutableStateOf("") }
    var status by remember { mutableStateOf(context.getString(R.string.scanning_servers)) }
    var isLoading by remember { mutableStateOf(true) }
    
    // Logic Flow
    var deviceFeatures by remember { mutableStateOf<List<DeviceFeature>>(emptyList()) }
    var installMethods by remember { mutableStateOf<List<InstallMethod>>(emptyList()) }
    var isSourceForgeFallback by remember { mutableStateOf(false) }
    
    // Expandable States
    var guideExpanded by remember { mutableStateOf(false) }
    var olderBuildsExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(targetCodename) {
        scope.launch {
            isLoading = true
            isSourceForgeFallback = false
            maintainer = context.getString(R.string.loading)
            
            val candidates = listOf(
                DeviceCandidate(rawVendor.lowercase(), targetCodename.lowercase()), 
                DeviceCandidate(rawVendor, targetCodename),                         
                DeviceCandidate(rawVendor.uppercase(), targetCodename.uppercase())  
            ).distinct()
            
            var jsonFound = false
            var officialDbInfo: JsonObject? = null
            var detectedVendor = rawVendor.lowercase()

            // 1. Check Master DB
            try {
                val masterList = api.getAllDevices()
                for (entry in masterList.entrySet()) {
                    val vendorObj = entry.value.asJsonObject
                    val matchingKey = when {
                        vendorObj.has(targetCodename) -> targetCodename
                        vendorObj.has(targetCodename.lowercase()) -> targetCodename.lowercase()
                        vendorObj.has(targetCodename.uppercase()) -> targetCodename.uppercase()
                        else -> null
                    }
                    if (matchingKey != null) {
                        officialDbInfo = vendorObj.getAsJsonObject(matchingKey)
                        detectedVendor = entry.key 
                        if (officialDbInfo!!.has("name")) deviceFullTitle = officialDbInfo!!.get("name").asString
                        if (officialDbInfo!!.has("maintainer")) {
                            maintainer = officialDbInfo!!.get("maintainer").asString
                            maintainerGithub = maintainer.split(" ").firstOrNull { it.startsWith("@") }?.replace("@", "") ?: ""
                        }
                        break
                    }
                }
            } catch (e: Exception) { }

            // 2. Try Official JSON
            for (candidate in candidates) {
                try {
                    status = context.getString(R.string.checking_device, candidate.codename)
                    val fetchedBuilds = api.getBuilds(candidate.codename)
                    
                    val resolvedLatest = fetchedBuilds.latest?.let { 
                        withContext(Dispatchers.IO) { GithubUtils.resolveBuild(it) } 
                    }
                    val resolvedOlder = fetchedBuilds.olderBuilds?.map { build ->
                        async(Dispatchers.IO) { GithubUtils.resolveBuild(build) }
                    }?.awaitAll()

                    builds = DeviceBuilds(resolvedLatest, resolvedOlder)
                    status = context.getString(R.string.status_official_found)
                    jsonFound = true
                    
                    try {
                        val markdownString = api.getDeviceInfo(detectedVendor, candidate.codename).string()
                        maintainer = ParserUtils.parseMarkdownValue(markdownString, "maintainer")
                        maintainerGithub = maintainer.split(" ").firstOrNull { it.startsWith("@") }?.replace("@", "") 
                            ?: maintainer.substringAfter("@").substringBefore(" ").substringBefore(")")
                        deviceFullTitle = ParserUtils.parseMarkdownValue(markdownString, "title").replace("\"", "")
                        
                        deviceFeatures = ParserUtils.parseFeatures(markdownString)
                        installMethods = ParserUtils.parseInstallMethods(context, markdownString)
                    } catch (e: Exception) { 
                         installMethods = ParserUtils.parseInstallMethods(context, "")
                    }
                    break
                } catch (e: Exception) { continue }
            }

            // 3. SourceForge Fallback
            if (!jsonFound) {
                status = "Scanning SourceForge..."
                val sfResult = withContext(Dispatchers.IO) {
                    SourceForgeUtils.checkAndFetch(targetCodename.lowercase())
                }

                when (sfResult) {
                    is SfResult.Found -> {
                        builds = sfResult.builds
                        status = "Found on SourceForge"
                        jsonFound = true
                        installMethods = ParserUtils.parseInstallMethods(context, "")
                    }
                    is SfResult.Empty -> {
                        if (officialDbInfo != null) {
                            isSourceForgeFallback = true
                            status = context.getString(R.string.status_official_db_only)
                        } else {
                            status = context.getString(R.string.status_not_supported)
                        }
                    }
                    is SfResult.NotFound -> {
                        status = context.getString(R.string.status_not_supported)
                        builds = null
                    }
                    is SfResult.Error -> {
                         if (officialDbInfo != null) {
                            isSourceForgeFallback = true
                            status = "Connection Error (Fallback Available)"
                        } else {
                            status = context.getString(R.string.status_connection_failed)
                        }
                    }
                }
            }
            isLoading = false
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(PBRP_Dark).padding(16.dp)) {
        // --- HEADER ---
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(if(manualCodename != null) stringResource(R.string.selected_device) else stringResource(R.string.detected_device), 
                     fontSize = 10.sp, color = Color.Gray)
                
                Text(
                    text = targetCodename.uppercase(),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    style = TextStyle(brush = PBRP_Gradient)
                )
                
                if (deviceFullTitle.isNotEmpty()) Text(deviceFullTitle, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(status, color = Color.Gray, fontSize = 12.sp)
            }
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
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = PBRP_Red) }
        } else if (isSourceForgeFallback) {
            // Fallback Card
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1500)), modifier = Modifier.fillMaxWidth().border(1.dp, PBRP_Orange, RoundedCornerShape(12.dp))) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.fallback_title), color = PBRP_Orange, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.fallback_desc), color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
                    Button(
                        onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://sourceforge.net/projects/pbrp/files/$targetCodename/"))) },
                        colors = ButtonDefaults.buttonColors(containerColor = PBRP_Orange), modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.open_sourceforge), color = Color.Black) }
                }
            }
        } else if (builds != null) {
            
            LazyColumn {
                // 1. Warnings / Features (Top)
                if (deviceFeatures.isNotEmpty()) {
                    items(deviceFeatures) { feature ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = feature.color),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                                .border(1.dp, feature.borderColor, RoundedCornerShape(12.dp))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(feature.icon, fontSize = 18.sp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(feature.title, color = feature.borderColor, fontWeight = FontWeight.Bold)
                                }
                                Text(feature.description, color = Color.LightGray, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                }

                // 2. Latest Build (Always visible)
                if (builds?.latest != null) {
                    item { 
                        Text(stringResource(R.string.latest_release), fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 8.dp))
                        BuildCard(builds!!.latest!!, true, context) 
                    }
                }

                // 3. Installation Guide (Collapsible - Below Latest, Before Older)
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { guideExpanded = !guideExpanded }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("INSTALLATION INSTRUCTIONS", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.weight(1f))
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Expand Guide",
                            tint = Color.Gray,
                            modifier = Modifier.rotate(if (guideExpanded) 180f else 0f)
                        )
                    }
                }

                // Expandable Guide Content
                if (guideExpanded) {
                    items(installMethods) { method ->
                        var expandedStep by remember { mutableStateOf(false) }
                        Card(
                            colors = CardDefaults.cardColors(containerColor = PBRP_Card),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                                .border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(8.dp))
                                .clickable { expandedStep = !expandedStep }
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(method.type.icon, fontSize = 20.sp)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(method.type.title, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                    Icon(
                                        if (expandedStep) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, 
                                        null, 
                                        tint = Color.Gray
                                    )
                                }
                                if (expandedStep) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    if (method.note != null) {
                                        Text("Note: ${method.note}", color = PBRP_Orange, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
                                    }
                                    method.steps.forEachIndexed { i, step -> 
                                        Text(
                                            "${i+1}. $step", 
                                            color = Color.LightGray, 
                                            fontSize = 14.sp, 
                                            modifier = Modifier.padding(bottom = 4.dp)
                                        ) 
                                    }
                                }
                            }
                        }
                    }
                    if(installMethods.isEmpty()) {
                        item { Text(stringResource(R.string.no_guide_found), color = Color.Gray, modifier = Modifier.padding(bottom = 8.dp)) }
                    }
                }
                
                // 4. Older Builds (Collapsible)
                if (!builds?.olderBuilds.isNullOrEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { olderBuildsExpanded = !olderBuildsExpanded }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(stringResource(R.string.older_versions), fontSize = 12.sp, color = Color.Gray, modifier = Modifier.weight(1f))
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Expand Older",
                                tint = Color.Gray,
                                modifier = Modifier.rotate(if (olderBuildsExpanded) 180f else 0f)
                            )
                        }
                    }
                    if (olderBuildsExpanded) {
                        items(builds?.olderBuilds ?: emptyList()) { build -> 
                            BuildCard(build, false, context) 
                        }
                    }
                }
            }
        } else {
            Card(colors = CardDefaults.cardColors(containerColor = Color.Red.copy(0.1f)), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.error_device_not_found), color = Color.Red, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
