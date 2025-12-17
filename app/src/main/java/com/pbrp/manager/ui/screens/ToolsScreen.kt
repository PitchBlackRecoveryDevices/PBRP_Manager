package com.pbrp.manager.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pbrp.manager.R
import com.pbrp.manager.RootUtils
import com.pbrp.manager.ui.theme.PBRP_Card
import com.pbrp.manager.ui.theme.PBRP_Dark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ToolsScreen() {
    var isRooted by remember { mutableStateOf(false) }
    
    LaunchedEffect(true) {
        withContext(Dispatchers.IO) { isRooted = RootUtils.isRooted() }
    }

    Column(modifier = Modifier.fillMaxSize().background(PBRP_Dark).padding(16.dp)) {
        Text(stringResource(R.string.tools_title), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
        
        Card(
            colors = CardDefaults.cardColors(containerColor = if (isRooted) Color.Green.copy(0.1f) else Color.Red.copy(0.1f)),
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp).border(1.dp, if(isRooted) Color.Green else Color.Red, RoundedCornerShape(12.dp))
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if(isRooted) Icons.Default.CheckCircle else Icons.Default.Cancel, null, tint = if(isRooted) Color.Green else Color.Red)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    if(isRooted) stringResource(R.string.root_granted) else stringResource(R.string.root_denied), 
                    color = Color.White, fontWeight = FontWeight.Bold
                )
            }
        }

        if (isRooted) {
            Text(stringResource(R.string.tools_section_power), color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
            ToolButton(stringResource(R.string.tools_reboot_system), "reboot")
            ToolButton(stringResource(R.string.tools_reboot_recovery), "reboot recovery")
            ToolButton(stringResource(R.string.tools_reboot_bootloader), "reboot bootloader")
            
            Spacer(modifier = Modifier.height(24.dp))
            Text(stringResource(R.string.tools_section_flash), color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
            Card(colors = CardDefaults.cardColors(containerColor = PBRP_Card), modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.tools_flash_desc), color = Color.Gray, modifier = Modifier.padding(16.dp), fontSize = 12.sp)
            }
        } else {
            Text(stringResource(R.string.tools_root_required), color = Color.Gray)
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
