import os

import uvicorn

from api import app  # noqa: F401  (uvicorn la referencia como "main:app")

if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=int(os.environ.get("SERVER_PORT", "8087")))
