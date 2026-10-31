"""Kotlin <-> Python 桥接层。

所有返回值都是 JSON 字符串，避免 Chaquopy 的类型转换歧义。
坐标系：a1=0 ... h8=63（python-chess 的 square 编号）。
"""

from __future__ import annotations

import json
import threading

import chess
import chess.engine

from session import Session

_lock = threading.Lock()
_session = Session()
_engine: chess.engine.SimpleEngine | None = None
_engine_name = ""
_engine_error = ""

_threads = 2
_hash_mb = 64
_last_cp: int | None = None
_last_delta: float | None = None


def _snapshot() -> dict:
    board = _session.board
    pieces = [[sq, p.piece_type, 1 if p.color else 0] for sq, p in board.piece_map().items()]
    last = _session.last_move
    return {
        "fen": board.fen(),
        "turn": "w" if board.turn else "b",
        "myColor": "w" if _session.my_color else "b",
        "ply": _session.ply,
        "pieces": pieces,
        "last": [last.from_square, last.to_square] if last else None,
        "sans": _session.san_list(),
        "flipped": bool(_session.flipped),
        "isMyTurn": _session.is_my_turn,
        "isCheck": board.is_check(),
        "isOver": board.is_game_over(),
        "kingSquare": board.king(board.turn) if board.is_check() else None,
        "canUndo": _session.ply > 0,
        "canRedo": len(_session._redo) > 0,
        "engine": _engine_name,
        "engineReady": _engine is not None,
        "engineError": _engine_error,
    }


def snapshot() -> str:
    with _lock:
        return json.dumps(_snapshot())


def _apply_options() -> None:
    if _engine is None:
        return
    options = {}
    if "Threads" in _engine.options:
        options["Threads"] = _threads
    if "Hash" in _engine.options:
        options["Hash"] = _hash_mb
    if "UCI_ShowWDL" in _engine.options:
        options["UCI_ShowWDL"] = True
    if options:
        _engine.configure(options)


def init_engine(engine_path: str) -> str:
    global _engine, _engine_name, _engine_error
    with _lock:
        if _engine is not None:
            try:
                _engine.quit()
            except Exception:
                pass
            _engine = None
        try:
            _engine = chess.engine.SimpleEngine.popen_uci(engine_path)
            _apply_options()
            _engine_name = str(_engine.id.get("name") or "Stockfish")
            _engine_error = ""
            return json.dumps({"ok": True, "name": _engine_name})
        except Exception as exc:
            _engine = None
            _engine_name = ""
            _engine_error = f"{type(exc).__name__}: {exc}"
            return json.dumps({"ok": False, "error": _engine_error})


def configure(threads: int, hash_mb: int) -> str:
    global _threads, _hash_mb
    with _lock:
        _threads = max(1, min(16, int(threads)))
        _hash_mb = max(16, min(2048, int(hash_mb)))
        try:
            _apply_options()
            return json.dumps({"ok": True})
        except Exception as exc:
            return json.dumps({"ok": False, "error": f"{type(exc).__name__}: {exc}"})


def reset(is_white: bool) -> str:
    global _last_cp, _last_delta
    with _lock:
        _session.my_color = chess.WHITE if is_white else chess.BLACK
        _session.flipped = not is_white
        _session.reset(_session.my_color)
        _last_cp = None
        _last_delta = None
        return json.dumps(_snapshot())


def flip() -> str:
    with _lock:
        _session.flipped = not _session.flipped
        return json.dumps(_snapshot())


def legal_targets(from_sq: int) -> str:
    with _lock:
        try:
            moves = _session.legal_from(int(from_sq))
        except Exception:
            moves = []
        return json.dumps([m.to_square for m in moves])


def play(from_sq: int, to_sq: int, promotion: int = 0) -> str:
    global _last_cp, _last_delta
    with _lock:
        promo = int(promotion) or None
        move = _session.find_move(int(from_sq), int(to_sq), promo)
        ok = bool(move) and _session.push(move)
        if ok:
            _last_cp = None
            _last_delta = None
        return json.dumps({"ok": ok, "state": _snapshot()})


def jump_to(ply: int) -> str:
    global _last_cp, _last_delta
    with _lock:
        _session.jump_to(int(ply))
        _last_cp = None
        _last_delta = None
        return json.dumps(_snapshot())


def undo() -> str:
    global _last_cp, _last_delta
    with _lock:
        _session.undo()
        _last_cp = None
        _last_delta = None
        return json.dumps(_snapshot())


def redo() -> str:
    global _last_cp, _last_delta
    with _lock:
        _session.redo()
        _last_cp = None
        _last_delta = None
        return json.dumps(_snapshot())


def _complexity_factor(board: chess.Board) -> float:

    factor = 1.0
    try:
        moves = board.legal_moves.count()
    except Exception:
        moves = 30
    pieces = len(board.piece_map())

    if pieces <= 10:
        factor += 0.60
    elif pieces <= 16:
        factor += 0.25
    if moves <= 3 and pieces > 10:
        factor -= 0.35
    if board.is_check():
        factor += 0.30
    if moves >= 35:
        factor += 0.25
    if _last_delta is not None and _last_delta > 1.0:
        factor += 0.40
    return max(0.4, min(2.5, factor))


def analyse(base_ms: int, adaptive: bool = True) -> str:
    global _last_cp, _last_delta
    with _lock:
        if _engine is None:
            return json.dumps({"ok": False, "error": _engine_error or "引擎未就绪"})
        board = _session.board
        if board.is_game_over():
            return json.dumps({"ok": False, "error": "对局结束"})

        base = max(100, min(30000, int(base_ms)))
        movetime = int(max(500, min(30000, base * _complexity_factor(board)))) if adaptive else base

        try:
            limit = chess.engine.Limit(time=movetime / 1000.0)
            info = _engine.analyse(board, limit)
        except Exception as exc:
            return json.dumps({"ok": False, "error": f"{type(exc).__name__}: {exc}"})

        out = {
            "ok": True,
            "version": _session.version,
            "fen": board.fen(),
            "uci": "",
            "san": "",
            "cp": None,
            "mate": None,
            "evalText": "—",
            "depth": int(info.get("depth") or 0),
            "nodes": int(info.get("nodes") or 0),
            "movetime": movetime,
            "wdl": None,
        }
        pv = info.get("pv") or []
        if pv:
            move = pv[0]
            out["uci"] = move.uci()
            try:
                out["san"] = board.san(move)
            except Exception:
                out["san"] = out["uci"]

        score = info.get("score")
        if score is not None:
            white = score.pov(chess.WHITE)
            if white.is_mate():
                mate = white.mate()
                out["mate"] = mate
                out["evalText"] = f"#{mate}" if mate > 0 else f"#-{abs(mate)}"
            else:
                cp = white.score()
                out["cp"] = cp
                out["evalText"] = f"{cp / 100.0:+.2f}"
                if _last_cp is not None:
                    _last_delta = abs(cp - _last_cp) / 100.0
                _last_cp = cp

        wdl = info.get("wdl")
        if wdl is not None:
            w = wdl.pov(chess.WHITE)
            out["wdl"] = [w.wins, w.draws, w.losses]
        return json.dumps(out)


def shutdown() -> str:
    global _engine, _engine_error
    with _lock:
        if _engine is not None:
            try:
                _engine.quit()
            except Exception:
                pass
            _engine = None
        _engine_error = ""
        return json.dumps({"ok": True})
