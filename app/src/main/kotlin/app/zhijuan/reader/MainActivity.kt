package app.zhijuan.reader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import app.zhijuan.data.s0.FileS0NovelRepository
import app.zhijuan.data.s0.FileS3GenerationJobStore
import app.zhijuan.data.s0.S4ProjectArchive
import app.zhijuan.data.s0.provider.OpenAiCompatibleS1Provider
import app.zhijuan.core.s0.S3RecoveryAuditor
import java.io.File

class MainActivity : ComponentActivity() {
    private val projectsRoot by lazy { File(filesDir, "zhijuan-projects") }
    private val repository by lazy { FileS0NovelRepository(projectsRoot) }
    private val jobStore by lazy { FileS3GenerationJobStore(projectsRoot) }
    private val provider by lazy { OpenAiCompatibleS1Provider.forApplication(this) }
    private val projectArchive by lazy { S4ProjectArchive(projectsRoot) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository.recoverPendingCommits()
        val recoveryAuditor = S3RecoveryAuditor(repository, jobStore)
        val recoveryDecisions = repository.listProjects().associate { snapshot ->
            snapshot.project.id to recoveryAuditor.audit(snapshot.project.id)
        }
        setContent {
            ZhijuanS0App(
                repository = repository,
                provider = provider,
                generationController = S3GenerationController(this),
                initialRecoveryDecisions = recoveryDecisions,
                projectArchive = projectArchive,
            )
        }
    }
}
