from typing import Any, Dict, List, Optional
from pydantic import BaseModel, Field


class Message(BaseModel):
    id: str
    text: Optional[str] = ""
    time: Optional[int] = None
    sender_id: Optional[str] = None
    file_snippets: Optional[List[str]] = None
    parts: Optional[List[Any]] = None
    mentions: Optional[List[Any]] = None
    thread_sn: Optional[str] = None
    member_event: Optional[Any] = None
    is_system: Optional[bool] = False
    is_hidden: Optional[bool] = False
    is_forward: Optional[bool] = False
    is_quote: Optional[bool] = False


class Chat(BaseModel):
    id: str
    name: Optional[str] = None
    sn: Optional[str] = None
    type: Optional[str] = None
    is_public: Optional[bool] = None
    members_count: Optional[int] = None
    members: Optional[List[Any]] = None


class PrepareIndexRequest(BaseModel):
    chat: Chat
    messages: List[Message] = Field(default_factory=list)
    overlap_messages: Optional[List[Message]] = None
    new_messages: Optional[List[Message]] = None
    labels: Optional[List[str]] = None
    chunk_size: int = 10
    chunk_overlap: int = 3


class Entity(BaseModel):
    label: str
    text: str
    start: int
    end: int
    score: float


class PreparedChunk(BaseModel):
    chat_id: str
    chunk_id: str
    message_ids: List[str]
    chunk_text: str
    sender_ids: List[str]
    time_from: Optional[int] = None
    time_to: Optional[int] = None
    entities: List[Entity] = Field(default_factory=list)
    dense_vec: List[float]
    lexical_weights: Dict[str, float] = Field(default_factory=dict)


class PrepareIndexResponse(BaseModel):
    chunks: List[PreparedChunk]


class QueryEntities(BaseModel):
    people: Optional[List[str]] = None
    emails: Optional[List[str]] = None
    documents: Optional[List[str]] = None
    names: Optional[List[str]] = None
    links: Optional[List[str]] = None


class PrepareQueryRequest(BaseModel):
    text: Optional[str] = None
    search_text: Optional[str] = None
    variants: Optional[List[str]] = None
    hyde: Optional[List[str]] = None
    keywords: Optional[List[str]] = None
    asker: Optional[str] = None
    asked_on: Optional[int] = None
    date_range: Optional[Any] = None
    date_mentions: Optional[List[Any]] = None
    entities: Optional[QueryEntities] = None


class PrepareQueryResponse(BaseModel):
    normalized_text: str
    dense_vec: List[float]
    lexical_weights: Dict[str, float] = Field(default_factory=dict)
    keywords: List[str] = Field(default_factory=list)
    entities: QueryEntities = Field(default_factory=QueryEntities)
