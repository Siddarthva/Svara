package com.svara.app.data.repository

import androidx.core.net.toUri
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import com.svara.app.data.db.FolderRuleDao
import com.svara.app.data.db.FolderRuleEntity
import com.svara.app.data.ruleId
import com.svara.app.data.toRule
import com.svara.app.domain.model.FolderKey
import com.svara.app.domain.model.FolderRule
import com.svara.app.domain.model.FolderRuleType
import com.svara.app.domain.repository.FolderRepository
import com.svara.app.domain.repository.LibraryRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FolderRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: FolderRuleDao,
    private val library: LibraryRepository,
    private val clock: Clock,
) : FolderRepository {

    override val rules: Flow<List<FolderRule>> = dao.observeAll().map { rows -> rows.map { it.toRule() } }

    override suspend fun addFolder(treeUri: String, type: FolderRuleType): FolderRule? {
        val uri = treeUri.toUri()
        val key = folderKeyForTree(uri) ?: return null
        val granted = try {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            true
        } catch (e: SecurityException) {
            Log.w(TAG, "Couldn't persist access to $uri", e)
            false
        }
        val entity = FolderRuleEntity(key.ruleId(), key.volume, key.path, type.name, treeUri, clock.now(), granted)
        dao.upsert(entity)
        library.applyFolderRules()
        if (type == FolderRuleType.INCLUDE) library.rescanFolders(listOf(key))
        return entity.toRule()
    }

    override suspend fun removeRule(key: FolderKey) {
        val all = dao.getAll()
        val rule = all.firstOrNull { it.id == key.ruleId() } ?: return
        dao.delete(rule.id)
        library.applyFolderRules()
        val tree = rule.treeUri ?: return
        if (all.none { it.id != rule.id && it.treeUri == tree }) {
            runCatching {
                context.contentResolver.releasePersistableUriPermission(tree.toUri(), Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
    }

    override suspend fun refreshAccess() {
        val persisted = context.contentResolver.persistedUriPermissions
            .filter { it.isReadPermission }
            .mapTo(HashSet()) { it.uri.toString() }
        for (rule in dao.getAll()) {
            val granted = rule.treeUri != null && rule.treeUri in persisted
            if (granted != rule.accessGranted) dao.setAccess(rule.id, granted)
        }
    }

    companion object {
        private const val TAG = "FolderRepository"
        private const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"

        /**
         * Only trees from the device storage provider map onto MediaStore folders. Their document
         * ids look like "primary:Music/Rock" or "1A2B-3C4D:Music".
         */
        fun folderKeyForTree(uri: Uri): FolderKey? {
            if (uri.authority != EXTERNAL_STORAGE_AUTHORITY) return null
            val docId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull() ?: return null
            return FolderKey.fromId(docId)
        }
    }
}
