"""Opt-in private Demo factory: never loaded by ordinary tests/imports."""
import hashlib
import hmac
import os
from pathlib import Path
import re
import stat

from fastapi.responses import JSONResponse
from ready_gym_intake import create_app, DeepSeekAdapter, parse_private_config
from ready_gym_foundation import FoundationProvider, JavaBusinessClient


class DemoAccessGate:
    def __init__(self, app, code):
        if type(code) is not str or len(code) < 32:
            raise ValueError("Demo requires a private access code")
        self.app, self.digest = app, hashlib.sha256(code.encode()).digest()

    async def __call__(self, scope, receive, send):
        if scope["type"] == "http":
            headers = [value for name, value in scope["headers"] if name.lower() == b"x-ready-gym-demo"]
            if len(headers) != 1 or len(headers[0]) > 256 or not hmac.compare_digest(hashlib.sha256(headers[0]).digest(), self.digest):
                response = JSONResponse({"error": {"code": "demo_unauthorized", "message": "需要有效的Demo访问码。"}}, status_code=401,
                                        headers={"Cache-Control": "no-store"})
                return await response(scope, receive, send)
        return await self.app(scope, receive, send)


def secure_demo_app(app, code):
    app.add_middleware(DemoAccessGate, code=code)
    return app


def private_text(path):
    parent, file = path.parent.lstat(), path.lstat()
    if (stat.S_ISLNK(parent.st_mode) or stat.S_ISLNK(file.st_mode) or not stat.S_ISREG(file.st_mode)
            or stat.S_IMODE(parent.st_mode) != 0o700 or stat.S_IMODE(file.st_mode) != 0o600
            or file.st_uid != os.getuid() or parent.st_uid != os.getuid() or file.st_size > 4096):
        raise ValueError("Owned private config directory700/file600 required")
    return path.read_text(encoding="utf-8")


def create_private_demo():
    folder = Path.home() / ".config/ready-gym"
    key = parse_private_config(private_text(folder / ".env"))
    values = dict(line.split("=", 1) for line in private_text(folder / "business.env").splitlines() if line)
    code = private_text(folder / "demo-access-code").strip()
    return secure_demo_app(create_app(provider=DeepSeekAdapter(key), business=JavaBusinessClient(values["READY_GYM_INTERNAL_TOKEN"]),
                                     agent_provider=FoundationProvider(key)), code)
