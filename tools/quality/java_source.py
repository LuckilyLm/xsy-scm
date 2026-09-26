#!/usr/bin/env python3
"""Java source segmenter used by the SCM quality guards.

A rule that scans raw file text cannot tell code from prose: a domain status
word quoted inside a Javadoc paragraph is documentation, while the same word as
a String literal in a comparison is a magic-string defect. This module makes
that distinction once so every rule can rely on it.

Each :class:`JavaSource` exposes three views of one compilation unit, all
sharing the original line numbering:

``code``
    The unit with every comment and every text-block body blanked out. Plain
    string literals are kept verbatim so annotation arguments stay readable to
    a rule.
``comments``
    The comment text only, one entry per source line that carries any.
``string_literals``
    Every string literal with its position and decoded content.

The scanner is a character state machine rather than a set of regular
expressions, because the ambiguous cases are exactly the ones a regex gets
wrong: ``//`` inside a string, a quote inside a block comment, and a Java text
block that spans lines and contains single quotes.
"""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
import re

_CODE = "code"
_LINE_COMMENT = "line_comment"
_BLOCK_COMMENT = "block_comment"
_STRING = "string"
_CHAR = "char"
_TEXT_BLOCK = "text_block"

_PACKAGE_RE = re.compile(r"^\s*package\s+([\w.]+)\s*;", re.MULTILINE)
_TYPE_RE = re.compile(
    r"^\s*(?:public\s+|final\s+|abstract\s+|sealed\s+|non-sealed\s+|static\s+|strictfp\s+)*"
    r"(?:class|interface|enum|record|@interface)\s+([A-Z]\w*)",
    re.MULTILINE,
)


@dataclass(frozen=True)
class CommentText:
    """Comment text contributed by one source line."""

    line: int
    text: str


@dataclass(frozen=True)
class StringLiteral:
    """One string literal occurrence.

    ``value`` is the text between the delimiters with backslash escapes left
    intact; rules compare it against domain vocabulary, where an escaped quote
    already means the literal is not a bare status word.
    """

    line: int
    value: str
    text_block: bool


class JavaSource:
    """A parsed Java compilation unit.

    ``relative_path`` is what findings and baselines record: it is stable across
    checkouts, unlike an absolute path.
    """

    def __init__(self, path: Path, relative_path: str, text: str) -> None:
        self.path = path
        self.relative_path = relative_path.replace("\\", "/")
        normalized = text.replace("\r\n", "\n").replace("\r", "\n")
        self.code, self.comments, self.string_literals = _scan(normalized)
        self.line_count = normalized.count("\n") + (0 if normalized.endswith("\n") else 1)

    @classmethod
    def read(cls, path: Path, root: Path) -> "JavaSource":
        resolved = path.resolve()
        text = resolved.read_text(encoding="utf-8", errors="replace")
        return cls(resolved, str(resolved.relative_to(root.resolve())), text)

    @property
    def package(self) -> str:
        match = _PACKAGE_RE.search(self.code)
        return match.group(1) if match else ""

    @property
    def type_name(self) -> str:
        match = _TYPE_RE.search(self.code)
        if match:
            return match.group(1)
        return self.path.stem


def _scan(text: str) -> tuple[str, list[CommentText], list[StringLiteral]]:
    """Split ``text`` into its code, comment and string-literal views."""
    code = list(text)
    comments: list[CommentText] = []
    literals: list[StringLiteral] = []

    state = _CODE
    line = 1
    comment_start_line = 0
    comment_buffer: list[str] = []
    literal_start_line = 0
    literal_buffer: list[str] = []
    index = 0
    length = len(text)

    def flush_comment() -> None:
        """Attach buffered comment text to the lines it actually spans."""
        for offset, chunk in enumerate("".join(comment_buffer).split("\n")):
            if chunk.strip():
                comments.append(CommentText(comment_start_line + offset, chunk.strip()))
        comment_buffer.clear()

    def flush_literal(text_block: bool) -> None:
        literals.append(StringLiteral(literal_start_line, "".join(literal_buffer), text_block))

    while index < length:
        char = text[index]
        following = text[index + 1] if index + 1 < length else ""

        # Newlines are never blanked: the code view has to keep the original
        # line numbering for every rule that reports a position.
        if char == "\n":
            if state == _LINE_COMMENT:
                flush_comment()
                state = _CODE
            elif state == _BLOCK_COMMENT:
                comment_buffer.append("\n")
            elif state == _TEXT_BLOCK:
                literal_buffer.append("\n")
            elif state == _STRING:
                # A bare newline cannot occur inside a Java string literal, so a
                # stray quote must not blank out the rest of the file and hide
                # every later finding.
                flush_literal(text_block=False)
                state = _CODE
            elif state == _CHAR:
                # Same reason: an unterminated char literal means the file is
                # already broken, and silently scanning nothing is worse.
                state = _CODE
            line += 1
            index += 1
            continue

        if state == _CODE:
            if char == "/" and following == "/":
                state = _LINE_COMMENT
                comment_start_line = line
                code[index] = " "
                code[index + 1] = " "
                index += 2
                continue
            if char == "/" and following == "*":
                state = _BLOCK_COMMENT
                comment_start_line = line
                code[index] = " "
                code[index + 1] = " "
                index += 2
                continue
            if char == '"':
                if text[index : index + 3] == '"""':
                    state = _TEXT_BLOCK
                    literal_start_line = line
                    literal_buffer = []
                    for offset in range(3):
                        code[index + offset] = " "
                    index += 3
                    continue
                state = _STRING
                literal_start_line = line
                literal_buffer = []
                index += 1
                continue
            if char == "'":
                state = _CHAR
                code[index] = " "
                index += 1
                continue
            index += 1
            continue

        if state == _LINE_COMMENT:
            comment_buffer.append(char)
            code[index] = " "
            index += 1
            continue

        if state == _BLOCK_COMMENT:
            comment_buffer.append(char)
            code[index] = " "
            if char == "*" and following == "/":
                code[index + 1] = " "
                index += 2
                flush_comment()
                state = _CODE
                continue
            index += 1
            continue

        if state == _CHAR:
            code[index] = " "
            if char == "\\":
                if index + 1 < length:
                    code[index + 1] = " "
                index += 2
                continue
            if char == "'":
                state = _CODE
            index += 1
            continue

        if state == _STRING:
            if char == "\\":
                literal_buffer.append(char + following)
                index += 2
                continue
            if char == '"':
                flush_literal(text_block=False)
                state = _CODE
                index += 1
                continue
            literal_buffer.append(char)
            index += 1
            continue

        # _TEXT_BLOCK: only the closing triple quote ends it, so a lone '"' is
        # content. Text-block bodies are SQL, JSON or prose, never an annotation
        # argument, so they are blanked from the code view instead of kept.
        if text[index : index + 3] == '"""':
            flush_literal(text_block=True)
            for offset in range(3):
                if index + offset < length:
                    code[index + offset] = " "
            index += 3
            state = _CODE
            continue
        literal_buffer.append(char)
        code[index] = " "
        index += 1

    if state in (_LINE_COMMENT, _BLOCK_COMMENT):
        flush_comment()
    if state in (_STRING, _TEXT_BLOCK):
        flush_literal(text_block=state == _TEXT_BLOCK)
    return "".join(code), comments, literals
