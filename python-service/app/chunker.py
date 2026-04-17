from dataclasses import dataclass, field
from typing import List, Optional

from .models import Message


@dataclass
class MessageChunk:
    message_ids: List[str]
    text: str
    sender_ids: List[str]
    time_from: Optional[int] = None
    time_to: Optional[int] = None


def _is_useful(msg: Message) -> bool:
    if msg.is_system or msg.is_hidden:
        return False
    text = (msg.text or "").strip()
    has_snippets = bool(msg.file_snippets)
    if not text and not has_snippets:
        return False
    return True


def _format_message(msg: Message) -> str:
    sender = msg.sender_id or "unknown"
    text = (msg.text or "").strip()
    snippets = msg.file_snippets or []
    if snippets:
        snippet_text = " | ".join(str(s) for s in snippets if s)
        text = f"{text} [attached: {snippet_text}]" if text else f"[attached: {snippet_text}]"
    return f"{sender}: {text}"


def build_chunks(
    messages: List[Message],
    chunk_size: int = 10,
    overlap: int = 3,
) -> List[MessageChunk]:
    """Group filtered messages into overlapping windows.

    Per TZ: 8-12 messages per chunk, overlap 2-3.
    """
    filtered = [m for m in messages if _is_useful(m)]
    if not filtered:
        return []

    if chunk_size < 1:
        chunk_size = 1
    if overlap < 0:
        overlap = 0
    step = max(1, chunk_size - overlap)

    chunks: List[MessageChunk] = []
    i = 0
    while i < len(filtered):
        window = filtered[i:i + chunk_size]
        if not window:
            break

        message_ids = [m.id for m in window]
        texts = [_format_message(m) for m in window]
        sender_ids: List[str] = []
        for m in window:
            if m.sender_id and m.sender_id not in sender_ids:
                sender_ids.append(m.sender_id)
        times = [m.time for m in window if m.time is not None]

        chunks.append(MessageChunk(
            message_ids=message_ids,
            text="\n".join(texts),
            sender_ids=sender_ids,
            time_from=min(times) if times else None,
            time_to=max(times) if times else None,
        ))

        if i + chunk_size >= len(filtered):
            break
        i += step

    return chunks
