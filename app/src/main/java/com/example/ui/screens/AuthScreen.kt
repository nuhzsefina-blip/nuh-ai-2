package com.example.ui.screens

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.security.GoogleDriveAuthManager
import com.example.security.GoogleSignInOutcome
import com.example.ui.theme.AlertCrimson
import com.example.ui.theme.JudicialGoldBright
import com.example.ui.theme.JudicialNavy
import com.example.ui.theme.JudicialNavyLight
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(
    activityContext: Context,
) {
    val scope = rememberCoroutineScope()
    var isBusy by remember { mutableStateOf(false) }
    var isCheckingSilent by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val silent = GoogleDriveAuthManager.attemptSilentGoogleSignIn(activityContext)
        isCheckingSilent = false
        if (silent is GoogleSignInOutcome.Error) {
            errorMessage = silent.message
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                // Court Banner Header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(JudicialNavy, JudicialNavyLight, Color(0xFF284B63))
                            )
                        ),
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_court_banner),
                        contentDescription = stringResource(R.string.cd_court_banner),
                        contentScale = ContentScale.Crop,
                        alpha = 0.25f,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Surface(
                            color = JudicialGoldBright.copy(alpha = 0.18f),
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Icon(
                                Icons.Default.Balance,
                                contentDescription = null,
                                tint = JudicialGoldBright,
                                modifier = Modifier
                                    .padding(12.dp)
                                    .size(34.dp),
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                        Text(
                            text = stringResource(R.string.court_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.85f),
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.auth_welcome_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = stringResource(R.string.auth_welcome_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            AuthFeatureRow(
                                icon = Icons.Default.CloudDone,
                                text = "Cloud Firestore እና የተመሰጠረ የመዝገብ ማመሳሰል",
                            )
                            AuthFeatureRow(
                                icon = Icons.Default.Mic,
                                text = "የቀጥታ የድምጽ ውይይት (Live) እና የማይክሮፎን ጽሁፍ ቅጂ",
                            )
                            AuthFeatureRow(
                                icon = Icons.Default.Search,
                                text = "በGoogle Search የተደገፈ የህግ ምርምር",
                            )
                            AuthFeatureRow(
                                icon = Icons.Default.Lock,
                                text = "የዳኛ ኑህ መሀመድ የውሳኔ አፃፃፍ ስልት እና Tamper-Proof PDF",
                            )
                        }

                        if (!errorMessage.isNullOrBlank()) {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = AlertCrimson.copy(alpha = 0.12f)
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(
                                        Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = AlertCrimson,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Text(
                                        text = errorMessage.orEmpty(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = AlertCrimson,
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = {
                                if (isBusy) return@Button
                                errorMessage = null
                                isBusy = true
                                scope.launch {
                                    when (
                                        val outcome = GoogleDriveAuthManager.signInWithGoogleInteractive(
                                            activityContext
                                        )
                                    ) {
                                        is GoogleSignInOutcome.Success -> {
                                            isBusy = false
                                        }
                                        is GoogleSignInOutcome.Cancelled -> {
                                            isBusy = false
                                            errorMessage = "የGoogle መግቢያ ተቋርጧል። እባክዎ ለመቀጠል መለያዎን ይምረጡ።"
                                        }
                                        is GoogleSignInOutcome.Error -> {
                                            isBusy = false
                                            errorMessage = outcome.message
                                        }
                                    }
                                }
                            },
                            enabled = !isBusy && !isCheckingSilent,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .testTag("google_sign_in_button"),
                        ) {
                            if (isBusy || isCheckingSilent) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(stringResource(R.string.auth_signing_in))
                            } else {
                                Icon(Icons.Default.AccountCircle, contentDescription = null)
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = "${stringResource(R.string.auth_sign_in_google)} (Sign in with Google)",
                                    fontWeight = FontWeight.Bold,
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
private fun AuthFeatureRow(
    icon: ImageVector,
    text: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(10.dp),
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(6.dp)
                    .size(18.dp),
            )
        }
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}
