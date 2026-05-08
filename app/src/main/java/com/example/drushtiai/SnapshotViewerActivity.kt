package com.example.drushtiai

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import androidx.core.app.ShareCompat
import com.example.drushtiai.data.CheatingSnapshotRow
import com.example.drushtiai.repo.SnapshotRepository
import kotlinx.coroutines.launch

class SnapshotViewerActivity : AppCompatActivity() {

    companion object {
        fun intent(ctx: Context, snap: CheatingSnapshotRow): Intent {
            return Intent(ctx, SnapshotViewerActivity::class.java).apply {
                putExtra(IntentExtras.SNAPSHOT_ID, snap.id)
                putExtra(IntentExtras.SNAPSHOT_URL, snap.imageUrl)
                putExtra(IntentExtras.SNAPSHOT_LABEL, snap.label)
            }
        }
    }

    private val snapshotRepo = SnapshotRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_snapshot_viewer)

        val id = intent.getStringExtra(IntentExtras.SNAPSHOT_ID)
        val url = intent.getStringExtra(IntentExtras.SNAPSHOT_URL).orEmpty()
        val label = intent.getStringExtra(IntentExtras.SNAPSHOT_LABEL)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.title = label ?: "Snapshot"
        toolbar.setNavigationOnClickListener { finish() }

        Glide.with(this).load(url).fitCenter().into(findViewById<ImageView>(R.id.ivFullscreen))

        val fabShare = findViewById<FloatingActionButton>(R.id.fabShareSnapshot)
        fabShare.isVisible = url.isNotBlank()
        fabShare.setOnClickListener {
            if (url.isBlank()) return@setOnClickListener
            val title = label?.trim().orEmpty().ifEmpty { getString(R.string.share_snapshot) }
            val text = buildString {
                append(title)
                append("\n\n")
                append(url)
            }
            ShareCompat.IntentBuilder(this)
                .setType("text/plain")
                .setChooserTitle(getString(R.string.share_snapshot))
                .setText(text)
                .startChooser()
        }

        val fabDelete = findViewById<FloatingActionButton>(R.id.fabDelete)
        if (id.isNullOrBlank()) {
            fabDelete.hide()
        }
        fabDelete.setOnClickListener {
            val sid = id ?: return@setOnClickListener
            lifecycleScope.launch {
                try {
                    snapshotRepo.deleteSnapshot(sid)
                    setResult(RESULT_OK, Intent().putExtra(IntentExtras.SNAPSHOT_DELETED, true))
                    finish()
                } catch (e: Exception) {
                    Toast.makeText(this@SnapshotViewerActivity, e.message ?: "Delete failed", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
