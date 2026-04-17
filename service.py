import os

os.environ["TRANSFORMERS_OFFLINE"] = "1"
os.environ["HF_DATASETS_OFFLINE"] = "1"
os.environ["OMP_NUM_THREADS"] = "8"
os.environ["MKL_NUM_THREADS"] = "4"

from gliner import GLiNER
from FlagEmbedding import BGEM3FlagModel
from langchain_text_splitters import RecursiveCharacterTextSplitter
from transformers import AutoTokenizer
from pydantic import BaseModel
from typing import List, Dict
import torch
import numpy as np
from fastapi import FastAPI
from contextlib import asynccontextmanager
import uvicorn

app = FastAPI()

@app.get("/")
def read_root():
    return {"message": "Сервер запущен..."}

class Entity(BaseModel):
    label: str
    text: str
    global_start: int 
    global_end: int
    score: float

class SearchResult(BaseModel):
    doc_id: str
    chunk_id: int               # порядковый номер чанка
    start_char_idx: int         # с какого символа оригинала начинается чанк
    end_char_idx: int           # каким символом заканчивается
    text: str                   
    entities: List[Entity]      
    dense_vecs: List[float]
    lexical_weights: Dict[str, float]

class ProcessRequest(BaseModel):
    doc_id: str
    text: str
    labels: List[str]

class SearchProcessor:
    def __init__(self, models_dir = "./local_models"):
        self.device = "cuda" if torch.cuda.is_available() else "cpu"
        self.use_fp16 = True if self.device == "cuda" else False
        if self.device == "cpu":
            torch.set_num_threads(8)

        print("Загрузка GLiNER...")
        self.gliner = GLiNER.from_pretrained(
            f"{models_dir}/gliner-multi",
            local_files_only=True
        ).to(self.device) 

        print("Загрузка bge-m3...")
        self.bge = BGEM3FlagModel(
            f"{models_dir}/bge-m3", 
            use_fp16=self.use_fp16,
            device=self.device
        )


        self.tokenizer = self.bge.tokenizer


    def chunk_split(self, text, chunk_size=512, overlap=64):
        text_splitter = RecursiveCharacterTextSplitter.from_huggingface_tokenizer(
            self.tokenizer,
            chunk_size=chunk_size,
            chunk_overlap=overlap,
            is_separator_regex=False,
            separators=["\n\n", "\n", " ", ""],
            keep_separator=True,
            strip_whitespace=True,
            add_start_index=True
        )

        docs = text_splitter.create_documents([text])
        return docs



    def process_document(self, doc_id, text, labels, gliner_threshold: float = 0.4, sparse_top_k: int = 100):

        docs = self.chunk_split(text)
        
        if not docs:
            return []

        chunk_texts = [doc.page_content for doc in docs]
        # bge_texts = [f"Документ: {doc_title}. {t}" if doc_title else t for t in chunk_texts]

        all_entities_batch = self.gliner.batch_predict_entities(
            chunk_texts,
            labels,
            threshold = gliner_threshold)
        
        
        vector_output = self.bge.encode(
                chunk_texts, 
                return_dense=True, 
                return_sparse=True, 
                # return_colbert_vecs=True # требует много памяти в Elastic
            )

        dense_numpy = vector_output['dense_vecs'] 
        norms = np.linalg.norm(dense_numpy, axis=1, keepdims=True)
        norms[norms == 0] = 1 
        dense_vecs_normalized = (dense_numpy / norms).tolist()

        sparse_vecs_batch = vector_output['lexical_weights']
        
        results = []
        for i, (doc, chunk_entities, dense_vec, sparse_vec) in enumerate(zip(
            docs, all_entities_batch, dense_vecs_normalized, sparse_vecs_batch
        )):
            
            chunk_start = doc.metadata['start_index']
            chunk_text = doc.page_content
            chunk_end = chunk_start + len(chunk_text)

            decoded_lexical_weights = {}

            sorted_weights = sorted(sparse_vec.items(), key=lambda item: item[1], reverse=True)[:sparse_top_k]
                        for tid_str, w in sorted_weights:
                safe_token_key = f"tok_{tid_str}" 
                decoded_lexical_weights[safe_token_key] = round(float(w), 4)

            processed_entities = [
                Entity(
                    label=ent["label"],
                    text=ent["text"],
                    global_start=chunk_start + ent["start"],
                    global_end=chunk_start + ent["end"],
                    score=round(ent["score"], 4)
                ) for ent in chunk_entities
            ]

            results.append(SearchResult.model_construct(
                doc_id= doc_id,
                chunk_id=i,
                start_char_idx=chunk_start,
                end_char_idx=chunk_end,
                text=chunk_text,
                entities=processed_entities,
                dense_vecs=dense_vec,
                lexical_weights= decoded_lexical_weights
            ))

        return results

processor = None
@asynccontextmanager
async def lifespan(app: FastAPI):
    global processor
    print("Инициализация моделей ИИ. Пожалуйста, подождите...")
    processor = SearchProcessor() 
    print("Модели успешно загружены!")
    yield
    print("Выключение сервера, освобождение ресурсов...")
    processor = None

app = FastAPI(lifespan=lifespan)

@app.get("/")
def read_root():
    return {"message": "Сервер запущен и модели загружены!"}

@app.post("/process", response_model=List[SearchResult])
def process_text(request: ProcessRequest):
    """
    Принимает текст, бьет на чанки, извлекает сущности (NER) и векторы (Dense + Sparse).
    """
    results = processor.process_document(
        doc_id=request.doc_id, 
        text=request.text, 
        labels=request.labels
    )
    return results

if __name__ == "__main__":
    uvicorn.run("main:app", host="0.0.0.0", port=8000, reload=False)