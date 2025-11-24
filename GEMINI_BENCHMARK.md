# Gemini 벤치마크 실행 가이드 (GEMINI_BENCHMARK.md)

이 문서는 Gemini 모델을 사용하여 `SimpleQA`, `BrowseComp` 등의 벤치마크를 실행하고, 시스템 파라미터를 최적화하는 방법을 안내합니다.

## 1. 개요

이 프로젝트는 `ldr-benchmark` 라는 CLI(Command Line Interface) 도구를 통해 다양한 벤치마크를 실행하고 성능을 측정합니다. 또한, Optuna와 같은 도구를 활용하여 주어진 조건(품질, 속도, 효율성 등)에 가장 적합한 시스템 파라미터를 찾는 최적화 기능을 지원합니다.

### 주요 기능

-   **벤치마크 실행**: `SimpleQA`, `BrowseComp` 벤치마크를 개별적 또는 동시에 실행할 수 있습니다.
-   **파라미터 최적화**: `iterations`, `questions_per_iteration`, `search_strategy` 등 주요 하이퍼파라미터를 자동으로 탐색하여 최적의 조합을 찾습니다.
-   **복합 목표 최적화**: 단순히 품질뿐만 아니라, `품질(Quality)`, `속도(Speed)`, `자원 사용량(Efficiency)` 등 여러 목표에 가중치를 부여하여 복합적인 최적화를 수행할 수 있습니다.
-   **결과 저장**: 모든 벤치마크 및 최적화 실행 결과는 타임스탬프 기반의 고유한 디렉토리에 저장되어 이력을 관리하기 용이합니다.

--- 

## 2. 사용 방법

### 2.1. 기본 벤치마크 실행 (CLI)

`ldr-benchmark` 명령어를 사용하여 터미널에서 직접 벤치마크를 실행할 수 있습니다.

**기본 명령어 형식:**

```bash
ldr-benchmark <command> [options]
```

**주요 인수 (Arguments):**

| 인수 | 설명 | 필수 여부 | 기본값 | 
| :--- | :--- | :--- | :--- | 
| `run` | 벤치마크 실행 명령어입니다. | 예 | - | 
| `--api-key` | OpenRouter API 키입니다. | 예 | - | 
| `--simpleqa` | `SimpleQA` 벤치마크를 실행합니다. | 아니요 | - | 
| `--browsecomp` | `BrowseComp` 벤치마크를 실행합니다. | 아니요 | - | 
| `--examples` | 각 벤치마크에서 실행할 예제 개수입니다. | 아니요 | `3` | 
| `--iterations` | 검색 반복 횟수입니다. | 아니요 | `2` | 
| `--questions` | 반복 당 생성할 질문 개수입니다. | 아니요 | `3` | 
| `--search-tool` | 사용할 검색 엔진입니다. | 아니요 | `searxng` | 
| `--search-strategy`| 사용할 검색 전략입니다. | 아니요 | `source_based` | 
| `--verbose` | 상세 로깅을 활성화합니다. | 아니요 | - | 

**실행 예시:**

```bash
# SimpleQA와 BrowseComp 벤치마크를 각각 10개의 예제로 실행
ldr-benchmark run \
    --api-key "YOUR_OPENROUTER_API_KEY" \
    --simpleqa \
    --browsecomp \
    --examples 10
```

### 2.2. 파라미터 최적화 실행

파라미터 최적화는 별도의 Python 스크립트(예: `run_optimization_examples.py`)를 통해 실행됩니다. 최적화 과정에서는 정의된 파라미터 공간(`param_space`) 내에서 여러 번의 시험(`n_trials`)을 통해 최적의 값을 찾습니다.

**주요 최적화 함수:**

-   `optimize_parameters()`: 기본적인 품질 점수를 기준으로 최적화를 수행합니다.
-   `optimize_for_quality()`: 품질에 중점을 둔 최적화를 수행합니다.
-   `optimize_for_speed()`: 품질과 속도를 함께 고려하되, 속도에 더 높은 가중치를 둡니다. (예: 품질 20%, 속도 80%)
-   `optimize_for_efficiency()`: 품질, 속도, 자원 사용량의 균형을 맞추는 최적화를 수행합니다. (예: 품질 40%, 속도 30%, 자원 30%)

**`benchmark_weights`를 이용한 복합 벤치마크 최적화:**

여러 벤치마크의 결과를 조합하여 종합 점수를 기준으로 최적화할 수 있습니다. 예를 들어, `SimpleQA`에 60%, `BrowseComp`에 40%의 가중치를 부여할 수 있습니다.

```python
# 최적화 스크립트 내 예시
from local_deep_research.benchmarks.optimization.api import optimize_parameters

# 60/40 가중치로 최적화 실행
best_params, best_score = optimize_parameters(
    query="Recent advancements in renewable energy",
    n_trials=20,  # 실제 최적화 시에는 20 이상을 권장
    output_dir="optimization_results/weighted_combo",
    benchmark_weights={
        "simpleqa": 0.6,
        "browsecomp": 0.4,
    },
)
```

--- 

## 3. 핵심 구성 요소

-   **CLI Entrypoint (`ldr_benchmark/cli.py`)**: `argparse`를 사용하여 커맨드 라인 인수를 파싱하고, `run_benchmark` 함수를 호출하는 진입점입니다.
-   **Benchmark Runner (`run_benchmark`, `run_benchmarks`)**: 입력된 설정에 따라 선택된 벤치마크(`evaluate_simpleqa`, `evaluate_browsecomp` 등)를 순차적으로 실행하고 결과를 취합 및 요약하는 핵심 로직입니다.
-   **Optimization API (`optimization/api.py`)**: `optimize_for_*` 함수들을 제공하여, 다양한 목표에 맞춰 파라미터 최적화를 수행할 수 있는 API를 제공합니다. `CompositeBenchmarkEvaluator`를 사용하여 여러 벤치마크의 점수를 가중합산합니다.
-   **Evaluators (`evaluators.py`)**: 각 벤치마크의 성능을 평가하고 점수를 계산하는 로직이 포함되어 있습니다.

--- 

## 4. 결과물 (Outputs)

벤치마크 또는 최적화 실행 시 다음과 같은 결과물이 생성됩니다.

1.  **콘솔 출력**: 실행 과정, 각 벤치마크의 진행 상황, 중간 결과 및 최종 요약이 콘솔에 출력됩니다.
    ```
    === Running SimpleQA benchmark with 10 examples ===
    SimpleQA evaluation complete in 120.5 seconds
    SimpleQA accuracy: 0.8750

    === Benchmark Summary ===
    Model: google/gemini-flash-1.5
    Provider: openrouter
    Examples: 10
    Results saved to: examples/benchmarks/results/gemini_20241125_103000
    ```

2.  **결과 디렉토리**: 모든 로그, 중간 산출물, 최종 결과는 타임스탬프가 포함된 디렉토리에 저장됩니다.
    -   예시: `examples/benchmarks/results/gemini_20241125_103000/`

3.  **JSON 요약 파일 (`benchmark_summary.json`)**: 실행 설정과 최종 결과를 담은 JSON 파일이 결과 디렉토리 내에 생성되어 기계적인 처리를 용이하게 합니다.

    **`benchmark_summary.json` 예시:**
    ```json
    {
      "timestamp": "20241125_103000",
      "model": "google/gemini-flash-1.5",
      "provider": "openrouter",
      "examples": 10,
      "benchmarks": ["simpleqa", "browsecomp"],
      "results": {
        "simpleqa": {
          "accuracy": 0.875,
          "duration": 120.5,
          "error": null
        },
        "browsecomp": {
          "accuracy": 0.750,
          "duration": 350.2,
          "error": null
        }
      }
    }
    ```

