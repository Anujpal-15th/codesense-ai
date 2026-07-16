"""CodeSense Python execution tracer.

Runs a user program under sys.settrace and emits a JSON document on REAL
stdout that deserializes directly into the backend's ExecutionTrace record
(same shape the Java/JDI tracer produces, including TraceValue's "valueKind"
discriminator). The user program's own print() output never reaches real
stdout - sys.stdout/sys.stderr are swapped for in-memory buffers and the
captured text is embedded in the JSON (consoleOutput / consoleOutputDelta),
which is what makes the stdout-as-transport protocol safe in both
local-process and Docker modes with no writable mount required.

Usage: python tracer.py <user_file> <limits_json_file>
  limits_json_file: path to a JSON file like
    {"maxSteps":2000,"timeoutSeconds":10,"maxStackDepth":64,
     "maxObjectDepth":4,"maxArrayElements":50,"maxObjectFields":50,
     "maxStringLength":1000,"maxConsoleOutputBytes":65536}
  (A file, not an argv literal: Windows argv quoting strips the JSON's
  double-quotes when passed inline - found the hard way.)

Exit code is always 0 when a trace JSON was produced (even for user-code
exceptions/timeouts - those are normal outcomes); nonzero only when the
tracer itself failed, in which case diagnostics go to real stderr.
"""

import io
import json
import sys
import time
import traceback
import types


def main():
    user_file = sys.argv[1]
    with open(sys.argv[2], "r", encoding="utf-8") as lf:
        limits = json.load(lf)

    max_steps = int(limits.get("maxSteps", 2000))
    timeout_s = float(limits.get("timeoutSeconds", 10))
    max_stack = int(limits.get("maxStackDepth", 64))
    max_depth = int(limits.get("maxObjectDepth", 4))
    max_elems = int(limits.get("maxArrayElements", 50))
    max_fields = int(limits.get("maxObjectFields", 50))
    max_str = int(limits.get("maxStringLength", 1000))
    max_console = int(limits.get("maxConsoleOutputBytes", 65536))

    with open(user_file, "r", encoding="utf-8") as f:
        source = f.read()

    real_stdout = sys.stdout
    out_buf = io.StringIO()
    err_buf = io.StringIO()

    steps = []
    outcome = "NORMAL"
    exception_info = None
    truncated = False
    start = time.monotonic()
    # consoleOutputDelta bookkeeping: how much of out_buf was already
    # attributed to earlier steps.
    consumed = [0]

    class _Abort(Exception):
        """Raised inside the trace hook to unwind user code on step/time caps."""
        def __init__(self, why):
            self.why = why

    # ---- value serialization (mirrors TraceValue's JSON union) ----

    SKIP_TYPES = (types.ModuleType, types.FunctionType, types.BuiltinFunctionType,
                  types.MethodType, type)

    # Typing-machinery internals (typing.List, typing.Optional, abc.ABCMeta
    # plumbing, etc.) end up bound as ordinary module-level locals whenever
    # user code does `from typing import List, Optional` - SKIP_TYPES doesn't
    # catch them (they're not `type` instances, they're typing._GenericAlias/
    # _SpecialForm objects), so without this they'd show up as noisy
    # "_SpecialGenericAlias"/"_SpecialForm" values in the Variables panel.
    # They're plumbing, not program state - never meaningful to a learner.
    EXCLUDED_VALUE_MODULES = {"typing", "abc", "_collections_abc"}

    def ser(v, depth, seen):
        if v is None:
            return {"valueKind": "null"}
        # bool must precede int: bool is an int subclass.
        if isinstance(v, bool):
            return {"valueKind": "primitive", "primitiveType": "boolean",
                    "literal": "true" if v else "false"}
        if isinstance(v, int):
            return {"valueKind": "primitive", "primitiveType": "int", "literal": str(v)}
        if isinstance(v, float):
            return {"valueKind": "primitive", "primitiveType": "double", "literal": repr(v)}
        if isinstance(v, str):
            cut = len(v) > max_str
            return {"valueKind": "string", "value": v[:max_str], "truncated": cut}
        if depth <= 0:
            return {"valueKind": "truncated", "reason": "max object depth reached"}
        vid = id(v)
        if vid in seen:
            return {"valueKind": "truncated", "reason": "cyclic reference"}
        seen = seen | {vid}
        ih = format(vid & 0xFFFFFF, "x")  # compact, stable-for-the-run identity
        if isinstance(v, (list, tuple)):
            els = [ser(e, depth - 1, seen) for e in v[:max_elems]]
            return {"valueKind": "list", "type": type(v).__name__, "identityHash": ih,
                    "size": len(v), "elements": els, "truncated": len(v) > max_elems}
        if isinstance(v, dict):
            entries = []
            for i, (k, val) in enumerate(v.items()):
                if i >= max_elems:
                    break
                entries.append({"key": ser(k, depth - 1, seen),
                                "value": ser(val, depth - 1, seen)})
            return {"valueKind": "map", "type": type(v).__name__, "identityHash": ih,
                    "size": len(v), "entries": entries, "truncated": len(v) > max_elems}
        if isinstance(v, (set, frozenset)):
            els = []
            for i, e in enumerate(v):
                if i >= max_elems:
                    break
                els.append(ser(e, depth - 1, seen))
            return {"valueKind": "set", "type": type(v).__name__, "identityHash": ih,
                    "size": len(v), "elements": els, "truncated": len(v) > max_elems}
        # Arbitrary object: shallow field dump via vars(); repr fallback.
        fields = []
        try:
            attrs = vars(v)
            for i, (name, val) in enumerate(attrs.items()):
                if i >= max_fields:
                    break
                if name.startswith("__"):
                    continue
                fields.append({"name": name, "declaredType": type(val).__name__,
                               "value": ser(val, depth - 1, seen)})
            trunc = len(attrs) > max_fields
        except TypeError:
            r = repr(v)
            fields.append({"name": "repr", "declaredType": "str",
                           "value": {"valueKind": "string", "value": r[:max_str],
                                     "truncated": len(r) > max_str}})
            trunc = False
        return {"valueKind": "object", "type": type(v).__name__, "identityHash": ih,
                "fields": fields, "truncated": trunc}

    def frame_locals(frame):
        out = []
        for name, val in frame.f_locals.items():
            if name.startswith("__"):
                continue
            if isinstance(val, SKIP_TYPES):
                continue
            if type(val).__module__ in EXCLUDED_VALUE_MODULES:
                continue
            out.append({"name": name, "declaredType": type(val).__name__,
                        "value": ser(val, max_depth, frozenset())})
        return out

    def snapshot_stack(frame):
        """User-file frames innermost-first, like JDI's thread.frames()."""
        stack = []
        f = frame
        while f is not None and len(stack) < max_stack:
            if f.f_code.co_filename == user_file:
                stack.append({
                    "className": "main",
                    "methodName": f.f_code.co_name,
                    # f_lineno is 0 on the module frame's 'call' event; the
                    # editor highlight is 1-based, so clamp.
                    "lineNumber": max(f.f_lineno or 1, 1),
                    "localVariables": frame_locals(f),
                    "thisObject": None,
                })
            f = f.f_back
        return stack

    def record(frame, event_type, return_value):
        text = out_buf.getvalue()
        delta = text[consumed[0]:]
        consumed[0] = len(text)
        steps.append({
            "stepIndex": len(steps),
            "eventType": event_type,
            "threadName": "main",
            "callStack": snapshot_stack(frame),
            "consoleOutputDelta": delta,
            "returnValue": return_value,
        })

    def tracer(frame, event, arg):
        if frame.f_code.co_filename != user_file:
            return None  # never descend into stdlib/tracer frames
        if time.monotonic() - start > timeout_s:
            raise _Abort("TIMED_OUT")
        if event == "call":
            record(frame, "METHOD_ENTRY", None)
        elif event == "line":
            record(frame, "LINE", None)
        elif event == "return":
            rv = None if arg is None else ser(arg, max_depth, frozenset())
            record(frame, "METHOD_EXIT", rv)
        if len(steps) >= max_steps:
            raise _Abort("TRUNCATED")
        return tracer

    # ---- compile & run ----

    try:
        code = compile(source, user_file, "exec")
    except SyntaxError as e:
        # The Python analogue of a Java compile error - surfaced as a normal
        # EXCEPTION outcome so the UI shows the real message + line number.
        # Don't leak the backend's temp-file path into user-facing lines.
        detail = [ln.rstrip("\n").replace(user_file, "main.py") for ln in
                  traceback.format_exception_only(type(e), e)
                  if not ln.lstrip().startswith("File ")]
        emit(real_stdout, [], "EXCEPTION", {
            "exceptionClassName": "SyntaxError",
            "message": "%s (line %s)" % (e.msg, e.lineno),
            "stackTraceLines": detail,
        }, "", False, 0, start)
        return

    user_globals = {"__name__": "__main__", "__file__": user_file,
                    "__builtins__": __builtins__}

    sys.stdout = out_buf
    sys.stderr = err_buf
    sys.settrace(tracer)
    try:
        exec(code, user_globals)
    except _Abort as a:
        outcome = a.why
        truncated = a.why == "TRUNCATED"
    except BaseException as e:  # noqa: BLE001 - user code may raise anything
        outcome = "EXCEPTION"
        # extract_tb is outermost-first; Java stack traces (and the UI) read
        # innermost-first, so reverse.
        user_lines = []
        for fs in reversed(traceback.extract_tb(e.__traceback__)):
            if fs.filename == user_file:
                user_lines.append("at main.%s(main.py:%d)" % (fs.name, fs.lineno))
        exception_info = {
            "exceptionClassName": type(e).__name__,
            "message": str(e),
            "stackTraceLines": user_lines,
        }
    finally:
        sys.settrace(None)
        sys.stdout = real_stdout
        sys.stderr = sys.__stderr__

    console = out_buf.getvalue()
    stderr_text = err_buf.getvalue()
    if stderr_text:
        console = console + stderr_text
    if len(console.encode("utf-8", "replace")) > max_console:
        console = console.encode("utf-8", "replace")[:max_console].decode("utf-8", "replace")

    emit(real_stdout, steps, outcome, exception_info, console, truncated,
         len(steps), start)


def emit(real_stdout, steps, outcome, exception_info, console, truncated,
         total, start):
    doc = {
        "steps": steps,
        "outcome": outcome,
        "exceptionInfo": exception_info,
        "consoleOutput": console,
        "truncated": truncated,
        "totalStepsCaptured": total,
        "executionTimeMillis": int((time.monotonic() - start) * 1000),
    }
    json.dump(doc, real_stdout)
    real_stdout.flush()


if __name__ == "__main__":
    main()
