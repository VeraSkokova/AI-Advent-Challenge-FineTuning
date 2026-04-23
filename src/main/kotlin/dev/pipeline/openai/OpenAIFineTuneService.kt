package dev.pipeline.openai

import com.aallam.openai.api.core.Status
import com.aallam.openai.api.file.FileId
import com.aallam.openai.api.file.FileSource
import com.aallam.openai.api.file.FileUpload
import com.aallam.openai.api.file.Purpose
import com.aallam.openai.api.finetuning.FineTuningId
import com.aallam.openai.api.finetuning.FineTuningRequest
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import kotlinx.coroutines.delay
import kotlinx.io.files.Path
import kotlin.time.Duration.Companion.seconds

class OpenAIFineTuneService(private val client: OpenAI) {

    suspend fun uploadTrainingFile(filePath: String): FileId {
        val file = java.io.File(filePath)
        require(file.exists()) { "Training file not found: $filePath" }

        println("  Uploading training file: $filePath")

        val fileUpload = FileUpload(
            file = FileSource(path = Path(filePath)),
            purpose = Purpose("fine-tune"),
        )

        val uploadedFile = client.file(fileUpload)
        println("  File uploaded. ID: ${uploadedFile.id.id}")
        return uploadedFile.id
    }

    suspend fun createFineTuningJob(
        trainingFileId: FileId,
        model: String = "gpt-4o-mini-2024-07-18",
    ): FineTuningId {
        println("  Creating fine-tuning job with model: $model")

        val request = FineTuningRequest(
            trainingFile = trainingFileId,
            model = ModelId(model),
        )

        val job = client.fineTuningJob(request)
        println("  Fine-tuning job created. ID: ${job.id.id}")
        return job.id
    }

    suspend fun pollJobStatus(jobId: FineTuningId): String {
        println("  Polling job status for: ${jobId.id}")

        while (true) {
            val job = client.fineTuningJob(jobId)
                ?: error("Fine-tuning job not found: ${jobId.id}")
            val status = job.status

            println("  Status: $status")

            when (status) {
                Status.Succeeded,
                Status.Failed,
                Status.Cancelled,
                -> {
                    val fineTunedModel = job.fineTunedModel?.id ?: "(none)"
                    println("  Job finished. Fine-tuned model: $fineTunedModel")
                    return status.value
                }
                else -> {
                    println("  Job still in progress. Waiting 30s...")
                    delay(30.seconds)
                }
            }
        }
    }
}
