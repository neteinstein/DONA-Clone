"""Load `const.py`/`devices.py`/`client.py` without requiring Home Assistant.

Those three modules are deliberately free of any `homeassistant` import (see
`client.py`'s module docstring), so their protocol logic can be unit-tested on
their own. But they live inside `custom_components/domotalk/`, whose
`__init__.py` *does* import Home Assistant — and Python always runs a
package's `__init__.py` before any of its submodules. So instead of a normal
`import`, this registers lightweight stand-in package objects in
`sys.modules` first (never executing the real `__init__.py`), then loads the
three files we actually want directly from disk under those package names.
"""

from __future__ import annotations

import importlib.util
import sys
import types
from pathlib import Path

_DOMOTALK_DIR = Path(__file__).resolve().parents[1] / "custom_components" / "domotalk"


def _install_fake_package(name: str, path: Path) -> None:
    if name in sys.modules:
        return
    package = types.ModuleType(name)
    package.__path__ = [str(path)]
    sys.modules[name] = package


def _load_submodule(name: str, filename: str) -> types.ModuleType:
    full_name = f"custom_components.domotalk.{name}"
    if full_name in sys.modules:
        return sys.modules[full_name]
    spec = importlib.util.spec_from_file_location(full_name, _DOMOTALK_DIR / filename)
    if spec is None or spec.loader is None:
        raise ImportError(f"Could not load {filename}")
    module = importlib.util.module_from_spec(spec)
    sys.modules[full_name] = module
    spec.loader.exec_module(module)
    return module


_install_fake_package("custom_components", _DOMOTALK_DIR.parent)
_install_fake_package("custom_components.domotalk", _DOMOTALK_DIR)

const = _load_submodule("const", "const.py")
devices = _load_submodule("devices", "devices.py")
client = _load_submodule("client", "client.py")
