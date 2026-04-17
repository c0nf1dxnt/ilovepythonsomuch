import os
import uuid
from contextlib import asynccontextmanager
from typing import List, Optional

os.environ.setdefault("OMP_NUM_THREADS", "8")
os.environ.setdefault("MKL_NUM_THREADS", "4")

from fastapi import FastAPI, HTTPException
import uvicorn

from .chunker import build_chunks
from .models import (
    Entity,
    PreparedChunk,
    PrepareIndexRequest,
    PrepareIndexResponse,
    PrepareQueryRequest,
    PrepareQueryResponse,
    QueryEntities,
)
from .processor import SearchProcessor


processor: Optional[SearchProcessor] = None


@asynccontextmanager
async def lifespan(_: FastAPI):
    global processor
    models_dir = os.getenv("MODELS_DIR")
    print("Инициализация моделей ИИ...")
    processor = SearchProcessor(models_dir=models_dir)
    print("Модели успешно загружены.")
    yield
    processor = None


app = FastAPI(title="Search ML preprocessing service", lifespan=lifespan)


@app.get("/health")
def health():
    ready = processor is not None
    return {"status": "ok" if ready else "loading", "ready": ready}


@app.post("/prepare_index", response_model=PrepareIndexResponse)
def prepare_index(req: PrepareIndexRequest):
    if processor is None:
        raise HTTPException(status_code=503, detail="Models are not loaded yet")

    all_messages = list(req.messages)
    if req.overlap_messages:
        all_messages = list(req.overlap_messages) + all_messages
    if req.new_messages:
        all_messages.extend(req.new_messages)

    chunks = build_chunks(
        all_messages,
        chunk_size=req.chunk_size,
        overlap=req.chunk_overlap,
    )

    if not chunks:
        return PrepareIndexResponse(chunks=[])

    texts = [c.text for c in chunks]
    entities_batch, dense_vecs, sparse_vecs = processor.encode(
        texts,
        labels=req.labels,
    )

    prepared: List[PreparedChunk] = []
    for chunk, ents, dense, sparse in zip(chunks, entities_batch, dense_vecs, sparse_vecs):
        chunk_entities = [
            Entity(
                label=e["label"],
                text=e["text"],
                start=int(e["start"]),
                end=int(e["end"]),
                score=round(float(e["score"]), 4),
            )
            for e in ents
        ]
        prepared.append(PreparedChunk(
            chat_id=req.chat.id,
            chunk_id=str(uuid.uuid4()),
            message_ids=chunk.message_ids,
            chunk_text=chunk.text,
            sender_ids=chunk.sender_ids,
            time_from=chunk.time_from,
            time_to=chunk.time_to,
            entities=chunk_entities,
            dense_vec=dense,
            lexical_weights=sparse,
        ))

    return PrepareIndexResponse(chunks=prepared)


def _build_query_text(req: PrepareQueryRequest) -> str:
    parts: List[str] = []
    primary = req.search_text or req.text or ""
    if primary:
        parts.append(primary.strip())

    for v in (req.variants or [])[:3]:
        if v:
            parts.append(v.strip())

    for h in (req.hyde or [])[:2]:
        if h:
            parts.append(h.strip())

    if req.keywords:
        parts.append(" ".join(k for k in req.keywords if k))

    ents = req.entities
    if ents:
        for field in ("people", "emails", "documents", "names", "links"):
            values = getattr(ents, field, None) or []
            if values:
                parts.append(" ".join(str(v) for v in values if v))

    joined = " ".join(p for p in parts if p)
    return joined.lower().strip()


@app.post("/prepare_query", response_model=PrepareQueryResponse)
def prepare_query(req: PrepareQueryRequest):
    if processor is None:
        raise HTTPException(status_code=503, detail="Models are not loaded yet")

    normalized = _build_query_text(req)
    if not normalized:
        raise HTTPException(status_code=400, detail="Query text is empty")

    _, dense_vecs, sparse_vecs = processor.encode(
        [normalized],
        extract_entities=False,
    )

    return PrepareQueryResponse(
        normalized_text=normalized,
        dense_vec=dense_vecs[0],
        lexical_weights=sparse_vecs[0],
        keywords=req.keywords or [],
        entities=req.entities or QueryEntities(),
    )


if __name__ == "__main__":
    uvicorn.run("app.main:app", host="0.0.0.0", port=8000, reload=False)
