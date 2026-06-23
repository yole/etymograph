package page.yole.etymograph.web

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ResetCommand
import org.eclipse.jgit.api.Status
import org.eclipse.jgit.transport.RemoteRefUpdate
import java.io.File
import kotlin.use

object GraphGitService {
    fun sync(workTree: File) {
        Git.open(workTree).use { git ->
            val status = git.status().call()
            val unversionedPaths = status.unversionedPaths()
            if (unversionedPaths.isNotEmpty()) {
                val add = git.add()
                unversionedPaths.forEach(add::addFilepattern)
                add.call()
            }
            git.add().addFilepattern(".").setUpdate(true).call()
            git.commit().setMessage("Sync changes").call()
            val pushResults = git.push().call()
            val failedUpdates = pushResults
                .flatMap { it.remoteUpdates }
                .filter { it.status !in successfulPushStatuses }
            if (failedUpdates.isNotEmpty()) {
                val details = failedUpdates.joinToString(", ") { update ->
                    "${update.remoteName}: ${update.status}"
                }
                throw IllegalStateException("Failed to push changes: $details")
            }
        }
    }

    fun status(workTree: File): String {
        return runCatching {
            Git.open(workTree).use { git ->
                val status = git.status().call()
                val changedFiles = buildSet {
                    addAll(status.added)
                    addAll(status.changed)
                    addAll(status.modified)
                    addAll(status.unversionedPaths())
                }
                "${changedFiles.size} changed files"
            }
        }.getOrDefault("")
    }

    fun revert(workTree: File) {
        Git.open(workTree).use { git ->
            git.reset().setMode(ResetCommand.ResetType.HARD).call()
            git.clean().setCleanDirectories(true).call()
        }
    }

    fun clone(repoUrl: String, clonePath: File) {
        Git.cloneRepository()
            .setURI(repoUrl)
            .setDirectory(clonePath)
            .call()
            .close()
    }

    private fun Status.unversionedPaths(): Set<String> {
        val untrackedFiles = untracked
        return buildSet {
            addAll(untrackedFiles)
            addAll(untrackedFolders.filter { folder ->
                untrackedFiles.none { file -> file == folder || file.startsWith("$folder/") }
            })
        }
    }

    private val successfulPushStatuses = setOf(
        RemoteRefUpdate.Status.OK,
        RemoteRefUpdate.Status.UP_TO_DATE,
        RemoteRefUpdate.Status.NON_EXISTING
    )
}