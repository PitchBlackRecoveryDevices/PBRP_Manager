package com.pbrp.manager.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pbrp.manager.BuildInfo
import com.pbrp.manager.DownloadUtils
import com.pbrp.manager.R
import com.pbrp.manager.ui.theme.PBRP_Card
import com.pbrp.manager.ui.theme.PBRP_Orange
import com.pbrp.manager.ui.theme.PBRP_Red

@Composable
fun BuildCard(build: BuildInfo, isLatest: Boolean, context: Context) {
    val downloadName = remember(build) {
        if (!build.fileName.isNullOrEmpty()) {
            build.fileName
        } else {
            try {
                val uri = Uri.parse(build.downloadLink)
                val last = uri.lastPathSegment
                if (last != null && last.contains(".") && last != "download") {
                    last
                } else if (last == "download") {
                    val segments = uri.pathSegments
                    if (segments.size > 1) segments[segments.size - 2] else "PBRP-${build.version}.zip"
                } else {
                    "PBRP-${build.version}.zip"
                }
            } catch (e: Exception) {
                "PBRP-${build.version}.zip"
            }
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = PBRP_Card),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .border(1.dp, if(isLatest) PBRP_Red else Color.White.copy(0.1f), RoundedCornerShape(12.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("v${build.version}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(1f))
                
                Text(
                    text = build.buildType, 
                    fontSize = 10.sp, 
                    fontWeight = FontWeight.Bold, 
                    color = when(build.buildType) {
                        "OFFICIAL" -> Color.Green
                        "BETA" -> Color(0xFF9C27B0)
                        "IMG" -> Color(0xFFFFEB3B)
                        "SOURCEFORGE" -> Color.Cyan
                        else -> PBRP_Orange
                    },
                    modifier = Modifier
                        .background(Color.White.copy(0.1f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            Text(build.date, fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(vertical = 4.dp))
            
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (build.githubLink != null) {
                    Button(
                        onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(build.githubLink))) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White.copy(0.1f),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) { 
                        Icon(Icons.Default.Code, null, tint = Color.White)
                        Spacer(modifier = Modifier.width(4.dp))
                        // EXPLICIT COLOR SET HERE
                        Text(stringResource(R.string.github), color = Color.White) 
                    }
                }
                
                Button(
                    onClick = { 
                        DownloadUtils.startDownload(context, build.downloadLink, downloadName)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PBRP_Red,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) { 
                    Icon(Icons.Default.Download, null, tint = Color.White)
                    Spacer(modifier = Modifier.width(4.dp))
                    // EXPLICIT COLOR SET HERE
                    Text(stringResource(R.string.download), color = Color.White) 
                }
            }
        }
    }
}
