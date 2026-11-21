package com.example.data.remote

import android.content.Context
import com.example.R
import com.example.data.local.CaseEntity
import com.example.data.local.CaseStatus
import com.google.android.gms.tasks.Task
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class FirestoreSyncRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
) {
    // Convenience constructor resolving the provisioned enterprise database ID
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireAuthorizedUserId(expectedUserId: String): String {
        val uid = auth.currentUser?.uid
            ?: error("በGoogle መለያ ሳይገቡ Cloud Firestore መጠቀም አይቻልም።")
        check(uid == expectedUserId) { "የተጠቃሚ መለያ ተቀይሯል።" }
        return uid
    }

    suspend fun upsertUserProfile(
        userId: String,
        displayName: String,
        email: String?,
        photoUrl: String?,
    ): Result<Unit> {
        val path = "users/$userId"
        return try {
            val uid = requireAuthorizedUserId(userId)
            val docRef = db.collection("users").document(uid)
            val existingSnap = docRef.get().awaitTask()
            val profile = FirestoreUserProfile(
                userId = uid,
                displayName = displayName,
                email = email,
                photoUrl = photoUrl,
            )
            if (existingSnap.exists()) {
                docRef.update(profile.toUpdateMap()).awaitTask()
            } else {
                docRef.set(profile.toCreateMap()).awaitTask()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    fun observeCloudCases(userId: String): Flow<List<FirestoreCloudCase>> =
        db.collection("users")
            .document(userId)
            .collection("cases")
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    FirestoreCloudCase::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }

    suspend fun syncCase(userId: String, caseEntity: CaseEntity): Result<String> {
        val baseId = buildStableCaseDocId(caseEntity)
        var targetDocId = baseId
        var path = "users/$userId/cases/$targetDocId"
        return try {
            val uid = requireAuthorizedUserId(userId)
            val casesCol = db.collection("users").document(uid).collection("cases")
            val existingSnap = casesCol.document(baseId).get().awaitTask()
            val existingStatus = existingSnap.getString("status")

            // Terminal state rule protection: finalized documents are immutable in Firestore.
            // If reopened as a new draft version, persist under a fresh versioned document ID.
            if (existingSnap.exists() && existingStatus == CaseStatus.FINALIZED.name) {
                if (caseEntity.status == CaseStatus.FINALIZED) {
                    return Result.success(baseId)
                }
                targetDocId = "${baseId.take(100)}_v${caseEntity.currentVersion.coerceAtLeast(2)}"
                path = "users/$uid/cases/$targetDocId"
            }

            val targetRef = casesCol.document(targetDocId)
            val model = FirestoreCloudCase.fromLocalEntity(uid, caseEntity)
            val targetSnap = if (targetDocId == baseId) existingSnap else targetRef.get().awaitTask()
            if (targetSnap.exists()) {
                if (targetSnap.getString("status") != CaseStatus.FINALIZED.name) {
                    targetRef.update(model.toUpdateMap()).awaitTask()
                }
            } else {
                targetRef.set(model.toCreateMap()).awaitTask()
            }
            Result.success(targetDocId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    fun observeResearchNotes(userId: String): Flow<List<FirestoreResearchNote>> =
        db.collection("users")
            .document(userId)
            .collection("research_notes")
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    FirestoreResearchNote::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }

    suspend fun saveResearchNote(
        userId: String,
        noteType: ResearchNoteType,
        title: String,
        content: String,
        sources: List<String> = emptyList(),
        caseNumber: String = "",
    ): Result<String> {
        val noteId = "note_${UUID.randomUUID().toString().replace("-", "").take(20)}"
        val path = "users/$userId/research_notes/$noteId"
        return try {
            val uid = requireAuthorizedUserId(userId)
            val note = FirestoreResearchNote(
                userId = uid,
                noteType = noteType.name,
                title = title,
                content = content,
                sources = sources,
                caseNumber = caseNumber,
            )
            db.collection("users")
                .document(uid)
                .collection("research_notes")
                .document(noteId)
                .set(note.toCreateMap())
                .awaitTask()
            Result.success(noteId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteResearchNote(userId: String, noteId: String): Result<Unit> {
        val cleanId = sanitizeId(noteId)
        val path = "users/$userId/research_notes/$cleanId"
        return try {
            val uid = requireAuthorizedUserId(userId)
            require(cleanId.isNotBlank()) { "Invalid note ID" }
            db.collection("users")
                .document(uid)
                .collection("research_notes")
                .document(cleanId)
                .delete()
                .awaitTask()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    companion object {
        fun sanitizeId(raw: String): String {
            val cleaned = raw.replace(Regex("""[^a-zA-Z0-9_\-]"""), "_")
                .trim('_')
                .take(120)
            return cleaned.ifBlank { "item_${System.currentTimeMillis()}" }
        }

        fun buildStableCaseDocId(caseEntity: CaseEntity): String {
            val keyPart = when {
                caseEntity.caseNumber.isNotBlank() ->
                    "num_${caseEntity.caseType.name}_${caseEntity.caseNumber}"
                caseEntity.id > 0L ->
                    "local_${caseEntity.id}"
                caseEntity.contentSha256.isNotBlank() ->
                    "sha_${caseEntity.contentSha256.take(24)}"
                else ->
                    "draft_${System.currentTimeMillis()}"
            }
            return "case_${sanitizeId(keyPart)}".take(128)
        }
    }
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
