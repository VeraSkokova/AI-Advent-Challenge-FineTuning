## Project Description
This project is an interactive console-based Kotlin application for an LLM fine-tuning preparation pipeline. It handles dataset validation, baseline evaluation using local models, and automating fine-tuning jobs via the OpenAI API.

## Tech Stack & Architecture
- **Language:** Kotlin
- **Build Tool:** Gradle (Kotlin DSL - `build.gradle.kts`)
- **App Interface:** Interactive CLI text menu loop (using standard input).
- **Libraries:**
    - `org.jetbrains.kotlinx:kotlinx-serialization-json` for JSON manipulation.
    - `com.aallam.openai:openai-client` for ALL LLM interactions.
    - `JUnit 5` and `io.mockk:mockk` for unit testing.

## Package Structure
All source code must be placed under `src/main/kotlin/dev/pipeline` with the following modular structure:
- `cli`: For the main entry point and the interactive menu loop.
- `validation`: For JSONL dataset validation logic.
- `baseline`: For Ollama baseline evaluation logic.
- `openai`: For the Fine-Tuning API client and related services.
  *Note: Test files should mirror this structure under `src/test/kotlin/dev/pipeline/`.*

## Context (Week Theory)
When solving tasks, rely on the following principles for dataset preparation:
1. **Garbage in, garbage out**: Data quality is more important than quantity. Remove duplicates, empty lines, and overly long/short texts.
2. **OpenAI Format**: Each JSONL line must contain a `messages` array with strict roles: `system` (behavior), `user` (input data), and `assistant` (reference/ideal response).
3. **Overfitting**: When generating synthetic data and training, ensure the model learns the underlying structure rather than memorizing patterns word-for-word. To achieve this, split the dataset into `train` (80%) and `eval` (20%).

## Project Commands
- Build the project: `./gradlew build`
- Run the validator (when the application plugin is configured): `./gradlew run`

## Coding Guidelines
- Write idiomatic Kotlin code.
- When working with files, always check for their existence and handle exceptions properly (e.g., try/catch for JSON parsing).
- Log API request statuses (especially when polling the status of a Fine-tuning job).

## Key Implementation Details
1. **Baseline with Ollama:** We use local models (via Ollama) to measure the baseline. Ollama provides an OpenAI-compatible API at `http://localhost:11434/v1/`. Configure the `aallam/openai-kotlin` client to point to this local host for baseline evaluations to save costs and keep data local.
2. **File Output:** Baseline evaluation results must be saved to a text file (e.g., `baseline_results.txt`) for later review, rather than just printing to the console.
3. **Testing:** All external API calls in the Fine-Tuning client must be mocked using `MockK`. Never make real HTTP requests to OpenAI during automated tests to avoid spending API credits.