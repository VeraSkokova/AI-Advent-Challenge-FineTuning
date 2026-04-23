package dev.pipeline.openai

import com.aallam.openai.api.core.Status
import com.aallam.openai.api.file.FileId
import com.aallam.openai.api.file.FileUpload
import com.aallam.openai.api.finetuning.FineTuningId
import com.aallam.openai.api.finetuning.FineTuningJob
import com.aallam.openai.api.finetuning.FineTuningRequest
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File

class OpenAIFineTuneServiceTest {

    private lateinit var mockClient: OpenAI
    private lateinit var service: OpenAIFineTuneService

    @BeforeEach
    fun setUp() {
        mockClient = mockk(relaxed = true)
        service = OpenAIFineTuneService(mockClient)
    }

    @Test
    fun `uploadTrainingFile uploads file and returns FileId`() = runTest {
        val expectedFileId = FileId("file-abc123")
        val mockFile = mockk<com.aallam.openai.api.file.File>(relaxed = true) {
            coEvery { id } returns expectedFileId
        }

        coEvery { mockClient.file(any<FileUpload>()) } returns mockFile

        val tempFile = File.createTempFile("train_test", ".jsonl")
        tempFile.writeText("""{"messages": []}""")
        tempFile.deleteOnExit()

        val result = service.uploadTrainingFile(tempFile.absolutePath)

        assertEquals(expectedFileId, result)
        coVerify(exactly = 1) { mockClient.file(any<FileUpload>()) }
    }

    @Test
    fun `uploadTrainingFile throws when file does not exist`() = runTest {
        assertThrows<IllegalArgumentException> {
            service.uploadTrainingFile("nonexistent_file.jsonl")
        }
    }

    @Test
    fun `createFineTuningJob creates job and returns FineTuningId`() = runTest {
        val fileId = FileId("file-abc123")
        val expectedJobId = FineTuningId("ftjob-xyz789")
        val mockJob = mockk<FineTuningJob>(relaxed = true) {
            coEvery { id } returns expectedJobId
        }

        coEvery { mockClient.fineTuningJob(any<FineTuningRequest>()) } returns mockJob

        val result = service.createFineTuningJob(fileId)

        assertEquals(expectedJobId, result)
        coVerify(exactly = 1) { mockClient.fineTuningJob(any<FineTuningRequest>()) }
    }

    @Test
    fun `createFineTuningJob uses custom model`() = runTest {
        val fileId = FileId("file-abc123")
        val expectedJobId = FineTuningId("ftjob-custom")
        val mockJob = mockk<FineTuningJob>(relaxed = true) {
            coEvery { id } returns expectedJobId
        }

        coEvery { mockClient.fineTuningJob(any<FineTuningRequest>()) } returns mockJob

        val result = service.createFineTuningJob(fileId, model = "gpt-4o-2024-08-06")

        assertEquals(expectedJobId, result)
    }

    @Test
    fun `pollJobStatus returns succeeded when job completes`() = runTest {
        val jobId = FineTuningId("ftjob-xyz789")
        val mockJob = mockk<FineTuningJob>(relaxed = true) {
            coEvery { status } returns Status.Succeeded
            coEvery { fineTunedModel } returns ModelId("ft:gpt-4o-mini:org:custom:id")
        }

        coEvery { mockClient.fineTuningJob(jobId) } returns mockJob

        val result = service.pollJobStatus(jobId)

        assertEquals("succeeded", result)
        coVerify(exactly = 1) { mockClient.fineTuningJob(jobId) }
    }

    @Test
    fun `pollJobStatus returns failed when job fails`() = runTest {
        val jobId = FineTuningId("ftjob-fail")
        val mockJob = mockk<FineTuningJob>(relaxed = true) {
            coEvery { status } returns Status.Failed
            coEvery { fineTunedModel } returns null
        }

        coEvery { mockClient.fineTuningJob(jobId) } returns mockJob

        val result = service.pollJobStatus(jobId)

        assertEquals("failed", result)
    }

    @Test
    fun `pollJobStatus returns cancelled when job is cancelled`() = runTest {
        val jobId = FineTuningId("ftjob-cancel")
        val mockJob = mockk<FineTuningJob>(relaxed = true) {
            coEvery { status } returns Status.Cancelled
            coEvery { fineTunedModel } returns null
        }

        coEvery { mockClient.fineTuningJob(jobId) } returns mockJob

        val result = service.pollJobStatus(jobId)

        assertEquals("cancelled", result)
    }
}
