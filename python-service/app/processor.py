import os
from typing import Dict, List, Tuple

import numpy as np
import torch

from FlagEmbedding import BGEM3FlagModel
from gliner import GLiNER
from huggingface_hub import snapshot_download


DEFAULT_LABELS = [
    "person",
    "organization",
    "document",
    "email",
    "url",
    "date",
    "product",
    "location",
]


class SearchProcessor:
    def __init__(
        self,
        models_dir: str = None,
        gliner_name: str = "urchade/gliner_multi-v2.1",
        bge_name: str = "BAAI/bge-m3",
    ):
        self.device = "cuda" if torch.cuda.is_available() else "cpu"
        self.use_fp16 = self.device == "cuda"
        if self.device == "cpu":
            torch.set_num_threads(int(os.getenv("OMP_NUM_THREADS", "4")))

        if models_dir and os.path.isdir(f"{models_dir}/gliner-multi"):
            gliner_path = f"{models_dir}/gliner-multi"
            gliner_kwargs = {"local_files_only": True}
        else:
            gliner_path = gliner_name
            gliner_kwargs = {}

        if models_dir and os.path.isdir(f"{models_dir}/bge-m3"):
            bge_path = f"{models_dir}/bge-m3"
        else:
            # FlagEmbedding calls snapshot_download() for remote repo IDs and
            # pulls the whole repo, including the huge optional ONNX artifact
            # `onnx/model.onnx_data`. We only use the PyTorch weights via
            # AutoModel on CPU, so we prefetch a local snapshot without ONNX.
            bge_path = snapshot_download(
                repo_id=bge_name,
                ignore_patterns=[
                    "onnx/*",
                    "flax_model.msgpack",
                    "rust_model.ot",
                    "tf_model.h5",
                ],
            )

        print(f"Загрузка GLiNER из {gliner_path}...")
        self.gliner = GLiNER.from_pretrained(gliner_path, **gliner_kwargs).to(self.device)

        print(f"Загрузка bge-m3 из {bge_path}...")
        self.bge = BGEM3FlagModel(bge_path, use_fp16=self.use_fp16, device=self.device)

        self.dense_dim = 1024

    def encode(
        self,
        texts: List[str],
        labels: List[str] = None,
        gliner_threshold: float = 0.4,
        sparse_top_k: int = 100,
        extract_entities: bool = True,
    ) -> Tuple[List[list], List[list], List[Dict[str, float]]]:
        if not texts:
            return [], [], []

        labels = labels or DEFAULT_LABELS

        if extract_entities:
            entities_batch = self.gliner.batch_predict_entities(
                texts, labels, threshold=gliner_threshold
            )
        else:
            entities_batch = [[] for _ in texts]

        vector_output = self.bge.encode(
            texts,
            return_dense=True,
            return_sparse=True,
        )

        dense_numpy = np.asarray(vector_output["dense_vecs"])
        norms = np.linalg.norm(dense_numpy, axis=1, keepdims=True)
        norms[norms == 0] = 1
        dense_vecs = (dense_numpy / norms).tolist()

        sparse_vecs: List[Dict[str, float]] = []
        for sparse in vector_output["lexical_weights"]:
            sorted_weights = sorted(sparse.items(), key=lambda i: i[1], reverse=True)[:sparse_top_k]
            sparse_vecs.append({
                f"tok_{tid}": round(float(w), 4) for tid, w in sorted_weights
            })

        return entities_batch, dense_vecs, sparse_vecs
