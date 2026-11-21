package com.example.security

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.MutableContextWrapper
import android.util.Base64
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.security.SecureRandom
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TAG = "GoogleDriveAuth"

data class GoogleUserProfile(
    val uid: String,
    val displayName: String,
    val email: String,
    val photoUrl: String?,
)

sealed interface GoogleSignInOutcome {
    data class Success(val profile: GoogleUserProfile) : GoogleSignInOutcome
    data object Cancelled : GoogleSignInOutcome
    data class Error(val message: String) : GoogleSignInOutcome
}

sealed interface DriveAuthOutcome {
    data class Authorized(val accessToken: String) : DriveAuthOutcome
    data class NeedsResolution(val pendingIntent: PendingIntent) : DriveAuthOutcome
    data class Error(val message: String) : DriveAuthOutcome
}

object GoogleDriveAuthManager {
    const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"

    fun authStateFlow(auth: FirebaseAuth = Firebase.auth): Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser)
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    fun currentUserProfile(): GoogleUserProfile? {
        val user = Firebase.auth.currentUser ?: return null
        return GoogleUserProfile(
            uid = user.uid,
            displayName = user.displayName ?: user.email ?: "ዳኛ",
            email = user.email.orEmpty(),
            photoUrl = user.photoUrl?.toString(),
        )
    }

    suspend fun signInWithGoogleInteractive(activityContext: Context): GoogleSignInOutcome {
        val webClientId = activityContext.getString(R.string.default_web_client_id)
        if (webClientId.isBlank()) {
            return GoogleSignInOutcome.Error("የGoogle Sign-In ውቅር (default_web_client_id) አልተገኘም።")
        }

        val credentialManager = CredentialManager.create(activityContext)
        val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(webClientId)
            .setNonce(generateSecureRandomNonce())
            .build()

        // Single-option request for API 36 Credential Manager bottom-sheet responsiveness
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInWithGoogleOption)
            .build()

        val mutableContext = MutableContextWrapper(activityContext)
        return try {
            val result = credentialManager.getCredential(
                context = mutableContext,
                request = request,
            )
            exchangeCredentialWithFirebase(result)
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "Google Sign-In cancelled or dismissed: ${e.message}", e)
            GoogleSignInOutcome.Cancelled
        } catch (e: NoCredentialException) {
            Log.w(TAG, "No Google credential available on device: ${e.message}", e)
            GoogleSignInOutcome.Error("በመሳሪያው ላይ የGoogle መለያ አልተገኘም። እባክዎ በስልኩ Settings ውስጥ Google Account ያስገቡ።")
        } catch (e: GetCredentialException) {
            Log.e(TAG, "Google Sign-In failed: ${e.type} - ${e.message}", e)
            GoogleSignInOutcome.Error("በGoogle መግባት አልተቻለም: ${e.localizedMessage ?: "ያልታወቀ ስህተት"}")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected Google Sign-In failure", e)
            GoogleSignInOutcome.Error("በGoogle መግባት አልተቻለም: ${e.localizedMessage ?: ""}")
        }
    }

    suspend fun attemptSilentGoogleSignIn(activityContext: Context): GoogleSignInOutcome? {
        if (Firebase.auth.currentUser != null) {
            return currentUserProfile()?.let { GoogleSignInOutcome.Success(it) }
        }
        val webClientId = runCatching {
            activityContext.getString(R.string.default_web_client_id)
        }.getOrNull().orEmpty()
        if (webClientId.isBlank()) return null

        val credentialManager = CredentialManager.create(activityContext)
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
            .setServerClientId(webClientId)
            .setAutoSelectEnabled(true)
            .setNonce(generateSecureRandomNonce())
            .build()

        // Separate single-option request for silent sign-in
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        val mutableContext = MutableContextWrapper(activityContext)
        return try {
            val result = credentialManager.getCredential(
                context = mutableContext,
                request = request,
            )
            exchangeCredentialWithFirebase(result)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    suspend fun authorizeGoogleDrive(context: Context): DriveAuthOutcome {
        val requestedScopes = listOf(Scope(DRIVE_FILE_SCOPE))
        val authorizationRequest = AuthorizationRequest.builder()
            .setRequestedScopes(requestedScopes)
            .build()

        return try {
            val authResult = Identity.getAuthorizationClient(context)
                .authorize(authorizationRequest)
                .awaitTask()

            if (authResult.hasResolution()) {
                val pendingIntent = authResult.pendingIntent
                if (pendingIntent != null) {
                    DriveAuthOutcome.NeedsResolution(pendingIntent)
                } else {
                    DriveAuthOutcome.Error("የGoogle Drive ፍቃድ መስኮት መክፈት አልተቻለም።")
                }
            } else {
                val token = authResult.accessToken
                if (!token.isNullOrBlank()) {
                    DriveAuthOutcome.Authorized(token)
                } else {
                    DriveAuthOutcome.Error("የGoogle Drive መዳረሻ ቶከን አልተገኘም።")
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiException) {
            Log.e(TAG, "Drive authorization ApiException status=${e.statusCode}", e)
            DriveAuthOutcome.Error("የGoogle Drive ፍቃድ አልተሰጠም (Code ${e.statusCode})።")
        } catch (e: Exception) {
            Log.e(TAG, "Drive authorization failed", e)
            DriveAuthOutcome.Error("የGoogle Drive ፍቃድ አልተሳካም: ${e.localizedMessage ?: ""}")
        }
    }

    fun extractDriveTokenFromIntent(context: Context, data: Intent?): DriveAuthOutcome {
        return try {
            val authResult = Identity.getAuthorizationClient(context)
                .getAuthorizationResultFromIntent(data)
            val token = authResult.accessToken
            if (!token.isNullOrBlank()) {
                DriveAuthOutcome.Authorized(token)
            } else {
                DriveAuthOutcome.Error("የGoogle Drive ፍቃድ ተሰርዟል ወይም ቶከን አልተገኘም።")
            }
        } catch (e: ApiException) {
            Log.w(TAG, "Drive authorization resolution failed status=${e.statusCode}", e)
            DriveAuthOutcome.Error("የGoogle Drive ፍቃድ አልተሰጠም ወይም ተሰርዟል።")
        } catch (e: Exception) {
            Log.e(TAG, "Drive token extraction failed", e)
            DriveAuthOutcome.Error("የDrive ፍቃድ ማረጋገጥ አልተቻለም።")
        }
    }

    suspend fun getSilentDriveAccessToken(context: Context): String? {
        return when (val res = authorizeGoogleDrive(context)) {
            is DriveAuthOutcome.Authorized -> res.accessToken
            else -> null
        }
    }

    suspend fun signOut(context: Context) {
        withContext(NonCancellable) {
            Firebase.auth.signOut()
            runCatching {
                CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
            }.onFailure { e ->
                Log.w(TAG, "Failed to clear credential state", e)
            }
        }
    }

    private suspend fun exchangeCredentialWithFirebase(result: GetCredentialResponse): GoogleSignInOutcome {
        val credential = result.credential
        if (credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            return GoogleSignInOutcome.Error("ያልተጠበቀ የGoogle መታወቂያ አይነት ተመልሷል።")
        }

        return try {
            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
            val firebaseCred = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
            val authResult = Firebase.auth.signInWithCredential(firebaseCred).awaitTask()
            val user = authResult.user
                ?: return GoogleSignInOutcome.Error("የFirebase መለያ ማረጋገጫ አልተሳካም።")
            GoogleSignInOutcome.Success(
                GoogleUserProfile(
                    uid = user.uid,
                    displayName = user.displayName ?: googleIdTokenCredential.displayName ?: user.email ?: "ዳኛ",
                    email = user.email ?: googleIdTokenCredential.id,
                    photoUrl = user.photoUrl?.toString() ?: googleIdTokenCredential.profilePictureUri?.toString(),
                )
            )
        } catch (e: GoogleIdTokenParsingException) {
            Log.e(TAG, "Invalid Google ID token response", e)
            GoogleSignInOutcome.Error("የGoogle መታወቂያ ቶከን መተንተን አልተቻለም።")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Firebase signInWithCredential failed", e)
            GoogleSignInOutcome.Error("የGoogle መግቢያ አልተሳካም: ${e.localizedMessage ?: ""}")
        }
    }

    private fun generateSecureRandomNonce(byteLength: Int = 32): String {
        val randomBytes = ByteArray(byteLength)
        SecureRandom().nextBytes(randomBytes)
        return Base64.encodeToString(
            randomBytes,
            Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING
        )
    }

    private suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { result ->
            if (cont.isActive) cont.resume(result)
        }
        addOnFailureListener { e ->
            if (cont.isActive) cont.resumeWithException(e)
        }
        addOnCanceledListener {
            if (cont.isActive) cont.cancel()
        }
    }
}
