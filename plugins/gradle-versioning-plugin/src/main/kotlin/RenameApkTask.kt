import com.android.build.api.artifact.ArtifactTransformationRequest
import com.android.build.api.variant.BuiltArtifact
import org.gradle.api.DefaultTask
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkerExecutor
import java.io.File
import java.io.Serializable
import javax.inject.Inject

interface CopyWorkItemParams : WorkParameters, Serializable {
    val inputApkFile: RegularFileProperty
    val outputApkFile: RegularFileProperty
}

abstract class CopyWorkItem @Inject constructor(
    private val params: CopyWorkItemParams
) : WorkAction<CopyWorkItemParams> {
    override fun execute() {
        params.outputApkFile.get().asFile.delete()
        params.inputApkFile.asFile.get().copyTo(params.outputApkFile.get().asFile)
    }
}

abstract class RenameApkTask @Inject constructor(
    private val workers: WorkerExecutor
) : DefaultTask() {

    @get:InputFiles
    abstract val inputDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Internal
    abstract val transformationRequest: Property<ArtifactTransformationRequest<RenameApkTask>>

    @get:Input
    abstract val newFileName: Property<String>

    @TaskAction
    fun taskAction() {
        transformationRequest.get().submit(
            this,
            workers.noIsolation(),
            CopyWorkItem::class.java
        ) { builtArtifact: BuiltArtifact, outputLocation: Directory, param: CopyWorkItemParams ->
            val inputFile = File(builtArtifact.outputFile)
            param.inputApkFile.set(inputFile)
            val outputFile = File(outputLocation.asFile, "${newFileName.get()}.apk")
            param.outputApkFile.set(outputFile)
            outputFile
        }
    }
}
