"""Verify a local community instance without invoking any model or printing secrets."""

import argparse
import http.cookiejar
import json
from pathlib import Path
from urllib.error import HTTPError
from urllib.request import HTTPCookieProcessor, Request, build_opener


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--url", default="http://127.0.0.1:3100")
    args = parser.parse_args()
    # This helper intentionally sends credentials only to an explicit loopback instance.
    from urllib.parse import urlparse
    if urlparse(args.url).hostname not in {"localhost", "127.0.0.1", "::1"}:
        parser.error("Use a loopback URL for this local credential smoke check")
    root = Path(__file__).resolve().parent.parent
    values = dict(line.split("=", 1) for line in (root / ".env").read_text().splitlines() if line and not line.startswith("#"))
    base = args.url.rstrip("/")
    client = build_opener(HTTPCookieProcessor(http.cookiejar.CookieJar()))

    def api(path, payload=None):
        request = Request(base + "/api/v1" + path,
                          data=None if payload is None else json.dumps(payload).encode(),
                          headers={"Content-Type": "application/json", "Origin": base})
        with client.open(request, timeout=10) as response:
            result = json.load(response)
        if result.get("code") != 0:
            raise RuntimeError(f"{path}: business request failed; inspect instance logs privately")
        print(f"PASS {path}")
        return result["data"]

    assert api("/health")["status"] == "UP"
    api("/auth/login", {"username": values["COMMUNITY_USERNAME"], "password": values["COMMUNITY_PASSWORD"]})
    try:
        for path in ["/auth/session", "/brand-spaces", "/service-access", "/credits/balance", "/usage/daily"]:
            api(path)
        try:
            api("/admin/overview")
        except HTTPError as error:
            assert error.code in {403, 404}
            print("PASS private management API unavailable")
        else:
            raise RuntimeError("Private management API unexpectedly available")
    finally:
        api("/auth/logout", {})
    print("No model calls made. Probe generation/execution still requires a separate provider acceptance run.")


if __name__ == "__main__":
    main()
