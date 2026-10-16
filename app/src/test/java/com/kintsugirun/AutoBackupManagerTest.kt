package com.kintsugirun

import android.content.ContentResolver
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.AfterClass
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException

class AutoBackupManagerTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var contentResolver: ContentResolver
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private lateinit var manager: AutoBackupManager

    private val prefStorage = mutableMapOf<String, String>()

    companion object {
        @JvmStatic
        @BeforeClass
        fun setupClass() {
            mockkStatic(Log::class)
            every { Log.d(any<String>(), any<String>()) } returns 0
            every { Log.e(any<String>(), any<String>()) } returns 0
            every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0
            every { Log.w(any<String>(), any<String>()) } returns 0

            mockkStatic(Uri::class)
            every { Uri.parse(any()) } answers {
                val str = firstArg<String>()
                val mockUri = mockk<Uri>()
                every { mockUri.toString() } returns str
                mockUri
            }

            mockkStatic(DocumentFile::class)
        }

        @JvmStatic
        @AfterClass
        fun tearDownClass() {
            unmockkStatic(Log::class)
            unmockkStatic(Uri::class)
            unmockkStatic(DocumentFile::class)
        }
    }

    @Before
    fun setUp() {
        prefStorage.clear()
        context = mockk(relaxed = true)
        contentResolver = mockk(relaxed = true)
        sharedPreferences = mockk(relaxed = true)
        editor = mockk(relaxed = true)

        every { context.contentResolver } returns contentResolver
        every { context.getSharedPreferences(AutoBackupManager.PREFS_NAME, Context.MODE_PRIVATE) } returns sharedPreferences

        every { sharedPreferences.getString(AutoBackupManager.KEY_AUTO_BACKUP_URI, null) } answers {
            prefStorage[AutoBackupManager.KEY_AUTO_BACKUP_URI]
        }
        every { sharedPreferences.edit() } returns editor
        every { editor.putString(AutoBackupManager.KEY_AUTO_BACKUP_URI, any()) } answers {
            prefStorage[AutoBackupManager.KEY_AUTO_BACKUP_URI] = secondArg()
            editor
        }
        every { editor.remove(AutoBackupManager.KEY_AUTO_BACKUP_URI) } answers {
            prefStorage.remove(AutoBackupManager.KEY_AUTO_BACKUP_URI)
            editor
        }
        every { editor.apply() } returns Unit

        manager = AutoBackupManager(context, testDispatcher, sharedPreferences)
    }

    @Test
    fun `isAutoBackupEnabled returns false when no URI stored`() {
        assertThat(manager.isAutoBackupEnabled()).isFalse()
    }

    @Test
    fun `getAutoBackupUri returns null when no URI stored`() {
        assertThat(manager.getAutoBackupUri()).isNull()
    }

    @Test
    fun `setAutoBackupUri saves URI and isAutoBackupEnabled returns true`() {
        val uri = mockk<Uri>()
        every { uri.toString() } returns "content://tree/folder"

        manager.setAutoBackupUri(uri)

        assertThat(manager.isAutoBackupEnabled()).isTrue()
        assertThat(manager.getAutoBackupUri()?.toString()).isEqualTo("content://tree/folder")
        verify(exactly = 1) { editor.putString(AutoBackupManager.KEY_AUTO_BACKUP_URI, "content://tree/folder") }
        verify(exactly = 1) { editor.apply() }
    }

    @Test
    fun `setAutoBackupUri with null removes URI and isAutoBackupEnabled returns false`() {
        val uri = mockk<Uri>()
        every { uri.toString() } returns "content://tree/folder"
        manager.setAutoBackupUri(uri)
        assertThat(manager.isAutoBackupEnabled()).isTrue()

        manager.setAutoBackupUri(null)

        assertThat(manager.isAutoBackupEnabled()).isFalse()
        assertThat(manager.getAutoBackupUri()).isNull()
        verify(exactly = 1) { editor.remove(AutoBackupManager.KEY_AUTO_BACKUP_URI) }
    }

    @Test
    fun `writeBackup returns false when auto backup is disabled`() = runTest(testDispatcher) {
        val result = manager.writeBackup("""{"workouts":[]}""")
        assertThat(result).isFalse()
    }

    @Test
    fun `writeBackup returns false when treeDocument cannot write`() = runTest(testDispatcher) {
        val uri = mockk<Uri>()
        every { uri.toString() } returns "content://tree/folder"
        manager.setAutoBackupUri(uri)

        val mockTreeDoc = mockk<DocumentFile>()
        every { DocumentFile.fromTreeUri(context, any()) } returns mockTreeDoc
        every { mockTreeDoc.canWrite() } returns false

        val result = manager.writeBackup("""{"workouts":[]}""")
        assertThat(result).isFalse()
    }

    @Test
    fun `writeBackup creates file and writes json content successfully`() = runTest(testDispatcher) {
        val treeUri = mockk<Uri>()
        every { treeUri.toString() } returns "content://tree/folder"
        manager.setAutoBackupUri(treeUri)

        val mockTreeDoc = mockk<DocumentFile>()
        val mockFile = mockk<DocumentFile>()
        val fileUri = mockk<Uri>()
        every { fileUri.toString() } returns "content://tree/folder/kintsugirun_auto_backup.json"
        every { mockFile.uri } returns fileUri

        every { DocumentFile.fromTreeUri(context, any()) } returns mockTreeDoc
        every { mockTreeDoc.canWrite() } returns true
        every { mockTreeDoc.findFile(AutoBackupManager.BACKUP_FILE_NAME) } returns null
        every { mockTreeDoc.createFile("application/json", AutoBackupManager.BACKUP_FILE_NAME) } returns mockFile

        val outputStream = ByteArrayOutputStream()
        every { contentResolver.openOutputStream(fileUri, "wt") } returns outputStream

        val jsonContent = """{"version":1,"workouts":[]}"""
        val result = manager.writeBackup(jsonContent)

        assertThat(result).isTrue()
        assertThat(outputStream.toString(Charsets.UTF_8.name())).isEqualTo(jsonContent)
    }

    @Test
    fun `writeBackup returns false and catches exception when openOutputStream throws`() = runTest(testDispatcher) {
        val treeUri = mockk<Uri>()
        every { treeUri.toString() } returns "content://tree/folder"
        manager.setAutoBackupUri(treeUri)

        val mockTreeDoc = mockk<DocumentFile>()
        val mockFile = mockk<DocumentFile>()
        val fileUri = mockk<Uri>()
        every { mockFile.uri } returns fileUri

        every { DocumentFile.fromTreeUri(context, any()) } returns mockTreeDoc
        every { mockTreeDoc.canWrite() } returns true
        every { mockTreeDoc.findFile(AutoBackupManager.BACKUP_FILE_NAME) } returns mockFile

        every { contentResolver.openOutputStream(fileUri, "wt") } throws IOException("Permission denied")

        val result = manager.writeBackup("""{"workouts":[]}""")
        assertThat(result).isFalse()
    }
}
