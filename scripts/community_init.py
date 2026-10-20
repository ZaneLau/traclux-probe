"""Create fresh self-hosted instance secrets locally, without printing them."""

import os
from pathlib import Path
import secrets


def main():
    root = Path(__file__).resolve().parent.parent
    target = root / ".env"
    values = {
        "DB_PASSWORD": secrets.token_hex(24),
        "MYSQL_ROOT_PASSWORD": secrets.token_hex(24),
        "KNOWLEDGE_DB_PASSWORD": secrets.token_hex(24),
        "JWT_SECRET": secrets.token_hex(32),
        "COMMUNITY_USERNAME": "owner@example.test",
        "COMMUNITY_PASSWORD": secrets.token_urlsafe(24),
        "MODEL_BASE_URL": "http://127.0.0.1:9/v1",
        "MODEL_NAME": "configure-your-model",
        "MODEL_API_KEY": "community-not-configured",
        "COMMUNITY_PROBE_COUNT": "5",
        "COMMUNITY_SCHEDULING_ENABLED": "false",
        "TRACLUX_PORT": "3100",
    }
    try:
        fd = os.open(target, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    except FileExistsError:
        print("Existing .env preserved. Edit it privately to configure your model.")
        return
    with os.fdopen(fd, "w") as stream:
        stream.write("# Local instance secrets. Never commit or share this file.\n")
        stream.writelines(f"{key}={value}\n" for key, value in values.items())
    print("Created private .env. Configure MODEL_* and read your login credentials there.")


if __name__ == "__main__":
    main()
