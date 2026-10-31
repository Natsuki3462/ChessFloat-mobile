"""Python 内核契约自检（不需要 Android 设备，在桌面环境即可跑）。

    python tools/verify_bridge.py --engine /path/to/stockfish

校验 Kotlin 侧依赖的 JSON 字段与行为：快照结构、合法着法、走子/悔棋/重做/
历史跳转、四种升变、WDL 胜率换算、自适应限时、将军与将杀识别。
"""

from __future__ import annotations

import argparse
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, "app", "src", "main", "python"))

import bridge
import chess

FAILS: list[str] = []


def check(name: str, ok: bool, detail: str = "") -> None:
    print(("[PASS] " if ok else "[FAIL] ") + name + ("  " + detail if detail else ""))
    if not ok:
        FAILS.append(name)


def sq(text: str) -> int:
    return (int(text[1]) - 1) * 8 + (ord(text[0]) - 97)


STATE_KEYS = {
    "fen", "turn", "myColor", "ply", "pieces", "last", "sans", "flipped",
    "isMyTurn", "isCheck", "isOver", "kingSquare", "canUndo", "canRedo",
    "engine", "engineReady", "engineError",
}


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--engine", required=True, help="Stockfish 可执行文件路径")
    args = ap.parse_args()

    print("== 引擎 ==")
    r = json.loads(bridge.init_engine(args.engine))
    check("init_engine", r.get("ok") is True, r.get("name") or r.get("error", ""))
    if not r.get("ok"):
        return 1
    check("configure", json.loads(bridge.configure(2, 64)).get("ok") is True)
    check("configure 越界夹紧", json.loads(bridge.configure(99, 99999)).get("ok") is True)

    print("\n== 快照契约 ==")
    st = json.loads(bridge.reset(True))
    missing = STATE_KEYS - set(st)
    check("字段齐全", not missing, f"缺 {missing}" if missing else "17 个字段")
    check("初始 32 子", len(st["pieces"]) == 32)
    check("pieces 为三元组", all(len(p) == 3 for p in st["pieces"]))
    check("轮白方", st["turn"] == "w" and st["myColor"] == "w")
    check("未翻转", st["flipped"] is False)
    check("last 为空", st["last"] is None)

    print("\n== 走子与历史 ==")
    check("e2 合法目标", set(json.loads(bridge.legal_targets(12))) == {20, 28})
    check("空格无着法", json.loads(bridge.legal_targets(36)) == [])
    r = json.loads(bridge.play(12, 28, 0))
    check("e2e4", r["ok"] is True and r["state"]["ply"] == 1)
    check("SAN 记录", r["state"]["sans"] == ["e4"], str(r["state"]["sans"]))
    check("非法着法被拒", json.loads(bridge.play(12, 20, 0))["ok"] is False)

    for uci in ("e7e5", "g1f3", "b8c6", "f1b5"):
        bridge.play(sq(uci[:2]), sq(uci[2:]), 0)
    st = json.loads(bridge.snapshot())
    check("走了 5 步", st["ply"] == 5)
    st = json.loads(bridge.jump_to(2))
    check("跳回第 2 步", st["ply"] == 2 and st["sans"] == ["e4", "e5"], str(st["sans"]))
    check("跳转后可重做", st["canRedo"] is True)
    st = json.loads(bridge.jump_to(5))
    check("再跳到第 5 步", st["ply"] == 5)
    st = json.loads(bridge.undo())
    check("悔棋", st["ply"] == 4)
    st = json.loads(bridge.redo())
    check("重做", st["ply"] == 5)

    print("\n== 升变 ==")
    bridge._session.board = chess.Board("8/P7/8/8/8/8/8/K6k w - - 0 1")
    check("a8 是合法目标", sq("a8") in json.loads(bridge.legal_targets(sq("a7"))))
    for promo, letter in ((2, "N"), (3, "B"), (4, "R"), (5, "Q")):
        bridge._session.board = chess.Board("8/P7/8/8/8/8/8/K6k w - - 0 1")
        r = json.loads(bridge.play(sq("a7"), sq("a8"), promo))
        fen = r["state"]["fen"].split()[0]
        check(f"升变 {letter}", r["ok"] and fen.startswith(letter), fen)

    print("\n== 分析 ==")
    bridge.reset(True)
    a = json.loads(bridge.analyse(1000, True))
    check("分析成功", a.get("ok") is True, a.get("error", ""))
    check("有 uci/san", bool(a.get("uci")) and bool(a.get("san")))
    check("有深度", a.get("depth", 0) > 0, f"depth={a.get('depth')}")
    check("有 evalText", a.get("evalText") not in (None, ""), str(a.get("evalText")))
    wdl = a.get("wdl")
    check("wdl 三元组", isinstance(wdl, list) and len(wdl) == 3, str(wdl))
    if wdl:
        check("wdl 合计 1000‰", abs(sum(wdl) - 1000) <= 2, str(sum(wdl)))
        white = (wdl[0] + wdl[1] / 2) / 10
        black = (wdl[2] + wdl[1] / 2) / 10
        check("黑白期望得分互补", abs(white + black - 100) < 0.01, f"{white:.1f}/{black:.1f}")
    check("关闭自适应=固定限时",
          json.loads(bridge.analyse(1000, False)).get("movetime") == 1000)
    bridge._session.board = chess.Board("8/8/8/4k3/8/8/4P3/4K3 w - - 0 1")
    check("残局自动加时",
          json.loads(bridge.analyse(1000, True)).get("movetime", 0) > 1000)

    print("\n== 将军 / 将杀 ==")
    bridge.reset(True)
    for uci in ("f2f3", "e7e5", "g2g4", "d8h4"):
        bridge.play(sq(uci[:2]), sq(uci[2:]), 0)
    st = json.loads(bridge.snapshot())
    check("检测到将军", st["isCheck"] is True)
    check("kingSquare 指向 e1", st["kingSquare"] == 4, str(st["kingSquare"]))
    check("将杀判定", st["isOver"] is True)

    bridge.shutdown()
    print("\n== 结果 ==")
    if FAILS:
        print(f"[FAIL] {len(FAILS)} 项未通过：{', '.join(FAILS)}")
        return 1
    print("[PASS] 全部通过")
    return 0


if __name__ == "__main__":
    sys.exit(main())
