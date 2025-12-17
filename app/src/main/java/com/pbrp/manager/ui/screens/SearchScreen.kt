package com.pbrp.manager.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pbrp.manager.PbrpApi
import com.pbrp.manager.R
import com.pbrp.manager.ui.theme.PBRP_Card
import com.pbrp.manager.ui.theme.PBRP_Dark
import com.pbrp.manager.ui.theme.PBRP_Red
import kotlinx.coroutines.launch
import java.util.Locale

// Helper Model
data class DeviceUiModel(val codename: String, val name: String)

@Composable
fun SearchScreen(onDeviceSelected: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val api = remember { PbrpApi.create() }
    
    var searchQuery by remember { mutableStateOf("") }
    
    // Data: Map<VendorName, List<Device>>
    var allDevicesMap by remember { mutableStateOf<Map<String, List<DeviceUiModel>>>(emptyMap()) }
    var filteredMap by remember { mutableStateOf<Map<String, List<DeviceUiModel>>>(emptyMap()) }
    
    // UI State
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // Expanded states for vendors (VendorName -> IsExpanded)
    val expandedStates = remember { mutableStateMapOf<String, Boolean>() }

    fun loadDevices() {
        scope.launch {
            isLoading = true
            errorMessage = null
            try {
                val json = api.getAllDevices()
                val tempMap = mutableMapOf<String, MutableList<DeviceUiModel>>()
                
                // Parse JSON: { "xiaomi": { "lavender": {...} } }
                json.entrySet().forEach { vendorEntry ->
                    try {
                        // Capitalize Vendor: "xiaomi" -> "Xiaomi"
                        val vendorName = vendorEntry.key.replaceFirstChar { 
                            if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() 
                        }
                        
                        val vendorObj = vendorEntry.value.asJsonObject
                        val deviceList = mutableListOf<DeviceUiModel>()

                        vendorObj.entrySet().forEach { deviceEntry ->
                            val deviceObj = deviceEntry.value.asJsonObject
                            if (deviceObj.has("name")) {
                                val name = deviceObj.get("name").asString
                                deviceList.add(DeviceUiModel(deviceEntry.key, name))
                            }
                        }
                        
                        if (deviceList.isNotEmpty()) {
                            tempMap[vendorName] = deviceList.sortedBy { it.name }.toMutableList()
                        }
                    } catch (e: Exception) { /* Skip bad entry */ }
                }
                
                allDevicesMap = tempMap.toSortedMap()
                filteredMap = allDevicesMap
                
                if (allDevicesMap.isEmpty()) errorMessage = "No devices found."
            } catch (e: Exception) {
                errorMessage = "Failed to load database:\n${e.localizedMessage}"
            } finally {
                isLoading = false
            }
        }
    }

    // Filter Logic: Vendor + Name + Codename match
    LaunchedEffect(searchQuery, allDevicesMap) {
        if (searchQuery.isEmpty()) {
            filteredMap = allDevicesMap
        } else {
            val query = searchQuery.lowercase().trim()
            val result = mutableMapOf<String, List<DeviceUiModel>>()

            allDevicesMap.forEach { (vendor, devices) ->
                // Strictly filter devices. 
                // A device matches if: "Vendor Name Codename" contains the query.
                val matchingDevices = devices.filter { device ->
                    val fullString = "$vendor ${device.name} ${device.codename}"
                    fullString.lowercase().contains(query)
                }

                // Only add the vendor group if it has matching devices
                if (matchingDevices.isNotEmpty()) {
                    result[vendor] = matchingDevices
                }
            }
            filteredMap = result
        }
    }

    // Load Data
    LaunchedEffect(true) {
        if (allDevicesMap.isEmpty()) loadDevices()
    }

    Column(modifier = Modifier.fillMaxSize().background(PBRP_Dark).padding(16.dp)) {
        // Search Bar
        TextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text(stringResource(R.string.search_hint)) },
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = PBRP_Card, unfocusedContainerColor = PBRP_Card,
                focusedIndicatorColor = PBRP_Red, unfocusedTextColor = Color.White, focusedTextColor = Color.White
            ),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )
        
        Spacer(modifier = Modifier.height(16.dp))

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                isLoading -> CircularProgressIndicator(color = PBRP_Red, modifier = Modifier.align(Alignment.Center))
                errorMessage != null -> {
                    Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(errorMessage!!, color = Color.Red, textAlign = TextAlign.Center)
                        Button(onClick = { loadDevices() }, colors = ButtonDefaults.buttonColors(containerColor = PBRP_Card)) {
                            Icon(Icons.Default.Refresh, null, tint = Color.White); Spacer(modifier = Modifier.width(8.dp)); Text("Retry", color = Color.White)
                        }
                    }
                }
                filteredMap.isEmpty() -> Text("No matching devices.", color = Color.Gray, modifier = Modifier.align(Alignment.TopCenter).padding(top = 20.dp))
                else -> {
                    LazyColumn {
                        filteredMap.forEach { (vendor, devices) ->
                            item(key = vendor) {
                                // Auto-expand if searching, otherwise use toggle state
                                val isSearching = searchQuery.isNotEmpty()
                                val isExpanded = isSearching || (expandedStates[vendor] == true)

                                VendorGroup(
                                    vendorName = vendor,
                                    deviceCount = devices.size,
                                    isExpanded = isExpanded,
                                    onToggle = { if (!isSearching) expandedStates[vendor] = !isExpanded },
                                    content = {
                                        Column {
                                            devices.forEach { device ->
                                                DeviceItemCard(device, onDeviceSelected)
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VendorGroup(vendorName: String, deviceCount: Int, isExpanded: Boolean, onToggle: () -> Unit, content: @Composable () -> Unit) {
    val rotationState by animateFloatAsState(targetValue = if (isExpanded) 180f else 0f, label = "rot")

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).border(1.dp, PBRP_Card, RoundedCornerShape(8.dp))
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() }
                    .background(PBRP_Card.copy(alpha = 0.5f))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(vendorName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                Text("$deviceCount", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(end = 8.dp))
                Icon(Icons.Default.KeyboardArrowDown, "Expand", tint = PBRP_Red, modifier = Modifier.rotate(rotationState))
            }
            AnimatedVisibility(visible = isExpanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Box(modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp, top = 4.dp)) { content() }
            }
        }
    }
}

@Composable
fun DeviceItemCard(device: DeviceUiModel, onSelected: (String) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = PBRP_Card),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onSelected(device.codename) }
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.PhoneAndroid, null, tint = Color.Gray, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(device.name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(device.codename, color = Color.Gray, fontSize = 10.sp)
            }
            Icon(Icons.Default.ChevronRight, null, tint = PBRP_Red.copy(alpha = 0.5f))
        }
    }
}
