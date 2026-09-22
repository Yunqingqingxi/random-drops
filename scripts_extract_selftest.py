import re, io, sys

SRC = r"D:\code\random-drops\core\src\main\java\com\randomdrops\SelfTest.java"

with open(SRC, encoding="utf-8") as f:
    text = f.read()

# ---- collect import block (everything between first import and the class decl) ----
imp_start = text.index("import ")
imp_end = text.index("public final class SelfTest")
import_block = text[imp_start:imp_end]

# ---- helper: extract a method by balanced braces ----
def extract_method(src, name):
    sig = "static void " + name + "("
    i = src.index(sig)
    # find first '{' after signature
    j = src.index("{", i)
    depth = 0
    k = j
    while k < len(src):
        c = src[k]
        if c == "{":
            depth += 1
        elif c == "}":
            depth -= 1
            if depth == 0:
                break
        k += 1
    # include trailing newline after closing brace
    end = k + 1
    while end < len(src) and src[end] in " \t\r\n":
        end += 1
    return src[i:end], (i, end)

ENCHANT_METHODS = ["checkStinkyFeet","checkMagnet","checkCurseBurden","checkCurseFrailty",
                   "checkNewEnchantments","checkEnchantLevelUp","checkUniversalLevelUp","checkLibrarian"]
EVENT_METHODS = ["checkGlobalEvents","checkEventsFix","checkMeteorRealism","checkBounty",
                 "checkBingo","checkNewEvents"]

def build_file(classname, methods):
    out = []
    out.append("package com.randomdrops;\n")
    # strip the package line from import block if present (there is none; imports only)
    out.append(import_block)
    out.append("\nimport static com.randomdrops.SelfTest.*;\n\n")
    out.append("public final class " + classname + " {\n")
    out.append("\tprivate " + classname + "() {\n\t}\n\n")
    for m in methods:
        body, _ = extract_method(text, m)
        # make method public so it can be referenced from the module @Mod initializer
        body = body.replace("private static void " + m + "(", "public static void " + m + "(", 1)
        out.append(body + "\n")
    out.append("}\n")
    return "".join(out)

ench = build_file("EnchantSelfTest", ENCHANT_METHODS)
evt = build_file("EventSelfTest", EVENT_METHODS)

# write new files
with open(r"D:\code\random-drops\enchants\src\main\java\com\randomdrops\EnchantSelfTest.java", "w", encoding="utf-8") as f:
    f.write(ench)
with open(r"D:\code\random-drops\events\src\main\java\com\randomdrops\EventSelfTest.java", "w", encoding="utf-8") as f:
    f.write(evt)

# ---- remove extracted methods from SelfTest, and make helpers public ----
new_text = text
# global private static -> public static (so static import works for helpers)
new_text = new_text.replace("private static", "public static")
for m in ENCHANT_METHODS + EVENT_METHODS:
    # new_text already had private->public; search with public signature
    body, (i, end) = extract_method(new_text, m)
    new_text = new_text[:i] + new_text[end:]

with open(SRC, "w", encoding="utf-8") as f:
    f.write(new_text)

print("WROTE EnchantSelfTest + EventSelfTest; removed", len(ENCHANT_METHODS)+len(EVENT_METHODS), "methods from SelfTest")
print("EnchantSelfTest bytes:", len(ench), "EventSelfTest bytes:", len(evt))
