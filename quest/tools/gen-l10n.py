"""Writes quest/core/src/main/kotlin/app/nama/core/L10nStrings.kt from 30nama/Core/L10n.swift.

Every app carries the same keys and translations. The Swift file is where they are written;
this copies its keys and its three tables, so the Quest app never drifts from it. Run it after
the Mac side adds or changes a string; a test fails until then.

    python quest/tools/gen-l10n.py
"""
import pathlib
import re

root = pathlib.Path(__file__).resolve().parents[2]
swift = (root / "30nama/Core/L10n.swift").read_text(encoding="utf-8")

enum = re.search(r"enum S: String, CaseIterable \{(.*?)\n\}", swift, re.S).group(1)
keys = [k.strip() for line in re.findall(r"case (.+)", enum) for k in line.split(",") if k.strip()]


def table(name):
    body = re.search(r"static let %s: \[S: String\] = \[(.*?)\n    \]" % name, swift, re.S).group(1)
    return re.findall(r'\.(\w+): "((?:[^"\\]|\\.)*)"', body)


def kotlin(value):
    # Swift escapes are \" \\ \n and \u{XXXX}; Kotlin takes the first three as they are, wants
    # \uXXXX (two of them past the BMP) for the last, and reads a bare $ as a template.
    def scalar(m):
        cp = int(m.group(1), 16)
        if cp < 0x10000:
            return r"\u%04X" % cp
        cp -= 0x10000
        return r"\u%04X\u%04X" % (0xD800 + (cp >> 10), 0xDC00 + (cp & 0x3FF))
    value = value.replace("$", r"\$")
    return re.sub(r"\\u\{([0-9A-Fa-f]+)\}", scalar, value)


out = [
    "// Written by quest/tools/gen-l10n.py from 30nama/Core/L10n.swift. Do not edit by hand.",
    "@file:Suppress(\"EnumEntryName\", \"SpellCheckingInspection\")",
    "",
    "package app.nama.core",
    "",
    "/** Interface string keys, named as in the Swift apps so every app reads the same tables. */",
    "enum class S {",
]
out += ["    %s," % k for k in keys]
out += ["}", "", "internal object LocTables {"]
for name in ("en", "fa", "ar"):
    out.append("    val %s: Map<S, String> by lazy { %s() }" % (name, name))
out.append("")
for name in ("en", "fa", "ar"):
    out.append("    private fun %s(): Map<S, String> = java.util.EnumMap<S, String>(S::class.java).apply {" % name)
    for key, value in table(name):
        out.append('        put(S.%s, "%s")' % (key, kotlin(value)))
    out.append("    }")
    out.append("")
out[-1] = "}"
target = root / "quest/core/src/main/kotlin/app/nama/core/L10nStrings.kt"
target.write_text("\n".join(out) + "\n", encoding="utf-8")
print("wrote", target.relative_to(root), "with", len(keys), "keys")
