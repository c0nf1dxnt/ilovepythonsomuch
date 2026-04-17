# Запуск MVP

## Структура

```
hackathon/
├── docker-compose.yml      # qdrant + python-service + java-backend
├── python-service/         # FastAPI: /health, /prepare_index, /prepare_query
│   ├── app/
│   │   ├── main.py         # эндпоинты
│   │   ├── processor.py    # GLiNER + BGE-M3 (адаптировано из service.py)
│   │   ├── chunker.py      # message-based chunking (8-12 сообщений, overlap 2-3)
│   │   └── models.py       # Pydantic
│   ├── Dockerfile
│   └── requirements.txt
└── java-backend/           # Spring Boot: /health, /index, /search
    ├── src/main/java/com/hackathon/search/
    │   ├── SearchApplication.java
    │   ├── controller/SearchController.java
    │   ├── service/SearchService.java      # оркестрация
    │   ├── service/PythonClient.java       # http → python
    │   ├── service/QdrantService.java      # http → qdrant
    │   ├── model/                           # DTO
    │   └── config/AppConfig.java
    ├── pom.xml
    └── Dockerfile
```

## Поднять стенд

```bash
docker compose up --build
```

Сервисы:
- Java API: http://localhost:8080
- Python ML: http://localhost:8000
- Qdrant: http://localhost:6333

Первый запуск долгий: Python сервис скачивает модели `BAAI/bge-m3` (~2GB) и `urchade/gliner_multi-v2.1` (~900MB) из Hugging Face Hub в volume `hf_cache`. Если модели уже лежат локально — положи их в `python-service/local_models/{bge-m3,gliner-multi}` и раскомментируй `MODELS_DIR` в docker-compose.yml.

## Эндпоинты Java (внешний API)

### GET /health

```bash
curl http://localhost:8080/health
# {"status":"ok"}
```

### POST /index

```bash
curl -X POST http://localhost:8080/index \
  -H 'Content-Type: application/json' \
  -d '{
    "chat": {"id": "chat-1", "name": "General", "type": "group"},
    "messages": [
      {"id": "m1", "text": "Я выложил фичу на staging", "time": 1713000000, "sender_id": "bob"},
      {"id": "m2", "text": "Проверил, всё ок", "time": 1713000060, "sender_id": "alice"},
      {"id": "m3", "text": "Можно мержить?", "time": 1713000120, "sender_id": "bob"}
    ]
  }'
# {"status":"ok","indexed_chunks":1}
```

### POST /search

```bash
curl -X POST http://localhost:8080/search \
  -H 'Content-Type: application/json' \
  -d '{
    "text": "готов ли фича к релизу",
    "keywords": ["фича", "staging", "релиз"],
    "top_k": 3
  }'
```

## Что внутри

**Индексация** (`/index`):
1. Java принимает `{chat, messages}`
2. Отправляет в Python `/prepare_index`
3. Python: фильтрует мусор (is_system / is_hidden / пустые) → режет на chunk'и по сообщениям (8-12 msg, overlap 2) → GLiNER NER → BGE-M3 dense + sparse векторы (нормализованные)
4. Java: апсертит точки в Qdrant с payload `{chat_id, chunk_id, message_ids, chunk_text, sender_ids, time_from/to, entities, lexical_weights}`

**Поиск** (`/search`):
1. Java принимает вопрос (text, search_text, variants, hyde, keywords, entities, ...)
2. Отправляет в Python `/prepare_query` — там текст собирается, нормализуется в lowercase, получаем dense vector
3. Java: cosine search в Qdrant по dense vector (кандидаты = top_k × 3)
4. Rerank: лексический буст за совпадение keywords/entities в `chunk_text` (+0.05 за каждое совпадение, до +0.25)
5. Возвращаем top_k с `{chunk_id, chat_id, message_ids, text, score}`

## TODO для улучшения Score

- Hybrid search: использовать sparse/lexical_weights Qdrant'а (named vectors) — сейчас храним, но не ищем по ним
- Rerank через cross-encoder (например, bge-reranker-v2-m3)
- Фильтр по `date_range` и `asker` через Qdrant filter
- Тюнинг `chunk_size` / `overlap` по данным хакатона
- Использовать colbert-vectors BGE-M3 для reranking (требует памяти)
